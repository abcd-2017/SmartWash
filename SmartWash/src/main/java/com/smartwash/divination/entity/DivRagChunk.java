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
 * RAG 语料：切片（embedding 存 JSON，内存余弦检索）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("div_rag_chunk")
public class DivRagChunk implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long documentId;

    private String chapter;

    private Integer seq;

    private String content;

    /** float 数组 JSON */
    private String embedding;

    private Integer tokenCount;

    private LocalDateTime createdAt;
}
