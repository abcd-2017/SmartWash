package com.smartwash.notice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.notice.from.AddNoticeFrom;
import com.smartwash.notice.from.SearchNoticeFrom;
import com.smartwash.notice.service.NoticeService;
import com.smartwash.notice.vo.NoticeReadStatVo;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内信管理端控制器。
 * 路径前缀 /admin/notices，需 ROLE_ADMIN（SecurityConfig 的 /admin/** 通配统一拦截）。
 * 发送人 creatorAdminId 一律取登录上下文，不接受前端传入，防伪造发送人。
 * 四端联动说明：本组为新增接口，Web 管理后台为消费方；Android/鸿蒙端后续接入（本次不消费）。
 */
@Tag(name = "站内信-管理端", description = "运营通知发送、已发列表与送达统计（ROLE_ADMIN）")
@Slf4j
@RestController
@RequestMapping("/admin/notices")
@RequiredArgsConstructor
public class NoticeAdminController {

    private final NoticeService noticeService;

    @Operation(summary = "发送站内信", description = "title/content/type/deep_link；type 仅 SYSTEM/ANNOUNCEMENT，deep_link 可空（纯文案公告）")
    @PostMapping
    public Result<Long> publish(@RequestBody @Valid AddNoticeFrom from) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(noticeService.publish(from, user.getUserId()));
    }

    @Operation(summary = "已发公告分页", description = "title 模糊 + type 筛选，按创建时间倒序（最新在前）")
    @GetMapping
    public Result<Page<NoticeVo>> page(@Valid SearchNoticeFrom from) {
        return Result.ok(noticeService.adminPage(from));
    }

    @Operation(summary = "送达统计", description = "发送总数 = 全平台用户总数；已读数 = 该公告去重已读人数（唯一索引保证一人一条）")
    @GetMapping("/{id}/reads")
    public Result<NoticeReadStatVo> readStat(@PathVariable("id") Long id) {
        return Result.ok(noticeService.readStat(id));
    }
}
