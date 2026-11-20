package com.smartwash.dining.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.config.MinioConfig;
import com.smartwash.dining.constant.DiningOrderStatus;
import com.smartwash.dining.core.ShoppingListAssembler;
import com.smartwash.dining.core.ShoppingListAssembler.RecipeIngredient;
import com.smartwash.dining.entity.DiningOrder;
import com.smartwash.dining.entity.DiningOrderItem;
import com.smartwash.dining.entity.DiningOrderMember;
import com.smartwash.dining.entity.DiningOrderMenu;
import com.smartwash.dining.from.AddDiningOrderDishFrom;
import com.smartwash.dining.from.CreateDiningOrderFrom;
import com.smartwash.dining.from.JoinDiningOrderFrom;
import com.smartwash.dining.from.SubmitDiningOrderFrom;
import com.smartwash.dining.mapper.DiningOrderItemMapper;
import com.smartwash.dining.mapper.DiningOrderMapper;
import com.smartwash.dining.mapper.DiningOrderMemberMapper;
import com.smartwash.dining.mapper.DiningOrderMenuMapper;
import com.smartwash.dining.service.DiningOrderService;
import com.smartwash.dining.service.DiningRecipeGate;
import com.smartwash.dining.stream.DiningOrderStreamer;
import com.smartwash.dining.vo.DiningOrderActiveVo;
import com.smartwash.dining.vo.DiningOrderBriefVo;
import com.smartwash.dining.vo.DiningOrderCreatedVo;
import com.smartwash.dining.vo.DiningOrderDetailVo;
import com.smartwash.dining.vo.DiningOrderFinishVo;
import com.smartwash.dining.vo.DiningOrderListVo;
import com.smartwash.entity.Users;
import com.smartwash.from.BaseSearchFrom;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.mapper.UsersMapper;
import com.smartwash.notice.constant.NoticeType;
import com.smartwash.notice.service.NoticeService;
import com.smartwash.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 点单订单服务实现。
 * 并发纪律：加入去重先查后插给友好提示，并发窗口由 uk_order_user 唯一索引兜底裁决；
 * 草稿期菜单编辑（加菜/移除）与提交点单（submit）在本类内走同一套 version 条件 UPDATE 乐观锁，
 * 条件不匹配返回业务错误码 409（端上拉取最新后重放）。
 * 选菜记录（dining_order_item）已停止写入：购买清单在 submit 时直接聚合本单菜单，无「选菜人」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiningOrderServiceImpl extends ServiceImpl<DiningOrderMapper, DiningOrder>
        implements DiningOrderService {

    private final DiningOrderMemberMapper memberMapper;

    private final DiningOrderMenuMapper menuMapper;

    private final DiningOrderItemMapper itemMapper;

    private final DiningRecipeGate recipeGate;

    private final UsersMapper usersMapper;

    private final DiningOrderMapper orderMapper;

    private final NoticeService noticeService;

    /** MinIO 配置：快照写入时把封面对象键拼成端上可直读的 URL（与菜谱读链路同口径） */
    private final MinioConfig minioConfig;

    /** SSE 变更推送：写操作事务提交后广播订单变更信号（本机 + Redis 多实例） */
    private final DiningOrderStreamer streamer;

    /** version 冲突业务码（HTTP 语义冲突） */
    private static final int VERSION_CONFLICT_CODE = 409;

    /** 唯一性冲突业务码：同一发起者已有进行中的 DRAFT 草稿（区别于 409 version 冲突） */
    private static final int UNIQUE_ACTIVE_ORDER_CODE = 40901;

    /** 标题上限（与 CreateDiningOrderFrom/SubmitDiningOrderFrom 的 @Size 一致，DB 列宽 64） */
    private static final int TITLE_MAX_LENGTH = 64;

    /** 列表食材摘要最多展示的食材名个数，超出以「…」收尾 */
    private static final int INGREDIENT_SUMMARY_LIMIT = 6;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DiningOrderCreatedVo create(CreateDiningOrderFrom from, Long userId) {
        List<Long> recipeIds = resolveMenuRecipeIds(from, userId);
        // 去重保序（同菜重复勾选入参防御），非空时整体把关上架存在性（空菜单 = 空草稿，合法）
        List<Long> distinctRecipeIds = recipeIds.stream().filter(Objects::nonNull).distinct().toList();
        if (!distinctRecipeIds.isEmpty()) {
            recipeGate.assertAllOnShelf(distinctRecipeIds);
        }

        // 唯一闸门（本期）：同一发起者同时最多一个进行中的 DRAFT；命中则拒绝并带既有单标题，
        // 端上据此提示并可跳转该单编辑态（并发穿透由端上二次校验兜底，见方案 §五）
        DiningOrder active = findMyActiveOrder(userId);
        if (active != null) {
            throw new CustomExceptions(UNIQUE_ACTIVE_ORDER_CODE,
                    "你有一个正在编辑的点单「" + active.getTitle() + "」，请先完成或放弃后再开始");
        }

        // 草稿态标题可空：DB title NOT NULL，统一落空串，submit 时再校验必填
        String title = from.getTitle() == null ? "" : from.getTitle();
        DiningOrder order = new DiningOrder()
                .setTitle(title)
                .setCreatorUserId(userId)
                .setStatus(DiningOrderStatus.DRAFT.getCode())
                .setJoinToken(IdUtil.fastSimpleUUID())
                .setVersion(0);
        this.save(order);
        // 创建者即首个成员（detail/join 均以成员表为身份依据）
        memberMapper.insert(new DiningOrderMember().setOrderId(order.getId()).setUserId(userId));
        for (Long recipeId : distinctRecipeIds) {
            menuMapper.insert(new DiningOrderMenu().setOrderId(order.getId()).setRecipeId(recipeId));
        }
        log.info("创建点单草稿, orderId: {}, userId: {}, 菜品数: {}", order.getId(), userId, distinctRecipeIds.size());
        return new DiningOrderCreatedVo(order.getId(), order.getJoinToken());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DiningOrderFinishVo submit(Long orderId, SubmitDiningOrderFrom from, Long userId) {
        // 行锁串行化「提交」与「草稿菜单编辑」：加菜/移除的 CAS 更新同一订单行会被本锁挡住，
        // 且提交会递增 version，避免快照落库后仍有菜单写穿透到已结束订单（并发窗口丢单/脏写）。
        DiningOrder order = orderMapper.selectForUpdate(orderId);
        if (order == null) {
            throw new CustomExceptions("点单不存在");
        }
        if (!Objects.equals(order.getCreatorUserId(), userId)) {
            throw new CustomExceptions("仅发起者可提交点单");
        }
        // 幂等：已结束直接返回库内原快照，不重复聚合、不改 finished_at
        if (DiningOrderStatus.FINISHED.getCode().equals(order.getStatus())) {
            return toFinishVo(order);
        }
        if (DiningOrderStatus.CANCELLED.getCode().equals(order.getStatus())) {
            throw new CustomExceptions("点单已取消，无法提交");
        }
        if (!DiningOrderStatus.DRAFT.getCode().equals(order.getStatus())) {
            throw new CustomExceptions("点单状态异常，无法提交");
        }
        // 草稿页原位编辑的标题随 submit 落库；不传沿用库内标题，最终一律校验非空
        String title = from.getTitle() != null ? from.getTitle().trim() : order.getTitle();
        if (title == null || title.isEmpty()) {
            throw new CustomExceptions("订单标题不能为空");
        }
        if (title.length() > TITLE_MAX_LENGTH) {
            throw new CustomExceptions("订单标题过长（上限64字符）");
        }

        List<DiningOrderMenu> menus = menuMapper.selectList(new LambdaQueryWrapper<DiningOrderMenu>()
                .eq(DiningOrderMenu::getOrderId, orderId).orderByAsc(DiningOrderMenu::getId));
        if (menus.isEmpty()) {
            throw new CustomExceptions("本单菜单不能为空，无法提交点单");
        }
        List<Long> recipeIds = menus.stream().map(DiningOrderMenu::getRecipeId).toList();
        Map<Long, List<String>> ingredientMap = recipeGate.getIngredients(recipeIds);
        Map<Long, DiningRecipeGate.RecipeBrief> briefs = recipeGate.getBriefs(recipeIds);
        // v2：配料明细（关联表主数据源）+ 一份热量（calories_total ?: calories_per_100g × 250 口径在 gate 内归一）
        Map<Long, List<DiningRecipeGate.IngredientDetail>> amountsMap = recipeGate.getIngredientAmounts(recipeIds);
        Map<Long, BigDecimal> caloriesMap = recipeGate.getServingCalories(recipeIds);

        // 菜谱视图按本单菜单编排序；兼容回落：无关联明细的老菜谱沿用 ingredients JSON 名列表
        // （明细为空 = 仅名并集，value/unit 缺失）；ingredients JSON 保留双写，即便有关联明细也并入名并集
        List<RecipeIngredient> recipes = new ArrayList<>(recipeIds.size());
        for (Long recipeId : recipeIds) {
            DiningRecipeGate.RecipeBrief brief = briefs.get(recipeId);
            List<ShoppingListAssembler.IngredientAmount> amounts =
                    amountsMap.getOrDefault(recipeId, List.of()).stream()
                            .map(detail -> new ShoppingListAssembler.IngredientAmount(
                                    detail.name(), detail.amountValue(), detail.amountUnit()))
                            .toList();
            recipes.add(new RecipeIngredient(recipeId, brief == null ? null : brief.name(),
                    ingredientMap.getOrDefault(recipeId, List.of()), amounts,
                    caloriesMap.get(recipeId),
                    brief == null ? "" : minioConfig.urlOf(brief.coverObjectKey())));
        }
        // 菜单即已选清单：selections 传空列表 = 本单菜单全部入选，按菜视图无「选菜人」
        ShoppingListAssembler.ShoppingList shoppingList =
                ShoppingListAssembler.assemble(recipes, List.of());
        String snapshot = JsonUtils.toJson(shoppingList);
        LocalDateTime finishedAt = LocalDateTime.now();

        // 状态流转条件 UPDATE 防并发：仅 DRAFT 且 version 未变才迁移，原子防并发重复提交；
        // 同步递增 version，让并发的草稿菜单写（CAS 校验 version）在提交后直接冲突退出
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<DiningOrder>()
                .eq(DiningOrder::getId, orderId)
                .eq(DiningOrder::getStatus, DiningOrderStatus.DRAFT.getCode())
                .eq(DiningOrder::getVersion, from.getVersion())
                .set(DiningOrder::getStatus, DiningOrderStatus.FINISHED.getCode())
                .set(DiningOrder::getTitle, title)
                .set(DiningOrder::getShoppingList, snapshot)
                .set(DiningOrder::getFinishedAt, finishedAt)
                .setSql("version = version + 1"));
        if (updated == 0) {
            DiningOrder latest = orderMapper.selectById(orderId);
            // 并发窗口：其他提交请求已提交 → 按幂等返回其快照
            if (latest != null && DiningOrderStatus.FINISHED.getCode().equals(latest.getStatus())) {
                return toFinishVo(latest);
            }
            if (latest != null && !Objects.equals(latest.getVersion(), from.getVersion())) {
                throw new CustomExceptions(VERSION_CONFLICT_CODE, "订单已被他人更新，请刷新后重试");
            }
            throw new CustomExceptions("点单状态已变更，请刷新后重试");
        }
        // 仅本次真正完成 DRAFT→FINISHED 流转才发通知：已结束幂等返回与并发窗口都不重复打扰全体成员
        noticeService.publishTargeted(
                NoticeType.DINING_FINISH,
                "「" + title + "」已结束点单",
                "购买清单已生成，点击查看",
                buildOrderDeepLink(orderId, order.getJoinToken()),
                listNoticeReceivers(orderId, order.getCreatorUserId(), null));
        log.info("提交点单, orderId: {}, userId: {}, 标题: {}, 菜品数: {}", orderId, userId, title, recipeIds.size());
        publishChanged(orderId);
        return new DiningOrderFinishVo()
                .setId(orderId)
                .setTitle(title)
                .setStatus(DiningOrderStatus.FINISHED.getCode())
                .setFinishedAt(finishedAt)
                .setShoppingList(snapshot);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addDish(Long orderId, AddDiningOrderDishFrom from, Long userId) {
        DiningOrder order = requireDraftOrder(orderId);
        requireMember(orderId, userId, "仅订单成员可以加菜");
        // 幂等：已在本单菜单内直接成功返回（不重复落库、不递增 version）
        if (isMenuDishExists(orderId, from.getRecipeId())) {
            log.debug("加菜幂等命中, orderId: {}, recipeId: {}", orderId, from.getRecipeId());
            return;
        }
        // 上新建档：下架/不存在的菜谱不得进入本单菜单
        recipeGate.assertAllOnShelf(List.of(from.getRecipeId()));
        casIncrementVersion(orderId, from.getVersion());
        try {
            menuMapper.insert(new DiningOrderMenu().setOrderId(orderId).setRecipeId(from.getRecipeId()));
        } catch (DuplicateKeyException e) {
            // 并发窗口撞 uk_order_recipe：他人先插入同菜，按幂等成功处理
            log.debug("加菜并发撞唯一索引，按幂等成功处理, orderId: {}, recipeId: {}", orderId, from.getRecipeId());
        }
        log.info("草稿加菜, orderId: {}, recipeId: {}, userId: {}", orderId, from.getRecipeId(), userId);
        publishChanged(orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeDish(Long orderId, Long recipeId, Long version, Long userId) {
        requireDraftOrder(orderId);
        // 拍板②：草稿期全部成员可移除任意菜（含发起者加的菜），不校验归属
        requireMember(orderId, userId, "仅订单成员可以移除菜");
        // 幂等：菜已不在本单菜单内直接成功返回（不递增 version）
        if (!isMenuDishExists(orderId, recipeId)) {
            log.debug("移除菜幂等命中, orderId: {}, recipeId: {}", orderId, recipeId);
            return;
        }
        casIncrementVersion(orderId, version);
        menuMapper.delete(new LambdaQueryWrapper<DiningOrderMenu>()
                .eq(DiningOrderMenu::getOrderId, orderId)
                .eq(DiningOrderMenu::getRecipeId, recipeId));
        log.info("草稿移除菜, orderId: {}, recipeId: {}, userId: {}", orderId, recipeId, userId);
        publishChanged(orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long orderId, Long userId) {
        DiningOrder order = this.getById(orderId);
        if (order == null) {
            throw new CustomExceptions("点单不存在");
        }
        if (!Objects.equals(order.getCreatorUserId(), userId)) {
            throw new CustomExceptions("仅发起者可放弃草稿");
        }
        if (!DiningOrderStatus.DRAFT.getCode().equals(order.getStatus())) {
            throw new CustomExceptions(DiningOrderStatus.CANCELLED.getCode().equals(order.getStatus())
                    ? "点单已取消，无需重复操作" : "仅草稿可放弃");
        }
        // 条件 UPDATE 置 CANCELLED：与并发 submit 互斥——submit 已把状态推到 FINISHED 时本更新 0 行 → 拒绝，
        // 不产生「草稿已提交但被改成取消」的中间态；同步递增 version 让轮询端也能感知状态变更
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<DiningOrder>()
                .eq(DiningOrder::getId, orderId)
                .eq(DiningOrder::getStatus, DiningOrderStatus.DRAFT.getCode())
                .set(DiningOrder::getStatus, DiningOrderStatus.CANCELLED.getCode())
                .set(DiningOrder::getCancelledAt, LocalDateTime.now())
                .setSql("version = version + 1"));
        if (updated == 0) {
            throw new CustomExceptions("草稿状态已变更，请刷新后重试");
        }
        log.info("放弃点单草稿（置 CANCELLED）, orderId: {}, userId: {}", orderId, userId);
        publishChanged(orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void leave(Long orderId, Long userId) {
        DiningOrder order = this.getById(orderId);
        if (order == null) {
            throw new CustomExceptions("点单不存在");
        }
        if (!DiningOrderStatus.DRAFT.getCode().equals(order.getStatus())) {
            throw new CustomExceptions(DiningOrderStatus.CANCELLED.getCode().equals(order.getStatus())
                    ? "点单已取消，无法退出" : "点单已结束，无法退出");
        }
        if (Objects.equals(order.getCreatorUserId(), userId)) {
            throw new CustomExceptions("发起者不能退出，如需结束请使用放弃草稿");
        }
        // 幂等：已不在单中直接成功返回
        if (!isMember(orderId, userId)) {
            return;
        }
        memberMapper.delete(new LambdaQueryWrapper<DiningOrderMember>()
                .eq(DiningOrderMember::getOrderId, orderId)
                .eq(DiningOrderMember::getUserId, userId));
        log.info("用户退出点单, orderId: {}, userId: {}", orderId, userId);
        publishChanged(orderId);
    }

    @Override
    public DiningOrderActiveVo getMyActiveOrder(Long userId) {
        DiningOrder order = findMyActiveOrder(userId);
        if (order == null) {
            return null;
        }
        return new DiningOrderActiveVo()
                .setOrderId(order.getId())
                .setTitle(order.getTitle())
                .setStatus(order.getStatus());
    }

    /**
     * 查当前用户自己发起的进行中 DRAFT（取最新一张）。唯一闸门与 active 查询共用，
     * 走 idx_creator；limit 1 常量拼接，避免同秒多单时 selectOne 抛异常。
     */
    private DiningOrder findMyActiveOrder(Long userId) {
        List<DiningOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<DiningOrder>()
                .eq(DiningOrder::getCreatorUserId, userId)
                .eq(DiningOrder::getStatus, DiningOrderStatus.DRAFT.getCode())
                .orderByDesc(DiningOrder::getId)
                .last("limit 1"));
        return orders.isEmpty() ? null : orders.get(0);
    }

    @Override
    public Page<DiningOrderListVo> pageMy(BaseSearchFrom from, Long userId) {
        // 「我发起 + 我参与」的合并与倒序由 mapper 单条 SQL 完成（EXISTS 走成员表 idx_user），
        // 不再先全量反查 orderId 再内存排序，避免深分页时内存与 IO 放大
        Page<DiningOrder> page = orderMapper.selectMyOrderPage(
                new Page<>(from.getPage(), from.getSize()), userId);
        List<DiningOrder> records = page.getRecords();
        // 列表摘要批量组装：一次查菜单 + 一次取食材名，避免逐行 N+1
        Map<Long, Integer> menuCounts = new LinkedHashMap<>();
        Map<Long, String> ingredientSummaries = new LinkedHashMap<>();
        if (!records.isEmpty()) {
            List<Long> orderIds = records.stream().map(DiningOrder::getId).toList();
            Map<Long, List<Long>> recipeIdsByOrder = new LinkedHashMap<>();
            for (DiningOrderMenu menu : menuMapper.selectList(new LambdaQueryWrapper<DiningOrderMenu>()
                    .in(DiningOrderMenu::getOrderId, orderIds)
                    .orderByAsc(DiningOrderMenu::getId))) {
                recipeIdsByOrder.computeIfAbsent(menu.getOrderId(), k -> new ArrayList<>())
                        .add(menu.getRecipeId());
            }
            List<Long> allRecipeIds = recipeIdsByOrder.values().stream()
                    .flatMap(List::stream).distinct().toList();
            Map<Long, List<String>> ingredientMap = allRecipeIds.isEmpty()
                    ? Map.of() : recipeGate.getIngredients(allRecipeIds);
            for (DiningOrder order : records) {
                List<Long> recipeIds = recipeIdsByOrder.getOrDefault(order.getId(), List.of());
                menuCounts.put(order.getId(), recipeIds.size());
                ingredientSummaries.put(order.getId(), summarizeIngredients(recipeIds, ingredientMap));
            }
        }
        Page<DiningOrderListVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(records.stream()
                .map(order -> new DiningOrderListVo()
                        .setId(order.getId())
                        .setTitle(order.getTitle())
                        .setStatus(order.getStatus())
                        .setIsCreator(order.getCreatorUserId() != null && order.getCreatorUserId().equals(userId))
                        .setCreatorUserId(order.getCreatorUserId())
                        .setMenuCount(menuCounts.getOrDefault(order.getId(), 0))
                        .setIngredientSummary(ingredientSummaries.getOrDefault(order.getId(), ""))
                        .setCreatedAt(order.getCreatedAt())
                        .setFinishedAt(order.getFinishedAt()))
                .toList());
        return voPage;
    }

    /** 食材摘要：复用购买清单聚合口径取食材名并集（保序去重），截前 6 个以「、」连接，超尾加「…」 */
    private String summarizeIngredients(List<Long> recipeIds, Map<Long, List<String>> ingredientMap) {
        if (recipeIds.isEmpty()) {
            return "";
        }
        List<RecipeIngredient> recipes = recipeIds.stream()
                .map(recipeId -> new RecipeIngredient(recipeId, null, ingredientMap.get(recipeId)))
                .toList();
        List<String> names = ShoppingListAssembler.assemble(recipes, List.of()).ingredientNames();
        if (names.isEmpty()) {
            return "";
        }
        List<String> top = names.size() > INGREDIENT_SUMMARY_LIMIT
                ? names.subList(0, INGREDIENT_SUMMARY_LIMIT) : names;
        String joined = String.join("、", top);
        return names.size() > INGREDIENT_SUMMARY_LIMIT ? joined + "…" : joined;
    }

    @Override
    public DiningOrderDetailVo detail(Long orderId, Long knownVersion, Long userId) {
        DiningOrder order = this.getById(orderId);
        if (order == null) {
            throw new CustomExceptions("点单不存在");
        }
        requireMember(orderId, userId, "仅订单成员可查看点单详情");

        DiningOrderDetailVo vo = new DiningOrderDetailVo()
                .setId(order.getId())
                .setVersion(toLong(order.getVersion()));

        // version 轮询协议：客户端已知 version 与服务端一致 → 轻量响应（仅 id/version/unchanged），跳过全量组装
        if (knownVersion != null && order.getVersion() != null
                && knownVersion.longValue() == order.getVersion().longValue()) {
            return vo.setUnchanged(true);
        }

        vo.setUnchanged(false)
                .setTitle(order.getTitle())
                .setStatus(order.getStatus())
                .setCreatorUserId(order.getCreatorUserId())
                .setFinishedAt(order.getFinishedAt())
                .setShoppingList(order.getShoppingList())
                // 加入凭证仅发起者可见：发起者从列表/详情可再次生成二维码，
                // 参与者响应不带 token（旧二维码泄露面收敛在创建响应与发起者详情两处）
                .setJoinToken(order.getCreatorUserId() != null
                        && order.getCreatorUserId().equals(userId) ? order.getJoinToken() : null);

        // 本单菜单 + 菜谱名称/分类（端上按分类组织左侧分类栏）
        List<DiningOrderMenu> menus = menuMapper.selectList(new LambdaQueryWrapper<DiningOrderMenu>()
                .eq(DiningOrderMenu::getOrderId, orderId).orderByAsc(DiningOrderMenu::getId));
        Map<Long, DiningRecipeGate.RecipeBrief> briefs = recipeGate.getBriefs(
                menus.stream().map(DiningOrderMenu::getRecipeId).toList());
        vo.setMenu(menus.stream().map(menu -> {
            DiningRecipeGate.RecipeBrief brief = briefs.get(menu.getRecipeId());
            return new DiningOrderDetailVo.MenuRecipe()
                    .setRecipeId(menu.getRecipeId())
                    .setName(brief == null ? null : brief.name())
                    .setCategoryId(brief == null ? null : brief.categoryId());
        }).toList());

        // 选菜记录（历史数据，已停止写入；新单恒为空，保留字段兼容旧端）
        vo.setItems(itemMapper.selectList(new LambdaQueryWrapper<DiningOrderItem>()
                        .eq(DiningOrderItem::getOrderId, orderId).orderByAsc(DiningOrderItem::getId))
                .stream().map(item -> new DiningOrderDetailVo.OrderItem()
                        .setRecipeId(item.getRecipeId())
                        .setUserId(item.getUserId())
                        .setNote(item.getNote()))
                .toList());

        // 参与者（发起者标记 + 脱敏手机号作选菜人标识）
        List<DiningOrderMember> members = memberMapper.selectList(new LambdaQueryWrapper<DiningOrderMember>()
                .eq(DiningOrderMember::getOrderId, orderId).orderByAsc(DiningOrderMember::getId));
        Map<Long, String> maskedPhones = loadMaskedPhones(
                members.stream().map(DiningOrderMember::getUserId).toList());
        vo.setMembers(members.stream().map(member -> new DiningOrderDetailVo.OrderMember()
                .setUserId(member.getUserId())
                .setIsCreator(member.getUserId() != null && member.getUserId().equals(order.getCreatorUserId()))
                .setPhoneMasked(maskedPhones.get(member.getUserId())))
                .toList());
        return vo;
    }

    @Override
    public DiningOrderBriefVo brief(String joinToken, Long userId) {
        DiningOrder order = this.getOne(new LambdaQueryWrapper<DiningOrder>()
                .eq(DiningOrder::getJoinToken, joinToken), false);
        if (order == null) {
            // 扫码入口统一文案：不区分单不存在与 token 已重置，避免泄露单号有效性
            throw new CustomExceptions("加入凭证无效或已重置");
        }
        Users creator = usersMapper.selectById(order.getCreatorUserId());
        return new DiningOrderBriefVo()
                .setOrderId(order.getId())
                .setTitle(order.getTitle())
                .setStatus(order.getStatus())
                .setCreatorName(creator == null ? null : maskPhone(creator.getPhoneNumber()))
                .setRecipeCount(menuMapper.selectCount(new LambdaQueryWrapper<DiningOrderMenu>()
                        .eq(DiningOrderMenu::getOrderId, order.getId())))
                .setJoined(isMember(order.getId(), userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void join(Long orderId, JoinDiningOrderFrom from, Long userId) {
        DiningOrder order = this.getById(orderId);
        if (order == null) {
            throw new CustomExceptions("点单不存在");
        }
        if (!order.getJoinToken().equals(from.getJoinToken())) {
            throw new CustomExceptions("加入凭证无效或已重置");
        }
        // 仅草稿可邀请加入（边点边邀）；已结束拒绝
        if (!DiningOrderStatus.isJoinable(order.getStatus())) {
            throw new CustomExceptions(DiningOrderStatus.CANCELLED.getCode().equals(order.getStatus())
                    ? "点单已取消，无法加入" : "点单已结束，无法加入");
        }
        if (isMember(orderId, userId)) {
            throw new CustomExceptions("你已在该点单中");
        }
        // 接收人快照在插入前取：发起者 + 既有参与者；操作人此刻还不是成员，天然被排除
        // （发起者即使因历史脏数据不在成员表也显式补入，保证必达）
        List<Long> receivers = listNoticeReceivers(orderId, order.getCreatorUserId(), userId);
        try {
            memberMapper.insert(new DiningOrderMember().setOrderId(orderId).setUserId(userId));
        } catch (DuplicateKeyException e) {
            // 并发窗口撞 uk_order_user：仍按防重复语义拒绝
            throw new CustomExceptions("你已在该点单中");
        }
        // 事件发布与业务写同事务：notice 行随成员插入一起提交/回滚，不丢通知；
        // 发布失败向上抛出触发整体回滚（不吞异常，避免「已加入但无通知」的静默不一致）
        noticeService.publishTargeted(
                NoticeType.DINING_JOIN,
                "有人加入了「" + order.getTitle() + "」",
                "点单有新成员加入，点击查看详情",
                buildOrderDeepLink(order.getId(), order.getJoinToken()),
                receivers);
        log.info("用户加入点单, orderId: {}, userId: {}", orderId, userId);
        publishChanged(orderId);
    }

    @Override
    public String resetJoinToken(Long orderId, Long userId) {
        DiningOrder order = this.getById(orderId);
        if (order == null) {
            throw new CustomExceptions("点单不存在");
        }
        if (!order.getCreatorUserId().equals(userId)) {
            throw new CustomExceptions("仅发起者可重置加入凭证");
        }
        if (!DiningOrderStatus.DRAFT.getCode().equals(order.getStatus())) {
            throw new CustomExceptions(DiningOrderStatus.CANCELLED.getCode().equals(order.getStatus())
                    ? "点单已取消，无需重置加入凭证" : "点单已结束，无需重置加入凭证");
        }
        String newToken = IdUtil.fastSimpleUUID();
        // 精确 SET join_token，不触碰 version（version 只由菜单编辑/提交流更协议驱动）
        this.update(new LambdaUpdateWrapper<DiningOrder>()
                .eq(DiningOrder::getId, orderId)
                .set(DiningOrder::getJoinToken, newToken));
        log.info("重置点单加入凭证, orderId: {}, userId: {}", orderId, userId);
        publishChanged(orderId);
        return newToken;
    }

    private DiningOrderFinishVo toFinishVo(DiningOrder order) {
        return new DiningOrderFinishVo()
                .setId(order.getId())
                .setTitle(order.getTitle())
                .setStatus(order.getStatus())
                .setFinishedAt(order.getFinishedAt())
                .setShoppingList(order.getShoppingList());
    }

    /**
     * 事件通知接收人：全体成员（发起者显式兜底 + 成员表），可选排除操作人。
     * 保序去重，过滤空 user_id（脏数据防御）。
     */
    private List<Long> listNoticeReceivers(Long orderId, Long creatorUserId, Long excludeUserId) {
        LinkedHashSet<Long> receivers = new LinkedHashSet<>();
        if (creatorUserId != null) {
            receivers.add(creatorUserId);
        }
        memberMapper.selectList(new LambdaQueryWrapper<DiningOrderMember>()
                        .eq(DiningOrderMember::getOrderId, orderId))
                .forEach(member -> {
                    if (member.getUserId() != null) {
                        receivers.add(member.getUserId());
                    }
                });
        if (excludeUserId != null) {
            receivers.remove(excludeUserId);
        }
        return List.copyOf(receivers);
    }

    /**
     * 点单详情深链（与 Android dining 域 DeepLinkContributor 解析口径一致）：
     * `smartwash://dining/order/{orderId}?t={joinToken}`——携带加入凭证，
     * 成员直达详情、非成员经 brief 确认加入（见 12 号票）。
     */
    private String buildOrderDeepLink(Long orderId, String joinToken) {
        return "smartwash://dining/order/" + orderId + "?t=" + joinToken;
    }

    /**
     * 解析本单菜单来源：fromOrderId 复用历史菜单（当前用户须为该单成员）或显式 recipeIds，二选一。
     */
    private List<Long> resolveMenuRecipeIds(CreateDiningOrderFrom from, Long userId) {
        if (from.getFromOrderId() != null) {
            if (!CollectionUtils.isEmpty(from.getRecipeIds())) {
                throw new CustomExceptions("recipeIds 与 fromOrderId 只能二选一");
            }
            DiningOrder source = this.getById(from.getFromOrderId());
            if (source == null) {
                throw new CustomExceptions("要复用的点单不存在");
            }
            requireMember(source.getId(), userId, "仅订单成员可复用历史菜单");
            return menuMapper.selectList(new LambdaQueryWrapper<DiningOrderMenu>()
                            .eq(DiningOrderMenu::getOrderId, source.getId())
                            .orderByAsc(DiningOrderMenu::getId))
                    .stream().map(DiningOrderMenu::getRecipeId).toList();
        }
        // 显式 recipeIds 为空 = 空草稿（合法），submit 时才校验菜单非空；
        // 入参整体缺省时返回空列表，避免后续 stream 空指针
        return CollectionUtils.isEmpty(from.getRecipeIds()) ? List.of() : from.getRecipeIds();
    }

    /** 仅 DRAFT 态可编辑本单菜单（加菜/移除），返回订单供调用方复用 */
    private DiningOrder requireDraftOrder(Long orderId) {
        DiningOrder order = this.getById(orderId);
        if (order == null) {
            throw new CustomExceptions("点单不存在");
        }
        if (!DiningOrderStatus.DRAFT.getCode().equals(order.getStatus())) {
            throw new CustomExceptions(DiningOrderStatus.CANCELLED.getCode().equals(order.getStatus())
                    ? "点单已取消，不能编辑菜单" : "点单已结束，不能编辑菜单");
        }
        return order;
    }

    /**
     * SSE 订阅资格校验：订单存在且当前用户为成员（与 detail 同口径，但不组装载荷）。
     * CANCELLED/FINISHED 终态同样允许订阅——端上需要感知状态变化并切只读/结束视图。
     */
    @Override
    public void assertReadable(Long orderId, Long userId) {
        if (this.getById(orderId) == null) {
            throw new CustomExceptions("点单不存在");
        }
        requireMember(orderId, userId, "仅订单成员可查看点单详情");
    }

    /**
     * 事务提交后再广播变更信号：避免「广播先到、端上回拉仍是旧值」的时序错位；
     * 无事务上下文（如纯单测直调）时立即执行。信号只带 orderId，端上收到后回拉详情。
     */
    private void publishChanged(Long orderId) {
        Runnable broadcast = () -> streamer.publish(orderId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broadcast.run();
                }
            });
        } else {
            broadcast.run();
        }
    }

    /** 本单菜单是否已含该菜（幂等判定依据） */
    private boolean isMenuDishExists(Long orderId, Long recipeId) {
        Long count = menuMapper.selectCount(new LambdaQueryWrapper<DiningOrderMenu>()
                .eq(DiningOrderMenu::getOrderId, orderId)
                .eq(DiningOrderMenu::getRecipeId, recipeId));
        return count != null && count > 0;
    }

    /**
     * version 乐观并发闸门：条件 UPDATE（WHERE id=? AND version=? SET version=version+1）原子递增，
     * 影响行数 0 = 客户端已知版本过期 → 业务错误码 409，客户端拉取最新详情后重放。
     * 幂等命中的请求在闸门之前返回，不产生无谓版本递增。
     */
    private void casIncrementVersion(Long orderId, Long knownVersion) {
        if (knownVersion == null) {
            throw new CustomExceptions("已知版本号 version 不能为空");
        }
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<DiningOrder>()
                .eq(DiningOrder::getId, orderId)
                .eq(DiningOrder::getVersion, knownVersion)
                .setSql("version = version + 1"));
        if (updated == 0) {
            throw new CustomExceptions(VERSION_CONFLICT_CODE, "订单已被他人更新，请刷新后重试");
        }
    }

    private boolean isMember(Long orderId, Long userId) {
        Long count = memberMapper.selectCount(new LambdaQueryWrapper<DiningOrderMember>()
                .eq(DiningOrderMember::getOrderId, orderId)
                .eq(DiningOrderMember::getUserId, userId));
        return count != null && count > 0;
    }

    private void requireMember(Long orderId, Long userId, String message) {
        if (!isMember(orderId, userId)) {
            throw new CustomExceptions(message);
        }
    }

    /** 批量取参与者脱敏手机号（选菜人标识），用户不存在时该条缺失不影响其他成员展示 */
    private Map<Long, String> loadMaskedPhones(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return usersMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(Users::getUserId,
                        user -> maskPhone(user.getPhoneNumber()), (a, b) -> a));
    }

    /** 手机号脱敏：11 位按 3+4+4 掩中间，其余长度保留前 3 后 2；空值返回空串 */
    private String maskPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return "";
        }
        if (phone.length() == 11) {
            return phone.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2");
        }
        if (phone.length() <= 5) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 2);
    }

    private Long toLong(Integer value) {
        return value == null ? null : value.longValue();
    }
}
