package com.smartwash.dining.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.dining.entity.DiningRecipeIngredient;
import com.smartwash.dining.mapper.DiningRecipeIngredientMapper;
import com.smartwash.dining.service.DiningRecipeIngredientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 菜谱配料关联服务实现（纯关联表薄壳：批量导入 saveBatch / 按菜谱删除直用 Mapper，
 * 无独立业务语义）。
 */
@Service
@RequiredArgsConstructor
public class DiningRecipeIngredientServiceImpl
        extends ServiceImpl<DiningRecipeIngredientMapper, DiningRecipeIngredient>
        implements DiningRecipeIngredientService {
}
