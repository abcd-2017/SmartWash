package com.smartwash.game.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.game.entity.GameConfig;
import com.smartwash.game.entity.GameLevel;
import com.smartwash.game.mapper.GameConfigMapper;
import com.smartwash.game.mapper.GameLevelMapper;
import com.smartwash.game.service.GameLevelService;
import com.smartwash.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 游戏关卡服务实现。
 *
 * <p>仅关卡定义型游戏使用。关卡与游戏包解耦：后台改关卡只动本表，
 * 每次变更递增 game_config.levels_version，H5 据版本号判断是否重新拉取。</p>
 *
 * <p>data_json 只校验 JSON 合法性（结构由游戏自定），不解析语义。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameLevelServiceImpl extends ServiceImpl<GameLevelMapper, GameLevel>
        implements GameLevelService {

    private final GameConfigMapper gameConfigMapper;

    @Override
    public List<GameLevel> listByGame(String gameKey) {
        requireGameKey(gameKey);
        return list(new LambdaQueryWrapper<GameLevel>()
                .eq(GameLevel::getGameKey, gameKey)
                .orderByAsc(GameLevel::getSort)
                .orderByAsc(GameLevel::getLevelNo));
    }

    @Override
    public List<GameLevel> listEnabled(String gameKey) {
        requireGameKey(gameKey);
        return list(new LambdaQueryWrapper<GameLevel>()
                .eq(GameLevel::getGameKey, gameKey)
                .eq(GameLevel::getEnabled, 1)
                .orderByAsc(GameLevel::getSort)
                .orderByAsc(GameLevel::getLevelNo));
    }

    @Override
    public GameLevel upsertLevel(String gameKey, Integer levelNo, String name, String dataJson,
                                 Integer difficulty, Integer enabled, Integer sort, String remark) {
        requireGameKey(gameKey);
        if (levelNo == null || levelNo < 1) {
            throw new CustomExceptions("关卡序号必须为正整数");
        }
        if (dataJson == null || dataJson.isBlank()) {
            throw new CustomExceptions("关卡数据不能为空");
        }
        try {
            JsonUtils.readTree(dataJson);
        } catch (Exception e) {
            throw new CustomExceptions("关卡数据不是合法 JSON");
        }
        int enable = enabled == null ? 1 : (enabled == 1 ? 1 : 0);
        int sortNo = sort == null ? levelNo * 10 : sort;

        GameLevel existing = getOne(new LambdaQueryWrapper<GameLevel>()
                .eq(GameLevel::getGameKey, gameKey)
                .eq(GameLevel::getLevelNo, levelNo), false);
        GameLevel entity = existing == null ? new GameLevel() : existing;
        entity.setGameKey(gameKey)
                .setLevelNo(levelNo)
                .setName(name)
                .setDataJson(dataJson)
                .setDifficulty(difficulty)
                .setEnabled(enable)
                .setSort(sortNo)
                .setRemark(remark);
        if (existing == null) {
            save(entity);
        } else {
            updateById(entity);
        }
        bumpLevelVersion(gameKey);
        return entity;
    }

    @Override
    public void deleteLevel(String gameKey, Integer levelNo) {
        requireGameKey(gameKey);
        if (levelNo == null) {
            throw new CustomExceptions("关卡序号不能为空");
        }
        remove(new LambdaQueryWrapper<GameLevel>()
                .eq(GameLevel::getGameKey, gameKey)
                .eq(GameLevel::getLevelNo, levelNo));
        bumpLevelVersion(gameKey);
    }

    @Override
    public int getLevelVersion(String gameKey) {
        GameConfig config = requireGame(gameKey);
        return config.getLevelsVersion() == null ? 1 : config.getLevelsVersion();
    }

    @Override
    public void bumpLevelVersion(String gameKey) {
        requireGame(gameKey);
        gameConfigMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<GameConfig>()
                .eq(GameConfig::getGameKey, gameKey)
                .setSql("levels_version = COALESCE(levels_version, 1) + 1"));
    }

    private GameConfig requireGame(String gameKey) {
        requireGameKey(gameKey);
        GameConfig config = gameConfigMapper.selectOne(new LambdaQueryWrapper<GameConfig>()
                .eq(GameConfig::getGameKey, gameKey), false);
        if (config == null) {
            throw new CustomExceptions("游戏不存在");
        }
        return config;
    }

    private void requireGameKey(String gameKey) {
        if (!StringUtils.hasText(gameKey)) {
            throw new CustomExceptions("gameKey 不能为空");
        }
    }
}
