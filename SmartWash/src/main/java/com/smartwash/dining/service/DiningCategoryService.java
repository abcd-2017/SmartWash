package com.smartwash.dining.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.dining.entity.DiningCategory;
import com.smartwash.dining.from.AddDiningCategoryFrom;
import com.smartwash.dining.from.SearchDiningCategoryFrom;
import com.smartwash.dining.from.UpdateDiningCategoryFrom;
import com.smartwash.dining.vo.DiningCategoryVo;

import java.util.List;

/**
 * 菜谱分类服务：Web 管理后台对平台公共菜谱库分类目录的增删改查。
 */
public interface DiningCategoryService extends IService<DiningCategory> {

    /** 管理端：分页查询（name 模糊，按 sort/id 升序，与用户端展示顺序一致） */
    Page<DiningCategoryVo> adminPage(SearchDiningCategoryFrom from);

    /** 管理端：新增分类（name 重复校验 + 唯一索引兜底裁决） */
    void addCategory(AddDiningCategoryFrom from);

    /** 管理端：更新分类（name 传入时与其他分类查重排除自身；字段 null=不修改） */
    void updateCategory(UpdateDiningCategoryFrom from);

    /** 管理端：批量物理删除（ids 逗号分隔；任一分类被菜谱引用时整体拒绝删除并给明确提示） */
    void deleteCategories(String ids);

    /** 用户端：全部分类（sort 升序、id 升序，与管理端列表顺序一致），供菜谱库分类筛选 */
    List<DiningCategoryVo> listAll();
}
