package com.smartwash.dining.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.dining.entity.DiningRecipeIngredient;

/**
 * 菜谱配料关联服务（dining_recipe_ingredient 纯关联表薄壳 IService）：
 * 批量导入等链路按菜谱批量重建配料关联（D23 性能加固：逐行 insert → saveBatch 分批）。
 * 无独立业务方法，写路径统一走本服务的批量能力。
 */
public interface DiningRecipeIngredientService extends IService<DiningRecipeIngredient> {
}
