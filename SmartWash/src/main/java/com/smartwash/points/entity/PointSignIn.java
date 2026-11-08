package com.smartwash.points.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日签到记录，对应表 point_sign_in。
 * uk_sign_user_date(user_id, sign_date) 唯一索引 = 幂等 DB 闸门：
 * 并发同日签到只允许一条插入成功，其余捕获 DuplicateKeyException 判幂等返回。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("point_sign_in")
public class PointSignIn implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "sign_id", type = IdType.AUTO)
    private Long signId;

    private Long userId;

    /** 签到日（业务日期） */
    private LocalDate signDate;

    /** 签到时点的连签天数（昨日有记录则 +1，否则重置 1） */
    private Integer streak;

    private LocalDateTime createdAt;
}
