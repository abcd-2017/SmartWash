package com.smartwash.toolbox.delivery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.toolbox.delivery.entity.ToolboxDeliveryItem;
import org.apache.ibatis.annotations.Param;

/**
 * 设备投递条目 Mapper。
 * 原子认领与字节统计是并发敏感 SQL，集中在本接口由 XML 实现。
 */
public interface ToolboxDeliveryItemMapper extends BaseMapper<ToolboxDeliveryItem> {

    /**
     * 原子认领（PsiTransfer claim 语义）：仅当未取走时置位，单条 UPDATE 天然原子。
     *
     * @return 影响行数：1=认领成功；0=已被取走或不存在
     */
    int claim(@Param("id") Long id);

    /**
     * 释放认领（下载中断时回滚，允许重取）：仅当已取走时回滚。
     *
     * @return 影响行数：1=释放成功
     */
    int release(@Param("id") Long id);

    /** 会话累计字节（NULL 记 0），用于会话总字节上限校验 */
    long sumObjectSize(@Param("sessionCode") String sessionCode);
}
