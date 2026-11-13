package com.smartwash.dining.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.dining.constant.DiningOrderStatus;
import com.smartwash.dining.core.ShoppingListAssembler;
import com.smartwash.dining.core.ShoppingListAssembler.RecipeIngredient;
import com.smartwash.dining.core.ShoppingListAssembler.Selection;
import com.smartwash.dining.entity.DiningOrder;
import com.smartwash.dining.entity.DiningOrderItem;
import com.smartwash.dining.entity.DiningOrderMember;
import com.smartwash.dining.entity.DiningOrderMenu;
import com.smartwash.dining.from.CreateDiningOrderFrom;
import com.smartwash.dining.from.JoinDiningOrderFrom;
import com.smartwash.dining.mapper.DiningOrderItemMapper;
import com.smartwash.dining.mapper.DiningOrderMapper;
import com.smartwash.dining.mapper.DiningOrderMemberMapper;
import com.smartwash.dining.mapper.DiningOrderMenuMapper;
import com.smartwash.dining.service.DiningOrderService;
import com.smartwash.dining.service.DiningRecipeGate;
import com.smartwash.dining.vo.DiningOrderBriefVo;
import com.smartwash.dining.vo.DiningOrderCreatedVo;
import com.smartwash.dining.vo.DiningOrderDetailVo;
import com.smartwash.dining.vo.DiningOrderFinishVo;
import com.smartwash.dining.vo.DiningOrderListVo;
import com.smartwash.entity.Users;
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
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 点单订单服务实现。
 * 并发纪律：加入去重先查后插给友好提示，并发窗口由 uk_order_user 唯一索引兜底裁决；
 * 选菜并发走 DiningOrderItemService 的 version 乐观并发协议，本类不直接改 version。
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DiningOrderCreatedVo create(CreateDiningOrderFrom from, Long userId) {
        List<Long> recipeIds = resolveMenuRecipeIds(from, userId);
        // 去重保序（同菜重复勾选入参防御），随后整体把关上架存在性
        List<Long> distinctRecipeIds = recipeIds.stream().filter(Objects::nonNull).distinct().toList();
        recipeGate.assertAllOnShelf(distinctRecipeIds);

        DiningOrder order = new DiningOrder()
                .setTitle(from.getTitle())
                .setCreatorUserId(userId)
                .setStatus(DiningOrderStatus.ONGOING.getCode())
                .setJoinToken(IdUtil.fastSimpleUUID())
                .setVersion(0);
        this.save(order);
        // 创建者即首个成员（detail/join 均以成员表为身份依据）
        memberMapper.insert(new DiningOrderMember().setOrderId(order.getId()).setUserId(userId));
        for (Long recipeId : distinctRecipeIds) {
            menuMapper.insert(new DiningOrderMenu().setOrderId(order.getId()).setRecipeId(recipeId));
        }
        log.info("创建点单订单, orderId: {}, userId: {}, 菜品数: {}", order.getId(), userId, distinctRecipeIds.size());
        return new DiningOrderCreatedVo(order.getId(), order.getJoinToken());
    }

    @Override
    public List<DiningOrderListVo> listMy(Long userId) {
        // 我参与的（创建即首个成员，成员表已覆盖我发起的单）；发起人直查兜底防御历史脏数据
        LinkedHashSet<Long> orderIds = memberMapper.selectList(new LambdaQueryWrapper<DiningOrderMember>()
                        .eq(DiningOrderMember::getUserId, userId))
                .stream().map(DiningOrderMember::getOrderId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        this.list(new LambdaQueryWrapper<DiningOrder>().eq(DiningOrder::getCreatorUserId, userId))
                .forEach(order -> orderIds.add(order.getId()));

        if (orderIds.isEmpty()) {
            return List.of();
        }
        return this.listByIds(orderIds).stream()
                .sorted(Comparator.comparing(DiningOrder::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(order -> new DiningOrderListVo()
                        .setId(order.getId())
                        .setTitle(order.getTitle())
                        .setStatus(order.getStatus())
                        .setIsCreator(order.getCreatorUserId() != null && order.getCreatorUserId().equals(userId))
                        .setCreatorUserId(order.getCreatorUserId())
                        .setCreatedAt(order.getCreatedAt())
                        .setFinishedAt(order.getFinishedAt()))
                .toList();
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

        // 选菜记录（共享购物车明细）
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
        if (!DiningOrderStatus.ONGOING.getCode().equals(order.getStatus())) {
            throw new CustomExceptions("点单已结束，无法加入");
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
        if (!DiningOrderStatus.ONGOING.getCode().equals(order.getStatus())) {
            throw new CustomExceptions("点单已结束，无需重置加入凭证");
        }
        String newToken = IdUtil.fastSimpleUUID();
        // 精确 SET join_token，不触碰 version（version 只由选菜变更协议驱动）
        this.update(new LambdaUpdateWrapper<DiningOrder>()
                .eq(DiningOrder::getId, orderId)
                .set(DiningOrder::getJoinToken, newToken));
        log.info("重置点单加入凭证, orderId: {}, userId: {}", orderId, userId);
        return newToken;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DiningOrderFinishVo finish(Long orderId, Long userId) {
        // 行锁串行化「结束」与「选菜写入」：选菜 CAS 更新同一订单行会被本锁挡住，
        // 避免快照读完后、状态落库前有选菜落库而漏进购买清单（并发窗口丢单）。
        DiningOrder order = orderMapper.selectForUpdate(orderId);
        if (order == null) {
            throw new CustomExceptions("点单不存在");
        }
        if (!Objects.equals(order.getCreatorUserId(), userId)) {
            throw new CustomExceptions("仅发起者可结束点单");
        }
        // 幂等：已结束直接返回库内原快照，不重复聚合、不改 finished_at
        if (DiningOrderStatus.FINISHED.getCode().equals(order.getStatus())) {
            return toFinishVo(order);
        }
        if (!DiningOrderStatus.ONGOING.getCode().equals(order.getStatus())) {
            throw new CustomExceptions("点单状态异常，无法结束");
        }

        List<DiningOrderMenu> menus = menuMapper.selectList(new LambdaQueryWrapper<DiningOrderMenu>()
                .eq(DiningOrderMenu::getOrderId, orderId).orderByAsc(DiningOrderMenu::getId));
        List<Long> recipeIds = menus.stream().map(DiningOrderMenu::getRecipeId).toList();
        Map<Long, List<String>> ingredientMap = recipeGate.getIngredients(recipeIds);
        Map<Long, DiningRecipeGate.RecipeBrief> briefs = recipeGate.getBriefs(recipeIds);

        // 菜谱视图按本单菜单编排序；选菜记录按勾选顺序（按菜视图内选菜人顺序）
        List<RecipeIngredient> recipes = new ArrayList<>(recipeIds.size());
        for (Long recipeId : recipeIds) {
            DiningRecipeGate.RecipeBrief brief = briefs.get(recipeId);
            recipes.add(new RecipeIngredient(recipeId, brief == null ? null : brief.name(),
                    ingredientMap.getOrDefault(recipeId, List.of())));
        }
        List<Selection> selections = itemMapper.selectList(new LambdaQueryWrapper<DiningOrderItem>()
                        .eq(DiningOrderItem::getOrderId, orderId).orderByAsc(DiningOrderItem::getId))
                .stream().map(item -> new Selection(item.getRecipeId(), item.getUserId(), item.getNote()))
                .toList();

        ShoppingListAssembler.ShoppingList shoppingList =
                ShoppingListAssembler.assemble(recipes, selections);
        String snapshot = JsonUtils.toJson(shoppingList);
        LocalDateTime finishedAt = LocalDateTime.now();

        // 状态流转条件 UPDATE 防并发：仅 ONGOING 才能结束
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<DiningOrder>()
                .eq(DiningOrder::getId, orderId)
                .eq(DiningOrder::getStatus, DiningOrderStatus.ONGOING.getCode())
                .set(DiningOrder::getStatus, DiningOrderStatus.FINISHED.getCode())
                .set(DiningOrder::getShoppingList, snapshot)
                .set(DiningOrder::getFinishedAt, finishedAt));
        if (updated == 0) {
            // 并发窗口：其他结束请求已提交 → 按幂等返回其快照；否则状态被异常改动，拒绝
            DiningOrder latest = orderMapper.selectById(orderId);
            if (latest != null && DiningOrderStatus.FINISHED.getCode().equals(latest.getStatus())) {
                return toFinishVo(latest);
            }
            throw new CustomExceptions("点单状态已变更，请刷新后重试");
        }
        // 仅本次真正完成 ONGOING→FINISHED 流转才发通知：已结束幂等返回与并发窗口都不重复打扰全体成员
        noticeService.publishTargeted(
                NoticeType.DINING_FINISH,
                "「" + order.getTitle() + "」已结束点单",
                "购买清单已生成，点击查看",
                buildOrderDeepLink(orderId, order.getJoinToken()),
                listNoticeReceivers(orderId, order.getCreatorUserId(), null));
        log.info("结束点单, orderId: {}, userId: {}, 选菜记录数: {}", orderId, userId, selections.size());
        return new DiningOrderFinishVo()
                .setId(orderId)
                .setTitle(order.getTitle())
                .setStatus(DiningOrderStatus.FINISHED.getCode())
                .setFinishedAt(finishedAt)
                .setShoppingList(snapshot);
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
        if (CollectionUtils.isEmpty(from.getRecipeIds())) {
            throw new CustomExceptions("本单菜单不能为空");
        }
        return from.getRecipeIds();
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
