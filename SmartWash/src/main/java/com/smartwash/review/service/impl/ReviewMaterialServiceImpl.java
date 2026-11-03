package com.smartwash.review.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.review.entity.StudyMaterial;
import com.smartwash.review.from.AddMaterialFrom;
import com.smartwash.review.mapper.StudyMaterialMapper;
import com.smartwash.review.service.IReviewMaterialService;
import com.smartwash.review.vo.MaterialVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 复习资料服务实现：创建 / 分页。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewMaterialServiceImpl extends ServiceImpl<StudyMaterialMapper, StudyMaterial>
        implements IReviewMaterialService {

    @Override
    public Long createMaterial(AddMaterialFrom from, Long userId) {
        StudyMaterial material = new StudyMaterial();
        material.setUserId(userId);
        material.setTitle(from.getTitle());
        material.setRawText(from.getRawText());
        save(material);
        log.info("创建复习资料成功, userId: {}, materialId: {}", userId, material.getId());
        return material.getId();
    }

    @Override
    public Page<MaterialVo> pageMaterials(Integer page, Integer size, Long userId) {
        Page<StudyMaterial> resultPage = new Page<>(page, size);
        LambdaQueryWrapper<StudyMaterial> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudyMaterial::getUserId, userId);
        wrapper.orderByDesc(StudyMaterial::getCreatedAt);

        Page<StudyMaterial> materialPage = page(resultPage, wrapper);
        Page<MaterialVo> voPage = new Page<>();
        BeanUtils.copyProperties(materialPage, voPage);

        List<MaterialVo> vos = new ArrayList<>();
        for (StudyMaterial material : materialPage.getRecords()) {
            MaterialVo vo = new MaterialVo();
            vo.setId(material.getId());
            vo.setTitle(material.getTitle());
            vo.setRawText(material.getRawText());
            vo.setCreatedAt(material.getCreatedAt());
            vos.add(vo);
        }
        voPage.setRecords(vos);
        return voPage;
    }
}
