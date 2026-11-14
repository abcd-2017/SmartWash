package com.smartwash.dining.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.config.MinioConfig;
import com.smartwash.dining.entity.DiningCategory;
import com.smartwash.dining.entity.DiningRecipe;
import com.smartwash.dining.from.AddDiningRecipeFrom;
import com.smartwash.dining.from.SearchDiningRecipeFrom;
import com.smartwash.dining.from.SearchWebDiningRecipeFrom;
import com.smartwash.dining.from.UpdateDiningRecipeFrom;
import com.smartwash.dining.mapper.DiningCategoryMapper;
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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /** 当前登录上下文 */
    @Autowired
    private SecurityUtil securityUtil;

    // ==================== 管理端 ====================

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

    // ==================== 用户端 ====================

    @Override
    public Page<DiningRecipeVo> userPage(SearchWebDiningRecipeFrom from) {
        // 用户端固定只查上架：status 不来自入参（SearchWebDiningRecipeFrom 无 status 字段），杜绝端上越权翻下架数据
        LambdaQueryWrapper<DiningRecipe> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DiningRecipe::getStatus, DiningRecipe.STATUS_ON_SHELF);
        wrapper.eq(from.getCategoryId() != null, DiningRecipe::getCategoryId, from.getCategoryId());
        wrapper.like(StringUtils.hasText(from.getKeyword()), DiningRecipe::getName, from.getKeyword());
        // 与管理端列表同序：新菜谱在前
        wrapper.orderByDesc(DiningRecipe::getId);

        Page<DiningRecipe> page = this.page(new Page<>(from.getPage(), from.getSize()), wrapper);
        List<DiningRecipeVo> vos = page.getRecords().stream().map(this::buildVo).toList();
        fillCategoryNames(page.getRecords(), vos);
        Page<DiningRecipeVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(vos);
        return voPage;
    }

    @Override
    public DiningRecipeVo userDetail(Long id) {
        DiningRecipe recipe = this.getById(id);
        // 下架与不存在同语义拒绝：不向下架菜谱泄露名称/步骤等内容，也不向端上区分两种失败
        if (recipe == null || !Objects.equals(recipe.getStatus(), DiningRecipe.STATUS_ON_SHELF)) {
            throw new CustomExceptions("菜谱不存在或已下架");
        }
        DiningRecipeVo vo = buildVo(recipe);
        fillCategoryNames(List.of(recipe), List.of(vo));
        return vo;
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

    // ==================== 内部方法 ====================

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

    /** 实体 → 管理端 Vo：拼封面直链、反序列化食材数组（分类名由批量联查统一回填） */
    private DiningRecipeVo buildVo(DiningRecipe recipe) {
        DiningRecipeVo vo = new DiningRecipeVo();
        BeanUtils.copyProperties(recipe, vo);
        vo.setIngredients(deserializeIngredients(recipe.getIngredients()));
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
