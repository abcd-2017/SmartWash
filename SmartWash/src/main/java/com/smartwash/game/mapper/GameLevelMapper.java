package com.smartwash.game.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.game.entity.GameLevel;
import org.apache.ibatis.annotations.Mapper;

/**
 * 游戏关卡 Mapper。
 * 增删改查走 MyBatis-Plus 内置方法（按 gameKey + levelNo 唯一定位）。
 */
@Mapper
public interface GameLevelMapper extends BaseMapper<GameLevel> {
}
