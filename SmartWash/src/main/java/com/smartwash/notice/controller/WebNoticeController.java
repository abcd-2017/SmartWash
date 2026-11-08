package com.smartwash.notice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.notice.from.SearchNoticeFrom;
import com.smartwash.notice.service.NoticeService;
import com.smartwash.notice.vo.NoticeVo;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内信用户端控制器。
 * 路径前缀 /web/auth/notices，需 ROLE_USER（SecurityConfig 的 /web/auth/** 通配统一拦截）。
 * 全部接口以当前登录用户为主体：收件箱条目 isRead 按当前用户回填，标记已读只作用于本人已读状态。
 * 四端联动说明：本组为新增接口，不改动任何既有接口；Android/鸿蒙端后续接入（本次不消费）。
 */
@Tag(name = "站内信-用户端", description = "我的收件箱、未读数与已读标记接口")
@Slf4j
@RestController
@RequestMapping("/web/auth/notices")
@RequiredArgsConstructor
public class WebNoticeController {

    private final NoticeService noticeService;

    @Operation(summary = "我的收件箱", description = "全量公告分页（最新在前），每条带当前用户 isRead")
    @GetMapping
    public Result<Page<NoticeVo>> inbox(@Valid SearchNoticeFrom from) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(noticeService.pageInbox(from, user.getUserId()));
    }

    @Operation(summary = "未读数", description = "notice 总数 − 当前用户已读数（懒创建口径，两个 COUNT 相减）")
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(noticeService.countUnread(user.getUserId()));
    }

    @Operation(summary = "标记已读", description = "幂等：重复标记静默成功；并发撞唯一索引亦判幂等")
    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable("id") Long id) {
        LoginUser user = UserContextHolder.getUser();
        noticeService.markRead(id, user.getUserId());
        return Result.ok(null);
    }

    @Operation(summary = "全部已读", description = "逐条插入已读标记，重复键吞掉（幂等）；返回本次新增条数")
    @PostMapping("/read-all")
    public Result<Integer> readAll() {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(noticeService.readAll(user.getUserId()));
    }
}
