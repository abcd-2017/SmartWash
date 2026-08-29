package com.smartwash.divination.from;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 创建卦例请求 DTO。
 * 客户端上传起卦原始输入（爻值/起卦参数 + 起卦时刻），服务端 core 重算权威盘面。
 */
@Data
public class CreateRecordFrom {

    @NotBlank(message = "术数方法不能为空")
    private String method;

    private String category;

    @NotBlank(message = "问题不能为空")
    private String question;

    @NotBlank(message = "起卦方式不能为空")
    private String castMethod;

    @NotNull(message = "起卦时刻不能为空")
    private Long castAt;

    /** 时区偏移分钟，东八=480 */
    private Integer tzOffset = 480;

    /** 起卦原始输入（六爻爻值数组 [4/1/2/3 ...] 等） */
    private List<Integer> lines;

    /** 端上排盘结果 JSON 字符串 */
    private String clientChart;
}
