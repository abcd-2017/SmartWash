package com.smartwash.game.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.game.entity.GameScoreEntry;
import com.smartwash.game.vo.GameScoreAdminVo;
import com.smartwash.game.vo.GameScoreUserVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 游戏排行榜 Mapper。
 * 写入走 MyBatis-Plus 内置方法（save / updateById）；榜内排名与管理端列表走自定义 SQL。
 */
@Mapper
public interface GameScoreEntryMapper extends BaseMapper<GameScoreEntry> {

    /**
     * 榜内 TOP N（不含用户信息，用户信息由 Service 批量补齐）。
     *
     * @param gameKey 游戏标识
     * @param limit   取前 N
     * @param asc     true=升序（步数/用时类，越小越好）；false=降序（分数类，越大越好）
     */
    List<GameScoreEntry> selectTopN(@Param("gameKey") String gameKey,
                                    @Param("limit") int limit,
                                    @Param("asc") boolean asc);

    /**
     * 批量查询榜内用户信息（脱敏手机号 + 头像），供 Service 拼装榜单。
     */
    List<GameScoreUserVo> selectScoreUsers(@Param("userIds") List<Long> userIds);

    /**
     * 管理端成绩分页列表（联表补脱敏手机号与游戏名，按 metric_value 降序）。
     */
    Page<GameScoreAdminVo> selectAdminPage(Page<GameScoreAdminVo> page,
                                           @Param("gameKey") String gameKey);
}
