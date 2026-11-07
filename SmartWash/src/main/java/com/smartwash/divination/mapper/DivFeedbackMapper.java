package com.smartwash.divination.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.divination.entity.DivFeedback;
import com.smartwash.divination.vo.DivFeedbackAggVo;
import org.apache.ibatis.annotations.Param;

/**
 * 解读反馈 Mapper。
 */
public interface DivFeedbackMapper extends BaseMapper<DivFeedback> {

    /**
     * 盘感统计：单用户反馈聚合（单行；verify = rating ≥ 4 记应验）。
     */
    DivFeedbackAggVo selectUserFeedbackAgg(@Param("userId") Long userId);
}
