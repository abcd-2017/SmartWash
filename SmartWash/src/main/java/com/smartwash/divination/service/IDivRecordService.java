package com.smartwash.divination.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.divination.entity.DivRecord;
import com.smartwash.divination.from.CreateRecordFrom;
import com.smartwash.divination.from.FeedbackFrom;
import com.smartwash.divination.from.SearchRecordFrom;
import com.smartwash.divination.vo.RecordDetailVo;
import com.smartwash.divination.vo.RecordVo;

/**
 * 卦例服务接口：创建/重算校验/卦历分页/详情/今日一签。
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
}
