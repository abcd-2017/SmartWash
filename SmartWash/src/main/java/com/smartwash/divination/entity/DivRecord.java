package com.smartwash.divination.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 占卜卦例主表。
 * 客户端上传爻值/起卦参数，服务端 core 重算权威盘面（server_chart）并与 client_chart 比对。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("div_record")
public class DivRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** liuyao/meihua/qimen/liuren */
    private String method;

    /** general/career/wealth/... */
    private String category;

    private String question;

    /** auto/manual/time */
    private String castMethod;

    /** 起卦时刻（权威，追问沿用） */
    private LocalDateTime castAt;

    /** 时区偏移分钟，东八=480 */
    private Integer tzOffset;

    /** app/today */
    private String source;

    /** 起卦原始输入（六爻爻值数组等） */
    private String lines;

    /** 端上排盘结果快照 */
    private String clientChart;

    /** 服务端 core 重算权威盘面 */
    private String serverChart;

    /** 端上/服务端盘面一致 */
    private Integer chartVerified;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
