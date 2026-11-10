package com.smartwash.game.controller;

import com.smartwash.common.Result;
import com.smartwash.game.entity.GameLevel;
import com.smartwash.game.entity.GameUserSave;
import com.smartwash.game.from.GameScoreReportFrom;
import com.smartwash.game.from.GameStateUpdateFrom;
import com.smartwash.game.service.GameLevelService;
import com.smartwash.game.service.GameScoreService;
import com.smartwash.game.service.GameUserSaveService;
import com.smartwash.game.vo.GameLeaderboardVo;
import com.smartwash.game.vo.GameLevelVo;
import com.smartwash.game.vo.GameLevelsVo;
import com.smartwash.game.vo.GameStateVo;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 游戏用户数据控制器（存档 / 成绩 / 排行榜 / 关卡下发）。
 * 路径前缀 /web/auth/game，需 ROLE_USER（SecurityConfig 统一拦截）。
 *
 * <p>本组接口由 H5 游戏经 Android 桥代理调用（桥替代 H5 持 token 发请求）；
 * 存档内容服务端不解析语义，仅校验合法性与大小。</p>
 */
@Tag(name = "游戏中心-用户数据", description = "游戏存档读写/成绩上报/排行榜/关卡下发（ROLE_USER）")
@Slf4j
@RestController
@RequestMapping("/web/auth/game")
@RequiredArgsConstructor
public class WebGameDataController {

    private final GameUserSaveService gameUserSaveService;
    private final GameScoreService gameScoreService;
    private final GameLevelService gameLevelService;

    @Operation(summary = "读存档", description = "按 gameKey 读当前用户存档；无存档时 stateJson 为 null（首次进入正常态）")
    @GetMapping("/state")
    public Result<GameStateVo> getState(@RequestParam("gameKey") String gameKey) {
        LoginUser user = UserContextHolder.getUser();
        GameUserSave save = gameUserSaveService.getByUserAndGame(user.getUserId(), gameKey);
        GameStateVo vo = new GameStateVo().setGameKey(gameKey);
        if (save != null) {
            vo.setStateJson(save.getStateJson())
                    .setSchemaVer(save.getSchemaVer())
                    .setUpdatedAt(save.getUpdatedAt());
        }
        return Result.ok(vo);
    }

    @Operation(summary = "写存档", description = "upsert 当前用户存档（一人一游戏一行）；stateJson 仅校验合法性与大小，不解析语义")
    @PutMapping("/state")
    public Result<GameStateVo> saveState(@RequestBody @Valid GameStateUpdateFrom from) {
        LoginUser user = UserContextHolder.getUser();
        GameUserSave save = gameUserSaveService.saveState(
                user.getUserId(), from.getGameKey(), from.getStateJson(), from.getSchemaVer());
        GameStateVo vo = new GameStateVo()
                .setGameKey(from.getGameKey())
                .setStateJson(save.getStateJson())
                .setSchemaVer(save.getSchemaVer())
                .setUpdatedAt(save.getUpdatedAt());
        return Result.ok(vo);
    }

    @Operation(summary = "上报成绩", description = "按 gameKey 上报成绩，取历史最佳（方向由 game_config.metric_order 决定）；未开启排行榜的游戏报错")
    @PostMapping("/score")
    public Result<String> reportScore(@RequestBody @Valid GameScoreReportFrom from) {
        LoginUser user = UserContextHolder.getUser();
        gameScoreService.reportScore(user.getUserId(), from.getGameKey(), from.getMetricValue(),
                from.getMetricExtra(), from.getMetricJson());
        return Result.ok("上报成功");
    }

    @Operation(summary = "排行榜", description = "按 gameKey 返回 TOP N（含当前用户排名）；未开启排行榜返回空榜")
    @GetMapping("/leaderboard")
    public Result<GameLeaderboardVo> leaderboard(@RequestParam("gameKey") String gameKey,
                                                 @RequestParam(value = "limit", defaultValue = "20") int limit) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(gameScoreService.getLeaderboard(gameKey, limit, user.getUserId()));
    }

    @Operation(summary = "关卡下发", description = "返回启用中的关卡列表 + 版本号（端上据版本号判断缓存是否失效）")
    @GetMapping("/levels")
    public Result<GameLevelsVo> levels(@RequestParam("gameKey") String gameKey) {
        List<GameLevel> levels = gameLevelService.listEnabled(gameKey);
        List<GameLevelVo> vos = new ArrayList<>(levels.size());
        for (GameLevel level : levels) {
            vos.add(new GameLevelVo()
                    .setLevelNo(level.getLevelNo())
                    .setName(level.getName())
                    .setDataJson(level.getDataJson())
                    .setDifficulty(level.getDifficulty())
                    .setSort(level.getSort()));
        }
        return Result.ok(new GameLevelsVo()
                .setGameKey(gameKey)
                .setVersion(gameLevelService.getLevelVersion(gameKey))
                .setLevels(vos));
    }
}
