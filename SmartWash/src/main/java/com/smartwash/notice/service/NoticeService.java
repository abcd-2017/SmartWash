package com.smartwash.notice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.notice.entity.Notice;
import com.smartwash.notice.from.AddNoticeFrom;
import com.smartwash.notice.from.SearchNoticeFrom;
import com.smartwash.notice.vo.NoticeReadStatVo;
import com.smartwash.notice.vo.NoticeVo;

/**
 * 站内信服务（v1：运营通知 + 用户收件箱）。
 * 已读采用懒创建（notice_read 首次标记才插入），未读数 = notice 总数 − 当前用户已读数。
 */
public interface NoticeService extends IService<Notice> {

    // ==================== 管理端 ====================

    /** 发送站内信（creatorAdminId 取登录上下文） */
    Long publish(AddNoticeFrom from, Long creatorAdminId);

    /** 管理端分页列表（title 模糊 + type 筛选，按创建时间倒序） */
    Page<NoticeVo> adminPage(SearchNoticeFrom from);

    /** 送达统计（发送总数 = 用户总数，已读数按 notice 过滤） */
    NoticeReadStatVo readStat(Long noticeId);

    // ==================== 用户端 ====================

    /** 我的收件箱分页（每条带当前用户 is_read，按创建时间倒序） */
    Page<NoticeVo> pageInbox(SearchNoticeFrom from, Long userId);

    /** 未读数（notice 总数 − 当前用户已读数） */
    long countUnread(Long userId);

    /** 标记已读（幂等：已读重复标记静默成功，并发撞唯一索引亦判幂等） */
    void markRead(Long noticeId, Long userId);

    /** 全部已读（逐条插入，重复键吞掉；返回本次新增条数） */
    int readAll(Long userId);
}
