package com.smartwash.dining.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.smartwash.dining.entity.DietaryPreference;
import com.smartwash.dining.from.DietaryPreferenceFrom;
import com.smartwash.dining.mapper.DietaryPreferenceMapper;
import com.smartwash.dining.service.DietaryPreferenceService;
import com.smartwash.dining.vo.DietaryPreferenceVo;
import com.smartwash.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户饮食偏好服务实现（契约 §2.1）。
 * 一人一行 upsert：无记录新建；有记录整行覆盖。
 * 字段 null = 默认值语义（allergens null=无忌口，目标 null=恢复默认），
 * 由实体 {@code @TableField(updateStrategy = IGNORED)} 保证 null 能写回库（全局 update-strategy=not_empty 会跳过 null）。
 */
@Slf4j
@Service
public class DietaryPreferenceServiceImpl
        extends ServiceImpl<DietaryPreferenceMapper, DietaryPreference>
        implements DietaryPreferenceService {

    @Override
    public DietaryPreferenceVo get(Long userId) {
        DietaryPreference entity = this.getById(userId);
        if (entity == null) {
            // 无记录 = 未设置：返回默认语义 VO（allergens 空列表、目标 null），端上兜底 2000/2000
            return new DietaryPreferenceVo().setAllergens(List.of());
        }
        return new DietaryPreferenceVo()
                .setAllergens(parseAllergens(entity.getAllergens()))
                .setCalorieTarget(entity.getCalorieTarget())
                .setSodiumTarget(entity.getSodiumTarget());
    }

    @Override
    public void save(Long userId, DietaryPreferenceFrom from) {
        String allergensJson = serializeAllergens(from.getAllergens());
        DietaryPreference existing = this.getById(userId);
        if (existing == null) {
            this.save(new DietaryPreference()
                    .setUserId(userId)
                    .setAllergens(allergensJson)
                    .setCalorieTarget(from.getCalorieTarget())
                    .setSodiumTarget(from.getSodiumTarget()));
            log.info("新建用户饮食偏好, userId: {}", userId);
        } else {
            // 整行覆盖（含 null 恢复默认）：实体只带 userId + 三字段，updateById 按主键回写
            this.updateById(new DietaryPreference()
                    .setUserId(userId)
                    .setAllergens(allergensJson)
                    .setCalorieTarget(from.getCalorieTarget())
                    .setSodiumTarget(from.getSodiumTarget()));
            log.info("更新用户饮食偏好, userId: {}", userId);
        }
    }

    @Override
    public Map<Long, List<String>> getAllergensByUserIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<DietaryPreference> list = this.list(new LambdaQueryWrapper<DietaryPreference>()
                .in(DietaryPreference::getUserId, userIds));
        return list.stream().collect(Collectors.toMap(
                DietaryPreference::getUserId,
                preference -> parseAllergens(preference.getAllergens()),
                (a, b) -> a));
    }

    /** 忌口标签 → JSON 文本（null/空按 null 落库，保持「无忌口」语义；去重保序） */
    private String serializeAllergens(List<String> allergens) {
        if (allergens == null || allergens.isEmpty()) {
            return null;
        }
        List<String> distinct = allergens.stream().filter(StringUtils::hasText).distinct().toList();
        return distinct.isEmpty() ? null : JsonUtils.toJson(distinct);
    }

    /** JSON 文本 → 忌口标签（null/解析失败按空列表，不阻断设置页渲染） */
    private List<String> parseAllergens(String allergensJson) {
        if (!StringUtils.hasText(allergensJson)) {
            return List.of();
        }
        try {
            List<String> list = JsonUtils.parseObject(allergensJson, new TypeReference<List<String>>() {
            });
            return list != null ? list : List.of();
        } catch (Exception e) {
            log.warn("饮食偏好忌口 JSON 解析失败，按空列表返回, allergens: {}", allergensJson, e);
            return List.of();
        }
    }
}
