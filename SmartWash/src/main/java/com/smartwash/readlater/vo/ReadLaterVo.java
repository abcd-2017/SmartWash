package com.smartwash.readlater.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 稍后读视图对象（用户端）。
 */
@Data
@Accessors(chain = true)
public class ReadLaterVo {

    private Long id;

    private String url;

    private String title;

    /** AI 摘要，未生成时为 null */
    private String digest;

    /** 标签数组（由逗号分隔存储拆分），无标签为空数组 */
    private List<String> tags;

    /** 0未读 1已读 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
