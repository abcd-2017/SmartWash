package com.smartwash.game.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.game.entity.GameUserSave;
import com.smartwash.game.from.GameSaveSearchFrom;
import com.smartwash.game.vo.GameSaveAdminVo;

/**
 * 游戏账号存档服务。
 * H5 经桥代理读写：换设备后进度跟随账号恢复。
 *
 * <p>存档内容（state_json）由游戏自定义，服务端不解析语义，仅校验 JSON 合法性与大小上限。</p>
 */
public interface GameUserSaveService extends IService<GameUserSave> {

    /**
     * 管理端存档分页列表（按 gameKey / userId 筛选，联表补脱敏手机号与游戏名）。
     */
    Page<GameSaveAdminVo> adminPage(GameSaveSearchFrom from);

    /**
     * 读存档：按 (userId, gameKey)。
     *
     * @return 存档实体；无记录返回 null（首次进入游戏的正常态）
     */
    GameUserSave getByUserAndGame(Long userId, String gameKey);

    /**
     * 写存档：upsert（一人一游戏一行）。
     * 并发首写由 uk_user_game 唯一索引兜底；state_json 非法或超限时抛业务异常。
     *
     * @param stateJson 游戏自定义 JSON 字符串
     * @param schemaVer 存档结构版本（游戏侧升级时递增）
     * @return 落库后的存档
     */
    GameUserSave saveState(Long userId, String gameKey, String stateJson, Integer schemaVer);
}
