package com.smartwash.game.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.game.entity.GameUserSave;
import com.smartwash.game.from.GameSaveSearchFrom;
import com.smartwash.game.mapper.GameUserSaveMapper;
import com.smartwash.game.service.GameUserSaveService;
import com.smartwash.game.vo.GameSaveAdminVo;
import com.smartwash.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 游戏账号存档服务实现。
 *
 * <p>并发：uk_user_game 唯一索引兜底。写路径先 UPDATE，影响 0 行再 INSERT，
 * INSERT 撞唯一键（并发首写）则回退为 UPDATE，保证幂等。</p>
 *
 * <p>内容校验：state_json 只校验「是合法 JSON」与「不超大小上限」，
 * 不解析语义（结构由游戏自定，服务端永不解析）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameUserSaveServiceImpl extends ServiceImpl<GameUserSaveMapper, GameUserSave>
        implements GameUserSaveService {

    /** 存档大小上限：单游戏存档预期 KB 级，64KB 留足余量并挡住异常膨胀 */
    private static final int MAX_STATE_BYTES = 64 * 1024;

    @Override
    public Page<GameSaveAdminVo> adminPage(GameSaveSearchFrom from) {
        Page<GameSaveAdminVo> page = new Page<>(from.getPage(), from.getSize());
        return baseMapper.selectAdminPage(page, from.getGameKey(), from.getUserId());
    }

    @Override
    public GameUserSave getByUserAndGame(Long userId, String gameKey) {
        requireGameKey(gameKey);
        return getOne(new LambdaQueryWrapper<GameUserSave>()
                .eq(GameUserSave::getUserId, userId)
                .eq(GameUserSave::getGameKey, gameKey), false);
    }

    @Override
    public GameUserSave saveState(Long userId, String gameKey, String stateJson, Integer schemaVer) {
        requireGameKey(gameKey);
        if (stateJson == null || stateJson.isBlank()) {
            throw new CustomExceptions("存档内容不能为空");
        }
        if (stateJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_STATE_BYTES) {
            throw new CustomExceptions("存档内容超过大小上限");
        }
        // 仅校验 JSON 合法性，不解析语义
        try {
            JsonUtils.readTree(stateJson);
        } catch (Exception e) {
            throw new CustomExceptions("存档内容不是合法 JSON");
        }
        int ver = schemaVer == null ? 1 : schemaVer;

        // 先尝试更新（已存在则覆盖）
        int updated = baseMapper.update(null, new LambdaUpdateWrapper<GameUserSave>()
                .eq(GameUserSave::getUserId, userId)
                .eq(GameUserSave::getGameKey, gameKey)
                .set(GameUserSave::getStateJson, stateJson)
                .set(GameUserSave::getSchemaVer, ver));
        if (updated > 0) {
            return getByUserAndGame(userId, gameKey);
        }

        // 不存在则首写；并发首写撞唯一索引时回退为更新
        try {
            GameUserSave entity = new GameUserSave()
                    .setUserId(userId)
                    .setGameKey(gameKey)
                    .setStateJson(stateJson)
                    .setSchemaVer(ver);
            save(entity);
            return entity;
        } catch (DuplicateKeyException e) {
            log.debug("存档并发首写，回退为更新, userId: {}, gameKey: {}", userId, gameKey);
            baseMapper.update(null, new LambdaUpdateWrapper<GameUserSave>()
                    .eq(GameUserSave::getUserId, userId)
                    .eq(GameUserSave::getGameKey, gameKey)
                    .set(GameUserSave::getStateJson, stateJson)
                    .set(GameUserSave::getSchemaVer, ver));
            return getByUserAndGame(userId, gameKey);
        }
    }

    private void requireGameKey(String gameKey) {
        if (!StringUtils.hasText(gameKey)) {
            throw new CustomExceptions("gameKey 不能为空");
        }
    }
}
