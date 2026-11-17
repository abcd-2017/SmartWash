package com.smartwash.dining.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.config.MinioConfig;
import com.smartwash.dining.entity.DiningCategory;
import com.smartwash.dining.entity.DiningIngredient;
import com.smartwash.dining.entity.DiningRecipe;
import com.smartwash.dining.entity.DiningRecipeFavorite;
import com.smartwash.dining.entity.DiningRecipeIngredient;
import com.smartwash.dining.from.AddDiningRecipeFrom;
import com.smartwash.dining.from.SearchDiningRecipeFrom;
import com.smartwash.dining.from.SearchWebDiningRecipeFrom;
import com.smartwash.dining.from.UpdateDiningRecipeFrom;
import com.smartwash.dining.mapper.DiningCategoryMapper;
import com.smartwash.dining.mapper.DiningIngredientMapper;
import com.smartwash.dining.mapper.DiningRecipeFavoriteMapper;
import com.smartwash.dining.mapper.DiningRecipeIngredientMapper;
import com.smartwash.dining.mapper.DiningRecipeMapper;
import com.smartwash.dining.service.DiningRecipeService;
import com.smartwash.dining.support.RecipeImageValidator;
import com.smartwash.dining.vo.DiningRecipeVo;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.service.FileStorageService;
import com.smartwash.utils.JsonUtils;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.SecurityUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜谱服务实现（管理端 02 号票 / 用户端 04 号票）。
 * 分类存在性由服务层校验（category_id 无外键约束，脏数据在写入前拦截）；
 * 封面上传走统一 FileStorageService（DB 存对象键、读时拼直链），校验对齐头像上传惯例（魔数+扩展名+大小）。
 * 下架语义：仅影响上架查询（status=1 过滤），历史点单订单按菜谱 ID 引用，不受上下架影响（spec 决策）。
 * 用户端新增无审核流：保存即上架、全员可见（source=USER，spec 决策 7/8）。
 */
