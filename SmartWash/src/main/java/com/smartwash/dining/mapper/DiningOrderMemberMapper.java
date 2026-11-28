package com.smartwash.dining.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.dining.entity.DiningOrderMember;
import com.smartwash.dining.vo.DiningCollaboratorVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 点单订单成员 Mapper。
 * 内置方法覆盖常规读写；跨订单聚合「协作过的用户」走自定义 SQL（见 DiningOrderMemberMapper.xml）。
 */
@Mapper
public interface DiningOrderMemberMapper extends BaseMapper<DiningOrderMember> {

    /**
     * 协作过的用户：与我共同参与的订单里的其他成员，按最近协作时间倒序。
     * 单条 SQL 自连接 + 分组聚合，替代「先查我所有订单 ID 再 IN 查成员」的两段式查询，
     * 避免历史订单多时 IN 列表膨胀与多次网络往返。
     */
    List<DiningCollaboratorVo> selectCollaborators(@Param("userId") Long userId);
}
