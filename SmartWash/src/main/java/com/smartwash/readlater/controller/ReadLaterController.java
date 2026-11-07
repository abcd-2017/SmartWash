package com.smartwash.readlater.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smartwash.common.Result;
import com.smartwash.readlater.from.AddReadLaterFrom;
import com.smartwash.readlater.from.SearchReadLaterFrom;
import com.smartwash.readlater.from.UpdateReadLaterFrom;
import com.smartwash.readlater.service.ReadLaterService;
import com.smartwash.readlater.vo.ReadLaterVo;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 稍后读（AI 摘要）用户端控制器。
 * 路径前缀 /web/auth/readlater，需 ROLE_USER。
 */
@Tag(name = "稍后读", description = "收藏链接 / AI 摘要 / 标签已读管理 / 短链分享")
@Slf4j
@RestController
@RequestMapping("/web/auth/readlater")
@RequiredArgsConstructor
public class ReadLaterController {

    private final ReadLaterService readLaterService;

    @Operation(summary = "新增收藏", description = "url 必填且仅 https；后端抓取页面标题（失败以 url 兜底），返回记录 id")
    @PostMapping
    public Result<Long> add(@RequestBody @Valid AddReadLaterFrom from) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(readLaterService.add(from, user.getUserId()));
    }

    @Operation(summary = "我的收藏分页", description = "支持 status/tag/keyword（模糊匹配 url+title+digest）筛选；未读优先、时间倒序")
    @GetMapping("/list")
    public Result<IPage<ReadLaterVo>> list(@Valid SearchReadLaterFrom from) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(readLaterService.list(from, user.getUserId()));
    }

    @Operation(summary = "生成 AI 摘要", description = "用页面标题调平台默认模型生成 ≤150 字摘要，落库并返回；平台未配置模型时返回业务失败")
    @PostMapping("/{id}/digest")
    public Result<String> digest(@PathVariable("id") Long id) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(readLaterService.generateDigest(id, user.getUserId()));
    }

    @Operation(summary = "更新收藏", description = "patch 语义：tags 整体替换 / status 已读切换，传哪个改哪个")
    @PutMapping("/{id}")
    public Result<String> update(@PathVariable("id") Long id, @RequestBody @Valid UpdateReadLaterFrom from) {
        LoginUser user = UserContextHolder.getUser();
        readLaterService.update(id, from, user.getUserId());
        return Result.ok("更新成功");
    }

    @Operation(summary = "删除收藏", description = "逻辑删除（is_delete 置 1），仅本人记录可删")
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable("id") Long id) {
        LoginUser user = UserContextHolder.getUser();
        readLaterService.delete(id, user.getUserId());
        return Result.ok("删除成功");
    }

    @Operation(summary = "一键转短链", description = "为收藏 url 创建短码（contentType=1 短链），返回完整短链地址")
    @PostMapping("/{id}/shorten")
    public Result<String> shorten(@PathVariable("id") Long id) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(readLaterService.shorten(id, user.getUserId()));
    }
}
