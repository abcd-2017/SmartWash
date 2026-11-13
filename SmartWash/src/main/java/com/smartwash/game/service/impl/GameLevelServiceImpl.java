package com.smartwash.game.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.game.entity.GameConfig;
import com.smartwash.game.entity.GameLevel;
import com.smartwash.game.from.GameLevelBatchFrom;
import com.smartwash.game.mapper.GameConfigMapper;
import com.smartwash.game.mapper.GameLevelMapper;
import com.smartwash.game.service.GameLevelService;
import com.smartwash.game.vo.GameLevelImportResultVo;
import com.smartwash.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    @Transactional(rollbackFor = Exception.class)
    public GameLevelImportResultVo batchImportLevels(String gameKey, boolean replaceAll,
                                                     List<GameLevelBatchFrom.LevelItem> levels) {
        requireGame(gameKey);
        if (levels == null || levels.isEmpty()) {
            throw new CustomExceptions("导入关卡列表不能为空");
        }
        // 批内预检（uk_game_level：一游戏一序号一行），任一项失败整批回滚不落库
        Set<Integer> seenNos = new HashSet<>();
        for (int i = 0; i < levels.size(); i++) {
            GameLevelBatchFrom.LevelItem item = levels.get(i);
            int seq = i + 1;
            if (item.getLevelNo() == null || item.getLevelNo() < 1) {
                throw new CustomExceptions("第 " + seq + " 关：关卡序号必须为正整数");
            }
            if (!seenNos.add(item.getLevelNo())) {
                throw new CustomExceptions("第 " + seq + " 关：关卡序号 " + item.getLevelNo() + " 在本批内重复");
            }
            if (item.getDataJson() == null || item.getDataJson().isBlank()) {
                throw new CustomExceptions("第 " + seq + " 关：关卡数据不能为空");
            }
            try {
                JsonUtils.readTree(item.getDataJson());
            } catch (Exception e) {
                throw new CustomExceptions("第 " + seq + " 关：关卡数据不是合法 JSON");
            }
        }
        if (replaceAll) {
            remove(new LambdaQueryWrapper<GameLevel>().eq(GameLevel::getGameKey, gameKey));
        }
        int created = 0;
        int updated = 0;
        for (GameLevelBatchFrom.LevelItem item : levels) {
            GameLevel existing = getOne(new LambdaQueryWrapper<GameLevel>()
                    .eq(GameLevel::getGameKey, gameKey)
                    .eq(GameLevel::getLevelNo, item.getLevelNo()), false);
            GameLevel entity = existing == null ? new GameLevel() : existing;
            entity.setGameKey(gameKey)
                    .setLevelNo(item.getLevelNo())
                    .setName(item.getName())
                    .setDataJson(item.getDataJson())
                    .setDifficulty(item.getDifficulty())
                    .setEnabled(item.getEnabled() == null ? 1 : (item.getEnabled() == 1 ? 1 : 0))
                    .setSort(item.getSort() == null ? item.getLevelNo() * 10 : item.getSort())
                    .setRemark(item.getRemark());
            if (existing == null) {
                save(entity);
                created++;
            } else {
                updateById(entity);
                updated++;
            }
        }
        // 整批一次性递增版本号：逐关递增会让 H5 按「版本已变」拉到中间态关卡集
        bumpLevelVersion(gameKey);
        return new GameLevelImportResultVo(levels.size(), created, updated);
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
