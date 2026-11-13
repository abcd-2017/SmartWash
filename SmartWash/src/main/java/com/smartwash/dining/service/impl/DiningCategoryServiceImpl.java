package com.smartwash.dining.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.dining.entity.DiningCategory;
import com.smartwash.dining.entity.DiningRecipe;
import com.smartwash.dining.from.AddDiningCategoryFrom;
import com.smartwash.dining.from.SearchDiningCategoryFrom;
import com.smartwash.dining.from.UpdateDiningCategoryFrom;
import com.smartwash.dining.mapper.DiningCategoryMapper;
import com.smartwash.dining.mapper.DiningRecipeMapper;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.dining.service.DiningCategoryService;
import com.smartwash.dining.vo.DiningCategoryVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * 菜谱分类服务实现。
 * 分类名唯一性：服务层先查重给业务侧友好提示，并发窗口由 uk_name 唯一索引兜底裁决。
 * 删除引用校验：被菜谱（dining_recipe.category_id）引用的分类拒绝删除，避免菜谱悬空成「未分类」脏数据。
 */
@Slf4j
@Service
public class DiningCategoryServiceImpl extends ServiceImpl<DiningCategoryMapper, DiningCategory>
        implements DiningCategoryService {

    /** 菜谱 Mapper：删除分类前按 category_id 计数拦截（依赖 Mapper 而非 Service，避免服务间循环依赖） */
    @Autowired
    private DiningRecipeMapper diningRecipeMapper;

    @Override
    public Page<DiningCategoryVo> adminPage(SearchDiningCategoryFrom from) {
        LambdaQueryWrapper<DiningCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(from.getName()), DiningCategory::getName, from.getName());
        // 与用户端展示顺序一致（sort 升序、id 升序），后台所见即端上所得
        wrapper.orderByAsc(DiningCategory::getSort).orderByAsc(DiningCategory::getId);

        Page<DiningCategory> page = this.page(new Page<>(from.getPage(), from.getSize()), wrapper);
        Page<DiningCategoryVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::buildVo).toList());
        return voPage;
    }

    @Override
    public void addCategory(AddDiningCategoryFrom from) {
        // 先查重给业务侧友好提示；并发窗口由 uk_name 唯一索引最终裁决
        if (this.getByName(from.getName()) != null) {
            throw new CustomExceptions("分类名称已存在");
        }
        DiningCategory category = new DiningCategory();
        BeanUtils.copyProperties(from, category);
        try {
            this.save(category);
        } catch (DuplicateKeyException e) {
            throw new CustomExceptions("分类名称已存在");
        }
        log.info("新增菜谱分类, name: {}, sort: {}", category.getName(), category.getSort());
    }

    @Override
    public void updateCategory(UpdateDiningCategoryFrom from) {
        DiningCategory category = this.getById(from.getId());
        if (category == null) {
            throw new CustomExceptions("分类不存在");
        }
        // 仅覆盖传入字段（MyBatis-Plus updateById 跳过 null 列）
        if (StringUtils.hasText(from.getName())) {
            // 重名校验排除自身：名称未变时查重应命中自身记录，不得误判
            DiningCategory conflict = this.getByNameExcludeId(from.getName(), from.getId());
            if (conflict != null) {
                throw new CustomExceptions("分类名称已存在");
            }
            category.setName(from.getName());
        }
        if (from.getSort() != null) {
            category.setSort(from.getSort());
        }
        this.updateById(category);
        log.info("更新菜谱分类, id: {}, name: {}", category.getId(), category.getName());
    }

    @Override
    public void deleteCategories(String ids) {
        log.info("删除菜谱分类, ids: {}", ids);
        List<Long> categoryIds = Arrays.stream(ids.split(","))
                .map(this::parseId)
                .toList();
        // 引用校验前置：先对全部 id 逐一检查菜谱引用，任一冲突即整体拒绝（未触删除，无需事务兜底）
        for (Long categoryId : categoryIds) {
            Long refCount = diningRecipeMapper.selectCount(new LambdaQueryWrapper<DiningRecipe>()
                    .eq(DiningRecipe::getCategoryId, categoryId));
            if (refCount != null && refCount > 0) {
                DiningCategory category = this.getById(categoryId);
                String name = category != null ? category.getName() : String.valueOf(categoryId);
                throw new CustomExceptions("分类「" + name + "」已被 " + refCount + " 个菜谱引用，无法删除");
            }
        }
        categoryIds.forEach(this::removeById);
    }

    @Override
    public List<DiningCategoryVo> listAll() {
        // 用户端全量分类（量级小不分页），排序与 adminPage 一致——后台所见即端上所得
        return this.list(new LambdaQueryWrapper<DiningCategory>()
                .orderByAsc(DiningCategory::getSort)
                .orderByAsc(DiningCategory::getId))
                .stream()
                .map(this::buildVo)
                .toList();
    }

    /** ids 逐个解析为 Long；controller 已用 @Pattern 兜住畸形入参，此处再做一层防御，避免 NumberFormatException 直达全局兜底 */
    private Long parseId(String id) {
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            throw new CustomExceptions("ids 必须为逗号分隔的数字");
        }
    }

    private DiningCategory getByName(String name) {
        return this.getBaseMapper().selectOne(new LambdaQueryWrapper<DiningCategory>()
                .eq(DiningCategory::getName, name));
    }

    private DiningCategory getByNameExcludeId(String name, Long excludeId) {
        return this.getBaseMapper().selectOne(new LambdaQueryWrapper<DiningCategory>()
                .eq(DiningCategory::getName, name)
                .ne(DiningCategory::getId, excludeId));
    }

    private DiningCategoryVo buildVo(DiningCategory category) {
        DiningCategoryVo vo = new DiningCategoryVo();
        BeanUtils.copyProperties(category, vo);
        return vo;
    }
}
