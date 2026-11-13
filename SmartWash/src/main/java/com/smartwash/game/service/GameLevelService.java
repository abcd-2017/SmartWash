package com.smartwash.game.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.game.entity.GameLevel;
import com.smartwash.game.from.GameLevelBatchFrom;
import com.smartwash.game.vo.GameLevelImportResultVo;

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
     * 批量导入关卡（管理端）：整批事务，任一项校验失败（序号/JSON/批内重复）全量回滚不落库；
     * replaceAll=true 先清空该游戏现有关卡再导入；成功后 levels_version 仅递增一次，
     * 避免逐关递增让 H5 拉到中间态关卡集。
     */
    GameLevelImportResultVo batchImportLevels(String gameKey, boolean replaceAll,
                                              List<GameLevelBatchFrom.LevelItem> levels);

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
