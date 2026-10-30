package com.smartwash.review.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.review.from.AddCardFrom;
import com.smartwash.review.from.AddMaterialFrom;
import com.smartwash.review.from.AnswerFrom;
import com.smartwash.review.from.SearchCardFrom;
import com.smartwash.review.service.IReviewCardService;
import com.smartwash.review.service.IReviewMaterialService;
import com.smartwash.review.vo.MaterialVo;
import com.smartwash.review.vo.ReviewCardVo;
import com.smartwash.review.vo.ReviewQueueVo;
import com.smartwash.review.vo.ReviewStatsVo;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 记忆复习接力用户端控制器。
 * 路径前缀 /web/auth/review/**，需 ROLE_USER。
 * 评分调度在后端 FsrsScheduler 纯函数内完成，客户端只传 (cardId, rating)。
 */
@Tag(name = "记忆复习-用户端", description = "资料/卡片管理与 FSRS 复习调度接口")
@Slf4j
@RestController
@RequestMapping("/web/auth/review")
@RequiredArgsConstructor
public class ReviewWebController {

    private final IReviewMaterialService materialService;
    private final IReviewCardService cardService;

    @Operation(summary = "创建资料", description = "粘贴文本创建复习资料")
    @PostMapping("/materials")
    public Result<Long> createMaterial(@RequestBody @Valid AddMaterialFrom from) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(materialService.createMaterial(from, user.getUserId()));
    }

    @Operation(summary = "资料分页", description = "当前用户的资料列表（按创建时间倒序）")
    @GetMapping("/materials")
    public Result<Page<MaterialVo>> pageMaterials(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(materialService.pageMaterials(page, size, user.getUserId()));
    }

    @Operation(summary = "建卡", description = "在资料下手动创建问答卡（初始 state=Learning, step=0, due=now）")
    @PostMapping("/materials/{id}/cards")
    public Result<Long> addCard(
            @PathVariable("id") @Parameter(description = "资料ID") Long id,
            @RequestBody @Valid AddCardFrom from) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(cardService.addCard(id, from, user.getUserId()));
    }

    @Operation(summary = "卡片分页", description = "按资料分页查询当前用户的卡片")
    @GetMapping("/cards")
    public Result<Page<ReviewCardVo>> pageCards(@Valid SearchCardFrom from) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(cardService.pageCards(from.getMaterialId(), from.getPage(), from.getSize(),
                user.getUserId()));
    }

    @Operation(summary = "今日队列", description = "到期卡在前（state,due 升序，上限 50）+ 新卡注入队尾（每日配额 20）")
    @GetMapping("/queue")
    public Result<ReviewQueueVo> getQueue() {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(cardService.getQueue(user.getUserId()));
    }

    @Operation(summary = "复习评分", description = "四档评分（1=Again 2=Hard 3=Good 4=Easy），服务端 FSRS-6 重算并写日志")
    @PostMapping("/answer")
    public Result<ReviewCardVo> answer(@RequestBody @Valid AnswerFrom from) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(cardService.answer(from, user.getUserId()));
    }

    @Operation(summary = "复习统计", description = "近 30 天每日复习数 + 各评分占比")
    @GetMapping("/stats")
    public Result<ReviewStatsVo> stats() {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(cardService.stats(user.getUserId()));
    }
}
