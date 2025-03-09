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
@TableName("schools")
public class Schools implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "school_id", type = IdType.AUTO)
    private Long schoolId;

    private String schoolName;

    private String location;

    private Integer lockerCount;

    private LocalDateTime createdAt;

}
