package com.smartwash.points.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 积分账户，对应表 point_account。
 * 一人一行（uk_point_account_user），懒创建：首次加分事务内 insert，冲突即并发首建者赢。
 * balance 是 point_ledger 的派生缓存值，仅允许条件 UPDATE 单语句自增，禁止读改写。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("point_account")
public class PointAccount implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "account_id", type = IdType.AUTO)
    private Long accountId;

    private Long userId;

    /** 当前积分余额（流水派生值） */
    private Integer balance;

    /** 累计获得积分（只增不减） */
    private Integer totalEarned;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
