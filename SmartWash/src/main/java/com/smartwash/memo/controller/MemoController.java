package com.smartwash.memo.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.memo.from.AddMemoFrom;
import com.smartwash.memo.from.SearchMemoFrom;
import com.smartwash.memo.from.UpdateMemoFrom;
import com.smartwash.memo.service.MemoService;
import com.smartwash.memo.vo.MemoVo;
import com.smartwash.memo.vo.TagStatVo;
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

import java.util.List;

/**
 * 速记手账用户端控制器。
 * 路径前缀 /web/auth/memo，需 ROLE_USER（SecurityConfig 既有规则覆盖，无需单独配置）；
 * 所有读写均以当前登录用户为主体，归属校验由服务层条件 UPDATE 谓词裁决。
 */
@Tag(name = "速记手账", description = "随手记新增/分页/更新/逻辑删除/标签聚合接口")
@Slf4j
@RestController
@RequestMapping("/web/auth/memo")
@RequiredArgsConstructor
public class MemoController {

    private final MemoService memoService;

    @Operation(summary = "新增速记", description = "content 必填且 ≤2000 字符；tags 可选，服务端 trim/去空/去重后逗号分隔落库；返回新记录 id")
    @PostMapping
    public Result<Long> create(@RequestBody @Valid AddMemoFrom from) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(memoService.create(from, user.getUserId()));
    }

    @Operation(summary = "分页查询速记", description = "pinned 优先，其余按更新时间倒序；keyword 对 content 模糊匹配，tag 精确过滤（匹配逗号分隔标签中的完整一项）")
    @GetMapping("/list")
    public Result<Page<MemoVo>> list(SearchMemoFrom from) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(memoService.list(from, user.getUserId()));
    }

    @Operation(summary = "更新速记", description = "patch 语义：content/tags/pinned 传了才更新；校验归属（非本人/已删除/不存在统一 404 语义）")
    @PutMapping("/{id}")
    public Result<String> update(@PathVariable("id") Long id, @RequestBody @Valid UpdateMemoFrom from) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(memoService.update(id, from, user.getUserId()));
    }

    @Operation(summary = "删除速记", description = "逻辑删除（is_delete 置 1），物理数据保留；校验归属")
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable("id") Long id) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(memoService.delete(id, user.getUserId()));
    }

    @Operation(summary = "标签聚合统计", description = "当前用户的标签计数，按 count 倒序；同频按标签字典序保证稳定")
    @GetMapping("/tags")
    public Result<List<TagStatVo>> tags() {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(memoService.tagStats(user.getUserId()));
    }
}
