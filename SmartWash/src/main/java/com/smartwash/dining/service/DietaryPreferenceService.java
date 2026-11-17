package com.smartwash.dining.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.dining.entity.DietaryPreference;
import com.smartwash.dining.from.DietaryPreferenceFrom;
import com.smartwash.dining.vo.DietaryPreferenceVo;

/**
 * 用户饮食偏好服务（契约 §2.1，功能 1+2 合并）。
 * 一人一行：无记录 = 未设置（GET 返回 null 字段，端上兜底默认值）。
 */
public interface DietaryPreferenceService extends IService<DietaryPreference> {

    /** 查询当前用户偏好（无记录返回 allergens 空列表 + 目标 null 的默认语义 VO） */
    DietaryPreferenceVo get(Long userId);

    /** 保存（upsert）：记录不存在则新建，存在则整行覆盖；字段 null=恢复默认 */
    void save(Long userId, DietaryPreferenceFrom from);
}
