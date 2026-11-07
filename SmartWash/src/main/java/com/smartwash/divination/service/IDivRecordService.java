package com.smartwash.divination.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.divination.entity.DivRecord;
import com.smartwash.divination.from.CreateRecordFrom;
import com.smartwash.divination.from.DivRecordSearchFrom;
import com.smartwash.divination.from.FeedbackFrom;
import com.smartwash.divination.from.SearchRecordFrom;
import com.smartwash.divination.vo.DivNudgeVo;
import com.smartwash.divination.vo.DivRecordSearchVo;
import com.smartwash.divination.vo.DivStatsVo;
import com.smartwash.divination.vo.RecordDetailVo;
import com.smartwash.divination.vo.RecordVo;

/**
 * 卦例服务接口：创建/重算校验/卦历分页/详情/今日一签。
 * 占卜闭环新增：解读历史检索 / 应验回访提醒 / 盘感统计（均为追加方法，既有方法不动）。
 */
public interface IDivRecordService {

    /**
     * 创建卦例（服务端 core 重算校验）。
     *
     * @param from      请求 DTO
     * @param userId    当前用户 ID
     * @return 创建的卦例 ID
     */
    Long createRecord(CreateRecordFrom from, Long userId);

    /**
     * 卦历分页查询。
     */
    Page<RecordVo> searchRecords(SearchRecordFrom from, Long userId);

    /**
     * 卦例详情（含最近一次解读）。
     */
    RecordDetailVo getRecordDetail(Long id, Long userId);

    /**
     * 今日一签：服务端按当日时间卦生成/复用卦例（method=meihua, source=today）。
     */
    RecordDetailVo getTodayRecord(Long userId);

    /**
     * 反馈 + 应验回填。
     */
    void addFeedback(Long recordId, FeedbackFrom from, Long userId);

    // ==================== 以下为占卜闭环新增方法（只增不改） ====================

    /**
     * 解读历史检索：keyword 匹配卦例问题或该卦最新一条解读正文；keyword 为空时返回空页。
     */
    Page<DivRecordSearchVo> searchRecordsByKeyword(DivRecordSearchFrom from, Long userId);

    /**
     * 当前用户待回访提醒分页（status=0，按 nudge_date 倒序）。
     */
    Page<DivNudgeVo> pageNudges(Integer page, Integer size, Long userId);

    /**
     * 忽略待回访提醒（归属校验 + 条件 UPDATE），返回是否成功。
     */
    boolean ignoreNudge(Long id, Long userId);

    /**
     * 当前用户待回访提醒数量（端上徽标）。
     */
    Integer countPendingNudges(Long userId);

    /**
     * 盘感统计（口径：verify = 反馈 rating ≥ 4 记应验）。
     */
    DivStatsVo getDivStats(Long userId);
}
