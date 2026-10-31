package com.smartwash.game.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.game.entity.GameConfig;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 游戏库 Mapper。
 * 自定义 SQL 仅承载用户端下发查询（显式列清单，天然不下发 enabled/remark/存储路径等管理字段），
 * 其余走 MyBatis-Plus 内置方法。
 */
@Mapper
public interface GameConfigMapper extends BaseMapper<GameConfig> {

    /**
     * 启用中的游戏列表（enabled=1，按 sort 升序、id 升序），仅含用户端下发列。
     */
    List<GameConfig> selectEnabledList();
}
