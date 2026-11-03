package com.smartwash.review.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.review.entity.ReviewCard;
import com.smartwash.review.from.AddCardFrom;
import com.smartwash.review.from.AnswerFrom;
import com.smartwash.review.vo.ReviewCardVo;
import com.smartwash.review.vo.ReviewQueueVo;
import com.smartwash.review.vo.ReviewStatsVo;

/**
 * 复习卡服务：建卡 / 卡片分页 / 今日队列 / 评分调度 / 统计。
 */
public interface IReviewCardService extends IService<ReviewCard> {

    /** 在资料下手动建卡（初始 state=Learning, step=0, due=now），返回卡片 id */
    Long addCard(Long materialId, AddCardFrom from, Long userId);

    /** 按资料分页查询当前用户的卡片 */
    Page<ReviewCardVo> pageCards(Long materialId, Integer page, Integer size, Long userId);

    /** 今日队列：到期卡（state,due 升序，上限 50）+ 新卡注入队尾（每日配额 20） */
    ReviewQueueVo getQueue(Long userId);

    /** 评分复习：FsrsScheduler 纯函数重算，事务内写日志 + 更新卡，返回更新后的卡片 */
    ReviewCardVo answer(AnswerFrom from, Long userId);

    /** 近 30 天每日复习数 + 各评分占比 */
    ReviewStatsVo stats(Long userId);
}
