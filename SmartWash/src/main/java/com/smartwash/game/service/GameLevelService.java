package com.smartwash.game.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.game.entity.GameLevel;

import java.util.List;

/**
 * 游戏关卡服务。
 * 仅关卡定义型游戏使用；关卡与游戏包解耦，扩展关卡不改游戏包。
 */
public interface GameLevelService extends IService<GameLevel> {

    /**
     * 关卡列表：按 gameKey 返回全部关卡（含未启用，管理端用），按 sort / level_no 排序。
     */
    List<GameLevel> listByGame(String gameKey);

    /**
     * 下发关卡：仅 enabled=1，按 sort / level_no 排序（用户端游戏加载用）。
     */
    List<GameLevel> listEnabled(String gameKey);

    /**
     * 创建或更新关卡：按 (gameKey, levelNo) 唯一定位（upsert）。
     * 变更后递增 game_config.levels_version（供 H5 缓存判断）。
     */
    GameLevel upsertLevel(String gameKey, Integer levelNo, String name, String dataJson,
                          Integer difficulty, Integer enabled, Integer sort, String remark);

    /**
     * 删除关卡：按 (gameKey, levelNo)。变更后递增版本号。
     */
    void deleteLevel(String gameKey, Integer levelNo);

    /**
     * 读取当前关卡版本号（game_config.levels_version）。
     */
    int getLevelVersion(String gameKey);

    /**
     * 递增关卡版本号（后台变更关卡后调用）。
     */
    void bumpLevelVersion(String gameKey);
}
