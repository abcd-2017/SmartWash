package com.smartwash.divination.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.divination.entity.DivFeedbackNudge;
import com.smartwash.divination.mapper.DivFeedbackNudgeMapper;
import com.smartwash.divination.vo.DivNudgeVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 应验回访提醒服务（占卜闭环，纯新增，不触碰既有解读/反馈逻辑）。
 *
 * 生命周期：定时任务扫描候选 → status=0 待回访；
 * 用户提交反馈 → status=1 已回访（由 DivRecordServiceImpl.addFeedback 末尾接线调用）；
 * 端上主动忽略 → status=2 已忽略（条件 UPDATE 校验归属与待处理态）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DivNudgeService {

    /** 待回访 */
    public static final int STATUS_PENDING = 0;
    /** 已回访 */
    public static final int STATUS_VISITED = 1;
    /** 已忽略 */
    public static final int STATUS_IGNORED = 2;
    /** 正常（未删除） */
    public static final int NOT_DELETED = 0;

    private final DivFeedbackNudgeMapper nudgeMapper;

    /**
     * 分页查询当前用户的待回访提醒（status=0，按 nudge_date 倒序）。
     */
    public Page<DivNudgeVo> pageNudges(Integer page, Integer size, Long userId) {
        Page<DivNudgeVo> p = new Page<>(page, size);
        return nudgeMapper.selectNudgePage(p, userId);
    }

    /**
     * 忽略提醒：条件 UPDATE（id + 归属用户 + 仍待回访），返回是否命中。
     */
    public boolean ignoreNudge(Long id, Long userId) {
        LambdaUpdateWrapper<DivFeedbackNudge> uw = new LambdaUpdateWrapper<>();
        uw.eq(DivFeedbackNudge::getId, id)
                .eq(DivFeedbackNudge::getUserId, userId)
                .eq(DivFeedbackNudge::getStatus, STATUS_PENDING)
                .set(DivFeedbackNudge::getStatus, STATUS_IGNORED);
        return nudgeMapper.update(null, uw) > 0;
    }

    /**
     * 当前用户待回访提醒数量（端上徽标）。
     */
    public long countPending(Long userId) {
        LambdaQueryWrapper<DivFeedbackNudge> qw = new LambdaQueryWrapper<>();
        qw.eq(DivFeedbackNudge::getUserId, userId)
                .eq(DivFeedbackNudge::getStatus, STATUS_PENDING)
                .eq(DivFeedbackNudge::getIsDelete, NOT_DELETED);
        return nudgeMapper.selectCount(qw);
    }

    /**
     * 闭环接线：将同解读的待回访提醒置为已回访（条件 UPDATE，幂等）。
     * 返回受影响行数（0 = 无待回访提醒，属正常路径）。
     */
    public int closePendingNudges(Long interpretationId, Long userId) {
        LambdaUpdateWrapper<DivFeedbackNudge> uw = new LambdaUpdateWrapper<>();
        uw.eq(DivFeedbackNudge::getInterpretationId, interpretationId)
                .eq(DivFeedbackNudge::getUserId, userId)
                .eq(DivFeedbackNudge::getStatus, STATUS_PENDING)
                .set(DivFeedbackNudge::getStatus, STATUS_VISITED);
        return nudgeMapper.update(null, uw);
    }

    /**
     * 扫描回访候选并落库：解读完成 ≥3 天（created_at ≤ now-3天）、该解读无反馈、且无既有提醒。
     * nudge_date 由扫描 SQL 按「解读完成日 + 3 天」给出；uk_interpretation + 并发捕获兜底防重复。
     *
     * @return 新增提醒条数
     */
    public int createNudgesForReadyInterpretations(LocalDateTime now) {
        List<DivFeedbackNudge> candidates = nudgeMapper.selectNudgeCandidates(now.minusDays(3));
        if (candidates.isEmpty()) {
            return 0;
        }
        int created = 0;
        for (DivFeedbackNudge candidate : candidates) {
            DivFeedbackNudge nudge = new DivFeedbackNudge();
            nudge.setUserId(candidate.getUserId());
            nudge.setRecordId(candidate.getRecordId());
            nudge.setInterpretationId(candidate.getInterpretationId());
            nudge.setNudgeDate(candidate.getNudgeDate());
            nudge.setStatus(STATUS_PENDING);
            nudge.setIsDelete(NOT_DELETED);
            try {
                nudgeMapper.insert(nudge);
                created++;
            } catch (DuplicateKeyException e) {
                // uk_interpretation 并发兜底：扫描与插入间隙被并发任务抢先插入，跳过即可
                log.debug("回访提醒已存在，跳过, interpretationId: {}", candidate.getInterpretationId());
            }
        }
        log.info("回访提醒扫描完成, 候选: {}, 新增: {}", candidates.size(), created);
        return created;
    }
}
