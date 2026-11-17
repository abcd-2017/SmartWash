package com.smartwash.dining.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.dining.entity.DiningIngredient;
import com.smartwash.dining.from.AddDiningIngredientFrom;
import com.smartwash.dining.from.SearchDiningIngredientFrom;
import com.smartwash.dining.from.UpdateDiningIngredientFrom;
import com.smartwash.dining.vo.DiningIngredientVo;

/**
 * 配料库服务：Web 管理后台对配料主数据（dining_ingredient）的增删改查。
 * 导入/建菜谱链路按名 upsert 自动注册（导入服务直连 Mapper），本服务供管理页人工维护。
 */
public interface DiningIngredientService extends IService<DiningIngredient> {

    /** 管理端：分页查询（keyword 配料名模糊 + category 精确，按 id 升序） */
    Page<DiningIngredientVo> adminPage(SearchDiningIngredientFrom from);

    /** 管理端：新增配料（name 唯一校验 + uk_name 兜底，重名 409 业务码；category 缺省 OTHER） */
    void addIngredient(AddDiningIngredientFrom from);

    /** 管理端：更新配料（name/category 不传即不修改；改名查重排除自身，重名 409） */
    void updateIngredient(Long id, UpdateDiningIngredientFrom from);

    /** 管理端：物理删除（被菜谱配料关联引用时拒绝删除，409 + 引用数量提示） */
    void deleteIngredient(Long id);
}