@Slf4j
@Service
public class DiningRecipeServiceImpl extends ServiceImpl<DiningRecipeMapper, DiningRecipe>
        implements DiningRecipeService {

    /** 封面对象前缀（bucket 内独立命名空间，已加入匿名只读白名单 MinioPublicReadPolicy） */
    public static final String COVER_DIRECTORY = "dining/covers";

    /** 管理端录入来源标识 */
    private static final String SOURCE_ADMIN = "ADMIN";

    /** 用户端新增来源标识 */
    private static final String SOURCE_USER = "USER";

    /** 分类 Mapper：写入前校验分类存在 + Vo 分类名联查（依赖 Mapper 而非 Service，避免服务间环） */
    @Autowired
    private DiningCategoryMapper diningCategoryMapper;

    /** 统一文件存储（MinIO）：DB 存对象键，直链由 MinioConfig.urlOf 拼装 */
    @Autowired
    private FileStorageService fileStorageService;

    /** 对外直链拼装（对象键 → 绝对 URL） */
    @Autowired
    private MinioConfig minioConfig;

    @Autowired
    private SecurityUtil securityUtil;

    /** 菜谱配料关联 Mapper：详情过敏原聚合（配料 ID → 过敏原） */
    @Autowired
    private DiningRecipeIngredientMapper recipeIngredientMapper;

    /** 配料库 Mapper：详情过敏原聚合（关联行 → 配料过敏原） */
    @Autowired
    private DiningIngredientMapper ingredientMapper;

    /** 菜谱收藏 Mapper：favorited 批量填充与收藏增删查（依赖 Mapper 而非服务，避免服务间环） */
    @Autowired
    private DiningRecipeFavoriteMapper favoriteMapper;


    @Override
    public Page<DiningRecipeVo> adminPage(SearchDiningRecipeFrom from) {
        LambdaQueryWrapper<DiningRecipe> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(from.getCategoryId() != null, DiningRecipe::getCategoryId, from.getCategoryId());
        wrapper.eq(from.getStatus() != null, DiningRecipe::getStatus, from.getStatus());
        wrapper.like(StringUtils.hasText(from.getKeyword()), DiningRecipe::getName, from.getKeyword());
        // 管理端列表按 id 降序：新菜谱在前，运营优先处理最新录入
        wrapper.orderByDesc(DiningRecipe::getId);

        Page<DiningRecipe> page = this.page(new Page<>(from.getPage(), from.getSize()), wrapper);
        List<DiningRecipeVo> vos = page.getRecords().stream().map(this::buildVo).toList();
        fillCategoryNames(page.getRecords(), vos);
        Page<DiningRecipeVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(vos);
        return voPage;
    }

    @Override
    public void addRecipe(AddDiningRecipeFrom from) {
        // 分类哨兵归一：0=未分类（Web 下拉的「未分类」选项），统一落库为 null
        Long categoryId = normalizeUncategorized(from.getCategoryId());
        // 分类传入时校验存在（category_id 无外键约束，写前拦截避免悬空引用）
        checkCategoryExists(categoryId);

        DiningRecipe recipe = new DiningRecipe();
        recipe.setName(from.getName());
        recipe.setCategoryId(categoryId);
        recipe.setSteps(from.getSteps());
        recipe.setIngredients(serializeIngredients(from.getIngredients()));
        // 封面上传与表单分两次提交：先拿对象键，随新增/编辑一起落库（缺失时保持 null）
        recipe.setCoverObjectKey(from.getCoverObjectKey());
        // 营养成分（契约 §3.2）：可空=未录入，随表单直接落库
        recipe.setCaloriesPer100g(from.getCaloriesPer100g());
        recipe.setCaloriesTotal(from.getCaloriesTotal());
        recipe.setProteinG(from.getProteinG());
        recipe.setFatG(from.getFatG());
        recipe.setCarbsG(from.getCarbsG());
        recipe.setSodiumMg(from.getSodiumMg());
        // 管理端录入：来源固定 ADMIN、创建人取当前登录管理员，默认上架（下架需显式走上下架接口）
        recipe.setSource(SOURCE_ADMIN);
        recipe.setStatus(DiningRecipe.STATUS_ON_SHELF);
        recipe.setCreatedByAdminId(currentAdminId());
        this.save(recipe);
        log.info("新增菜谱, id: {}, name: {}, categoryId: {}, adminId: {}",
                recipe.getId(), recipe.getName(), recipe.getCategoryId(), recipe.getCreatedByAdminId());
    }

    @Override
    public void updateRecipe(UpdateDiningRecipeFrom from) {
        DiningRecipe recipe = this.getById(from.getId());
        if (recipe == null) {
            throw new CustomExceptions("菜谱不存在");
        }
        // 分类变更协议：null=不修改；0=改回未分类（归一为 null 落库）；>0=改到指定分类（校验存在）
        if (from.getCategoryId() != null) {
            Long categoryId = normalizeUncategorized(from.getCategoryId());
            checkCategoryExists(categoryId);
            recipe.setCategoryId(categoryId);
        }
        if (StringUtils.hasText(from.getName())) {
            recipe.setName(from.getName());
        }
        if (StringUtils.hasText(from.getSteps())) {
            recipe.setSteps(from.getSteps());
        }
        if (from.getIngredients() != null) {
            recipe.setIngredients(serializeIngredients(from.getIngredients()));
        }
        if (from.getCoverObjectKey() != null) {
            recipe.setCoverObjectKey(from.getCoverObjectKey());
        }
        // 营养成分（契约 §3.2）：null=不修改，对齐本接口全量字段语义
        if (from.getCaloriesPer100g() != null) {
            recipe.setCaloriesPer100g(from.getCaloriesPer100g());
        }
        if (from.getCaloriesTotal() != null) {
            recipe.setCaloriesTotal(from.getCaloriesTotal());
        }
        if (from.getProteinG() != null) {
            recipe.setProteinG(from.getProteinG());
        }
        if (from.getFatG() != null) {
            recipe.setFatG(from.getFatG());
        }
        if (from.getCarbsG() != null) {
            recipe.setCarbsG(from.getCarbsG());
        }
        if (from.getSodiumMg() != null) {
            recipe.setSodiumMg(from.getSodiumMg());
        }
        this.updateById(recipe);
        log.info("更新菜谱, id: {}, name: {}", recipe.getId(), recipe.getName());
    }

    @Override
    public void deleteRecipes(String ids) {
        log.info("删除菜谱, ids: {}", ids);
        for (String id : ids.split(",")) {
            this.removeById(parseId(id));
        }
        // 封面 MinIO 对象不随删除回收（孤儿对象量级小，可接受）——对齐 game/workbench 封面惯例
    }

    @Override
    public void changeStatus(Long id, Integer status) {
        // 合法值前置拦截：不接受 0/1 之外的取值穿透到库
        if (!Objects.equals(status, DiningRecipe.STATUS_ON_SHELF)
                && !Objects.equals(status, DiningRecipe.STATUS_OFF_SHELF)) {
            throw new CustomExceptions("status 仅支持 1=上架 0=下架");
        }
        DiningRecipe recipe = this.getById(id);
        if (recipe == null) {
            throw new CustomExceptions("菜谱不存在");
        }
        recipe.setStatus(status);
        this.updateById(recipe);
        log.info("菜谱上下架, id: {}, status: {}", id, status);
        // 下架语义：不再出现在上架查询（status=1 过滤）；历史点单订单按菜谱 ID 引用，不受影响
    }

    @Override
    public String uploadCover(MultipartFile file) {
        // 对齐既有上传惯例：JPG/PNG/WebP 白名单 + 魔数 + 扩展名一致性 + 5MB 上限（contentType 可伪造不采信）
        RecipeImageValidator.validate(file);
        try {
            String objectKey = fileStorageService.uploadFile(file, COVER_DIRECTORY);
            log.info("菜谱封面上传成功, objectKey: {}, 直链: {}", objectKey, minioConfig.urlOf(objectKey));
            return objectKey;
        } catch (CustomExceptions e) {
            throw e;
        } catch (Exception e) {
            log.error("菜谱封面上传失败", e);
            throw new CustomExceptions("封面上传失败，请稍后重试");
        }
    }


    @Override
    public Page<DiningRecipeVo> userPage(SearchWebDiningRecipeFrom from, Long userId) {
        // 用户端固定只查上架：status 不来自入参（SearchWebDiningRecipeFrom 无 status 字段），杜绝端上越权翻下架数据
        LambdaQueryWrapper<DiningRecipe> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DiningRecipe::getStatus, DiningRecipe.STATUS_ON_SHELF);
        wrapper.eq(from.getCategoryId() != null, DiningRecipe::getCategoryId, from.getCategoryId());
        wrapper.like(StringUtils.hasText(from.getKeyword()), DiningRecipe::getName, from.getKeyword());
        // 食材反查（契约 §2.4）：与 keyword/categoryId 组合为 AND；命中为空时短路为空结果
        applyIngredientFilter(from.getIngredient(), wrapper);
        // 与管理端列表同序：新菜谱在前
        wrapper.orderByDesc(DiningRecipe::getId);

        Page<DiningRecipe> page = this.page(new Page<>(from.getPage(), from.getSize()), wrapper);
        List<DiningRecipeVo> vos = page.getRecords().stream().map(this::buildVo).toList();
        fillCategoryNames(page.getRecords(), vos);
        fillFavorited(vos, userId);
        Page<DiningRecipeVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(vos);
        return voPage;
    }

    @Override
    public DiningRecipeVo userDetail(Long id, Long userId) {
        DiningRecipe recipe = this.getById(id);
        // 下架与不存在同语义拒绝：不向下架菜谱泄露名称/步骤等内容，也不向端上区分两种失败
        if (recipe == null || !Objects.equals(recipe.getStatus(), DiningRecipe.STATUS_ON_SHELF)) {
            throw new CustomExceptions("菜谱不存在或已下架");
        }
        DiningRecipeVo vo = buildVo(recipe);
        fillCategoryNames(List.of(recipe), List.of(vo));
        vo.setAllergens(aggregateAllergens(id));
        fillFavorited(List.of(vo), userId);
        return vo;
    }

    // ==================== 收藏（契约 §2.3） ====================

    @Override
    public boolean favorite(Long userId, Long recipeId) {
        // 存在性校验：不校验上下架——收藏是用户数据，不因目录下架而失效（详情仍按上架口径拒绝）
        Long count = this.baseMapper.selectCount(new LambdaQueryWrapper<DiningRecipe>()
                .eq(DiningRecipe::getId, recipeId));
        if (count == null || count == 0) {
            throw new CustomExceptions("菜谱不存在");
        }
        // 幂等：已收藏直接返回当前态，不重复插
        Long exists = favoriteMapper.selectCount(new LambdaQueryWrapper<DiningRecipeFavorite>()
                .eq(DiningRecipeFavorite::getUserId, userId)
                .eq(DiningRecipeFavorite::getRecipeId, recipeId));
        if (exists != null && exists > 0) {
            return true;
        }
        try {
            favoriteMapper.insert(new DiningRecipeFavorite().setUserId(userId).setRecipeId(recipeId));
        } catch (DuplicateKeyException e) {
            // 并发窗口撞 uk_user_recipe：先收藏者生效，本次按已收藏处理（幂等）
            log.info("收藏并发撞唯一键，按已收藏处理, userId: {}, recipeId: {}", userId, recipeId);
        }
        log.info("收藏菜谱, userId: {}, recipeId: {}", userId, recipeId);
        return true;
    }

    @Override
    public boolean unfavorite(Long userId, Long recipeId) {
        // 幂等：未收藏时删除 0 行，无异常
        favoriteMapper.delete(new LambdaQueryWrapper<DiningRecipeFavorite>()
                .eq(DiningRecipeFavorite::getUserId, userId)
                .eq(DiningRecipeFavorite::getRecipeId, recipeId));
        log.info("取消收藏, userId: {}, recipeId: {}", userId, recipeId);
        return false;
    }

    @Override
    public Page<DiningRecipeVo> favoritePage(Long userId, SearchWebDiningRecipeFrom from) {
        // 按收藏时间倒序（id 自增等价于时间序）：最新收藏的在前
        Page<DiningRecipeFavorite> favPage = favoriteMapper.selectPage(
                new Page<>(from.getPage(), from.getSize()),
                new LambdaQueryWrapper<DiningRecipeFavorite>()
                        .eq(DiningRecipeFavorite::getUserId, userId)
                        .orderByDesc(DiningRecipeFavorite::getId));
        List<Long> recipeIds = favPage.getRecords().stream()
                .map(DiningRecipeFavorite::getRecipeId).toList();
        Page<DiningRecipeVo> voPage = new Page<>(favPage.getCurrent(), favPage.getSize(), favPage.getTotal());
        if (recipeIds.isEmpty()) {
            voPage.setRecords(List.of());
            return voPage;
        }
        Map<Long, DiningRecipe> recipeById = this.baseMapper
                .selectList(new LambdaQueryWrapper<DiningRecipe>().in(DiningRecipe::getId, recipeIds)).stream()
                .collect(Collectors.toMap(DiningRecipe::getId, r -> r, (a, b) -> a));
        // 保持收藏顺序；收藏行指向的菜谱已被物理删除时跳过（无外键，孤儿收藏行不影响其他条目）
        List<DiningRecipe> ordered = recipeIds.stream()
                .map(recipeById::get).filter(Objects::nonNull).toList();
        List<DiningRecipeVo> vos = new ArrayList<>(ordered.size());
        for (DiningRecipe recipe : ordered) {
            DiningRecipeVo vo = buildVo(recipe);
            vo.setFavorited(true);
            vos.add(vo);
        }
        fillCategoryNames(ordered, vos);
        voPage.setRecords(vos);
        return voPage;
    }

    /**
     * 食材反查过滤（契约 §2.4）：先按配料名模糊取菜谱 ID 集合（join 关联表），再 IN 进主查询。
     * 命中为空时用 {@code 1 = 0} 短路，避免 {@code IN ()} 语法错误。
     */
    private void applyIngredientFilter(String ingredient, LambdaQueryWrapper<DiningRecipe> wrapper) {
        if (!StringUtils.hasText(ingredient)) {
            return;
        }
        List<Long> recipeIds = this.baseMapper.selectRecipeIdsByIngredientName(ingredient.trim());
        if (recipeIds == null || recipeIds.isEmpty()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.in(DiningRecipe::getId, recipeIds);
    }

    /**
     * 批量填充 favorited（契约 §2.3）：一次 {@code WHERE user_id=? AND recipe_id IN(...)} 查询，禁止 N+1。
     * userId 为空或列表为空时全部置 false（公开/管理端场景）。
     */
    private void fillFavorited(List<DiningRecipeVo> vos, Long userId) {
        if (userId == null || vos.isEmpty()) {
            vos.forEach(vo -> vo.setFavorited(false));
            return;
        }
        List<Long> recipeIds = vos.stream().map(DiningRecipeVo::getId).toList();
        Set<Long> favorited = favoriteMapper.selectList(new LambdaQueryWrapper<DiningRecipeFavorite>()
                        .eq(DiningRecipeFavorite::getUserId, userId)
                        .in(DiningRecipeFavorite::getRecipeId, recipeIds)).stream()
                .map(DiningRecipeFavorite::getRecipeId).collect(Collectors.toSet());
        vos.forEach(vo -> vo.setFavorited(favorited.contains(vo.getId())));
    }

    /**
     * 聚合该菜全部配料的过敏原去重集合（契约 §2.2）：关联表 → 配料 → 非空 allergen，
     * 按 {@link DiningIngredient#ALLERGEN_VALUES} 顺序稳定输出，空集返回空列表。
     */
    private List<String> aggregateAllergens(Long recipeId) {
        List<DiningRecipeIngredient> relations = recipeIngredientMapper.selectList(
                new LambdaQueryWrapper<DiningRecipeIngredient>()
                        .eq(DiningRecipeIngredient::getRecipeId, recipeId));
        if (relations.isEmpty()) {
            return List.of();
        }
        List<Long> ingredientIds = relations.stream()
                .map(DiningRecipeIngredient::getIngredientId).filter(Objects::nonNull).distinct().toList();
        if (ingredientIds.isEmpty()) {
            return List.of();
        }
        Set<String> allergens = ingredientMapper.selectBatchIds(ingredientIds).stream()
                .map(DiningIngredient::getAllergen).filter(StringUtils::hasText).collect(Collectors.toSet());
        // 按枚举定义顺序稳定输出（同菜多配料命中同一标签只出现一次）
        return DiningIngredient.ALLERGEN_VALUES.stream().filter(allergens::contains).toList();
    }

    @Override
    public void addUserRecipe(AddDiningRecipeFrom from, Long userId) {
        // 分类哨兵协议与管理端一致：null/0 均未分类、>0 校验存在（GLOSSARY 10.10）
        Long categoryId = normalizeUncategorized(from.getCategoryId());
        checkCategoryExists(categoryId);

        DiningRecipe recipe = new DiningRecipe();
        recipe.setName(from.getName());
        recipe.setCategoryId(categoryId);
        recipe.setSteps(from.getSteps());
        recipe.setIngredients(serializeIngredients(from.getIngredients()));
        // 封面对象键随用户端新增一并落库，避免仅上传成功而记录缺失导致封面静默丢失
        recipe.setCoverObjectKey(from.getCoverObjectKey());
        // 用户端新增：来源 USER、创建人取当前登录用户，直上架（无审核流，spec 决策）
        recipe.setSource(SOURCE_USER);
        recipe.setStatus(DiningRecipe.STATUS_ON_SHELF);
        recipe.setCreatedByUserId(userId);
        this.save(recipe);
        log.info("用户端新增菜谱, id: {}, name: {}, categoryId: {}, userId: {}",
                recipe.getId(), recipe.getName(), recipe.getCategoryId(), recipe.getCreatedByUserId());
    }


    /** 分类存在性校验（null=未分类，合法放行） */
    private void checkCategoryExists(Long categoryId) {
        if (categoryId == null) {
            return;
        }
        if (diningCategoryMapper.selectById(categoryId) == null) {
            throw new CustomExceptions("分类不存在");
        }
    }

    /** 未分类哨兵归一：0 → null（DB category_id 可空=未分类） */
    private Long normalizeUncategorized(Long categoryId) {
        return categoryId != null && categoryId == 0L ? null : categoryId;
    }

    /** 食材名数组 → JSON 文本（null/空列表按 null 落库，保持「未填写」语义） */
    private String serializeIngredients(List<String> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return null;
        }
        return JsonUtils.toJson(ingredients);
    }

    /** DB 中的 JSON 文本 → 食材名数组（null/解析失败按空列表，不阻断列表渲染） */
    private List<String> deserializeIngredients(String ingredientsJson) {
        if (!StringUtils.hasText(ingredientsJson)) {
            return Collections.emptyList();
        }
        try {
            List<String> list = JsonUtils.parseObject(ingredientsJson, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
            });
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            log.warn("菜谱食材 JSON 解析失败，按空列表返回, ingredients: {}", ingredientsJson, e);
            return Collections.emptyList();
        }
    }

    /** 当前登录管理员 ID（管理端写操作上下文必有管理员身份） */
    private Long currentAdminId() {
        LoginUser currentUser = (LoginUser) securityUtil.getCurrentUser();
        if (currentUser == null) {
            throw new CustomExceptions("未获取到当前管理员身份");
        }
        return currentUser.getUserId();
    }

    /** ids 逐个解析为 Long；controller 已用 @Pattern 兜住畸形入参，此处再做一层防御，避免 NumberFormatException 直达全局兜底 */
    private Long parseId(String id) {
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            throw new CustomExceptions("ids 必须为逗号分隔的数字");
        }
    }

    /** 实体 → Vo：拼封面直链、反序列化食材数组（分类名由批量联查统一回填）；favorited 默认 false，用户端再按实际覆盖 */
    private DiningRecipeVo buildVo(DiningRecipe recipe) {
        DiningRecipeVo vo = new DiningRecipeVo();
        BeanUtils.copyProperties(recipe, vo);
        vo.setIngredients(deserializeIngredients(recipe.getIngredients()));
        vo.setFavorited(false);
        vo.setCoverUrl(StringUtils.hasText(recipe.getCoverObjectKey())
                ? minioConfig.urlOf(recipe.getCoverObjectKey()) : null);
        return vo;
    }

    /** 批量联查分类名回填 Vo（一次 selectBatchIds，避免逐行 N+1） */
    private void fillCategoryNames(List<DiningRecipe> records, List<DiningRecipeVo> vos) {
        List<Long> categoryIds = records.stream()
                .map(DiningRecipe::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (categoryIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = diningCategoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(DiningCategory::getId, DiningCategory::getName));
        vos.forEach(vo -> {
            if (vo.getCategoryId() != null) {
                vo.setCategoryName(nameMap.get(vo.getCategoryId()));
            }
        });
    }
}
