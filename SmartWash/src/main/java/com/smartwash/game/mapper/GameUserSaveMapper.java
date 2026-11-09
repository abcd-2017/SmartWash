package com.smartwash.game.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.game.entity.GameUserSave;
import com.smartwash.game.vo.GameSaveAdminVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 游戏账号存档 Mapper。
 * 一人一游戏一行，读写走 MyBatis-Plus 内置方法（getOne / save / update）；
 * 管理端分页列表走自定义 SQL（联表 users / game_config 补展示字段）。
 */
@Mapper
public interface GameUserSaveMapper extends BaseMapper<GameUserSave> {

    /**
     * 管理端存档分页列表（联表补脱敏手机号与游戏名）。
     */
    Page<GameSaveAdminVo> selectAdminPage(Page<GameSaveAdminVo> page,
                                          @Param("gameKey") String gameKey,
                                          @Param("userId") Long userId);
}
