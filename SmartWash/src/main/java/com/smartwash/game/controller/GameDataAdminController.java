package com.smartwash.game.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.common.audit.Audited;
import com.smartwash.game.entity.GameLevel;
import com.smartwash.game.from.GameLevelBatchFrom;
import com.smartwash.game.from.GameLevelSaveFrom;
import com.smartwash.game.from.GameSaveSearchFrom;
import com.smartwash.game.from.GameScoreSearchFrom;
import com.smartwash.game.service.GameLevelService;
import com.smartwash.game.service.GameScoreService;
import com.smartwash.game.service.GameUserSaveService;
import com.smartwash.game.vo.GameLevelImportResultVo;
import com.smartwash.game.vo.GameSaveAdminVo;
import com.smartwash.game.vo.GameScoreAdminVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 游戏数据管理端控制器（关卡 / 存档 / 成绩查看）。
 * 路径前缀 /admin/game，需 ROLE_ADMIN（SecurityConfig 统一拦截）。
 *
 * <p>关卡管理让运营不重传游戏包即可扩展关卡；存档/成绩查看支撑客诉与数据分析。
 * 本组为新增接口，Web 管理后台为消费方。</p>
 */
@Tag(name = "游戏中心-数据管理", description = "关卡管理/存档查看/成绩查看（ROLE_ADMIN）")
@Slf4j
@RestController
@RequestMapping("/admin/game")
@RequiredArgsConstructor
public class GameDataAdminController {

    private final GameLevelService gameLevelService;
    private final GameUserSaveService gameUserSaveService;
    private final GameScoreService gameScoreService;

    // ==================== 关卡 ====================

    @Operation(summary = "关卡列表", description = "按 gameKey 返回全部关卡（含未启用），按 sort / levelNo 排序")
    @GetMapping("/levels")
    public Result<List<GameLevel>> listLevels(@RequestParam("gameKey") String gameKey) {
        return Result.ok(gameLevelService.listByGame(gameKey));
    }

    @Operation(summary = "保存关卡（新建/更新）", description = "按 (gameKey, levelNo) upsert；dataJson 仅校验 JSON 合法性，结构由游戏自定；保存后版本号递增")
    @Audited(action = "GAME_LEVEL_SAVE", targetType = "game_level", targetIdSpEL = "#from.gameKey + ':' + #from.levelNo")
    @PostMapping("/levels")
    public Result<GameLevel> saveLevel(@RequestBody @Valid GameLevelSaveFrom from) {
        GameLevel level = gameLevelService.upsertLevel(from.getGameKey(), from.getLevelNo(), from.getName(),
                from.getDataJson(), from.getDifficulty(), from.getEnabled(), from.getSort(), from.getRemark());
        return Result.ok(level);
    }

    @Operation(summary = "批量导入关卡", description = "整批事务：任一项校验失败（序号/JSON/批内重复/上限 500）全量回滚不落库；replaceAll=true 先清空该游戏现有关卡再导入；成功后 levels_version 仅递增一次。双兼容 JSON 文件由前端解析归一后提交")
    @Audited(action = "GAME_LEVEL_BATCH_IMPORT", targetType = "game_level", targetIdSpEL = "#from.gameKey")
    @PostMapping("/levels/batch")
    public Result<GameLevelImportResultVo> batchImportLevels(@RequestBody @Valid GameLevelBatchFrom from) {
        return Result.ok(gameLevelService.batchImportLevels(from.getGameKey(), from.isReplaceAll(), from.getLevels()));
    }

    @Operation(summary = "删除关卡", description = "按 (gameKey, levelNo) 物理删除；删除后版本号递增")
    @Audited(action = "GAME_LEVEL_DELETE", targetType = "game_level", targetIdSpEL = "#gameKey + ':' + #levelNo")
    @DeleteMapping("/levels")
    public Result<String> deleteLevel(@RequestParam("gameKey") String gameKey,
                                      @RequestParam("levelNo") Integer levelNo) {
        gameLevelService.deleteLevel(gameKey, levelNo);
        return Result.ok("删除成功");
    }

    // ==================== 存档 / 成绩 ====================

    @Operation(summary = "存档列表", description = "按 gameKey / userId 筛选，联表补脱敏手机号与游戏名，按更新时间倒序")
    @GetMapping("/saves")
    public Result<Page<GameSaveAdminVo>> listSaves(GameSaveSearchFrom from) {
        return Result.ok(gameUserSaveService.adminPage(from));
    }

    @Operation(summary = "成绩列表", description = "按 gameKey 筛选，联表补脱敏手机号与游戏名，按 metric_value 降序")
    @GetMapping("/scores")
    public Result<Page<GameScoreAdminVo>> listScores(GameScoreSearchFrom from) {
        return Result.ok(gameScoreService.adminPage(from));
    }
}
