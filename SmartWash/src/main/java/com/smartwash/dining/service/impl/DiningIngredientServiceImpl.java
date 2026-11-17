package com.smartwash.dining.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.dining.entity.DiningIngredient;
import com.smartwash.dining.entity.DiningRecipeIngredient;
import com.smartwash.dining.from.AddDiningIngredientFrom;
import com.smartwash.dining.from.SearchDiningIngredientFrom;
import com.smartwash.dining.from.UpdateDiningIngredientFrom;
import com.smartwash.dining.mapper.DiningIngredientMapper;
import com.smartwash.dining.mapper.DiningRecipeIngredientMapper;
import com.smartwash.dining.service.DiningIngredientService;
import com.smartwash.dining.vo.DiningIngredientVo;
import com.smartwash.exception.CustomExceptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 配料库服务实现。
 * 名称唯一性：服务层先查重给业务侧友好提示（409），并发窗口由 uk_name 唯一索引兜底裁决。
 * 删除引用校验：被菜谱配料关联（dining_recipe_ingredient）引用的配料拒绝删除——
 * 关联表是买菜清单聚合主数据源，删配料会让菜谱悬空丢明细（对齐分类删除引用校验先例）。
 */
@Slf4j
@Service
public class DiningIngredientServiceImpl extends ServiceImpl<DiningIngredientMapper, DiningIngredient>
        implements DiningIngredientService {

    /** 配料分类缺省值（启发规则未命中/管理端未指定时的兜底分类） */
    private static final String DEFAULT_CATEGORY = DiningIngredient.CATEGORY_OTHER;

    /** 菜谱配料关联 Mapper：删除配料前按 ingredient_id 计数拦截（依赖 Mapper 而非 Service，避免服务间循环依赖） */
    @Autowired
    private DiningRecipeIngredientMapper recipeIngredientMapper;

    @Override
    public Page<DiningIngredientVo> adminPage(SearchDiningIngredientFrom from) {
        LambdaQueryWrapper<DiningIngredient> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(from.getKeyword()), DiningIngredient::getName, from.getKeyword());
        wrapper.eq(StringUtils.hasText(from.getCategory()), DiningIngredient::getCategory, from.getCategory());
        // 配料库按字典册风格 id 升序：与导入注册顺序一致，翻页位置稳定
        wrapper.orderByAsc(DiningIngredient::getId);

        Page<DiningIngredient> page = this.page(new Page<>(from.getPage(), from.getSize()), wrapper);
        Page<DiningIngredientVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::buildVo).toList());
        return voPage;
    }

    @Override
    public void addIngredient(AddDiningIngredientFrom from) {
        // 先查重给业务侧友好提示；并发窗口由 uk_name 唯一索引最终裁决
        if (this.getByName(from.getName()) != null) {
            throw new CustomExceptions(409, "配料名称已存在");
        }
        DiningIngredient ingredient = new DiningIngredient()
                .setName(from.getName())
                .setCategory(StringUtils.hasText(from.getCategory()) ? from.getCategory() : DEFAULT_CATEGORY);
        try {
            this.save(ingredient);
        } catch (DuplicateKeyException e) {
            throw new CustomExceptions(409, "配料名称已存在");
        }
        log.info("新增配料, name: {}, category: {}", ingredient.getName(), ingredient.getCategory());
    }

    @Override
    public void updateIngredient(Long id, UpdateDiningIngredientFrom from) {
        DiningIngredient ingredient = this.getById(id);
        if (ingredient == null) {
            throw new CustomExceptions("配料不存在");
        }
        // 仅覆盖传入字段（MyBatis-Plus updateById 跳过 null 列）
        if (StringUtils.hasText(from.getName())) {
            // 重名校验排除自身：名称未变时查重应命中自身记录，不得误判
            DiningIngredient conflict = this.getByNameExcludeId(from.getName(), id);
            if (conflict != null) {
                throw new CustomExceptions(409, "配料名称已存在");
            }
            ingredient.setName(from.getName());
        }
        if (StringUtils.hasText(from.getCategory())) {
            ingredient.setCategory(from.getCategory());
        }
        this.updateById(ingredient);
        log.info("更新配料, id: {}, name: {}, category: {}", ingredient.getId(),
                ingredient.getName(), ingredient.getCategory());
    }

    @Override
    public void deleteIngredient(Long id) {
        DiningIngredient ingredient = this.getById(id);
        if (ingredient == null) {
            throw new CustomExceptions("配料不存在");
        }
        // 引用校验前置：被菜谱配料关联引用时拒绝删除（买菜清单聚合主数据源，删配料会让菜谱悬空丢明细）
        Long refCount = recipeIngredientMapper.selectCount(new LambdaQueryWrapper<DiningRecipeIngredient>()
                .eq(DiningRecipeIngredient::getIngredientId, id));
        if (refCount != null && refCount > 0) {
            throw new CustomExceptions(409, "配料「" + ingredient.getName() + "」已被 "
                    + refCount + " 个菜谱引用，无法删除");
        }
        this.removeById(id);
        log.info("删除配料, id: {}, name: {}", id, ingredient.getName());
    }

    private DiningIngredient getByName(String name) {
        return this.getBaseMapper().selectOne(new LambdaQueryWrapper<DiningIngredient>()
                .eq(DiningIngredient::getName, name));
    }

    private DiningIngredient getByNameExcludeId(String name, Long excludeId) {
        return this.getBaseMapper().selectOne(new LambdaQueryWrapper<DiningIngredient>()
                .eq(DiningIngredient::getName, name)
                .ne(DiningIngredient::getId, excludeId));
    }

    private DiningIngredientVo buildVo(DiningIngredient ingredient) {
        DiningIngredientVo vo = new DiningIngredientVo();
        BeanUtils.copyProperties(ingredient, vo);
        return vo;
    }
}
