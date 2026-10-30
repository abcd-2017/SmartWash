package com.smartwash.review.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.review.entity.StudyMaterial;
import com.smartwash.review.from.AddMaterialFrom;
import com.smartwash.review.vo.MaterialVo;

/**
 * 复习资料服务：建资料 / 分页列表。
 */
public interface IReviewMaterialService extends IService<StudyMaterial> {

    /** 创建资料，返回资料 id */
    Long createMaterial(AddMaterialFrom from, Long userId);

    /** 当前用户的资料分页（按创建时间倒序） */
    Page<MaterialVo> pageMaterials(Integer page, Integer size, Long userId);
}
