package com.smartwash.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 *
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("lockers")
public class Lockers implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "locker_id", type = IdType.AUTO)
    private Long lockerId;

    private Long schoolId;

    private Integer lockerNumber;

    private String status;

    private LocalDateTime lastUsedAt;

}
