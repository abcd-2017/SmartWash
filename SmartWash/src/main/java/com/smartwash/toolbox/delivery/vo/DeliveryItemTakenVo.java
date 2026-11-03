package com.smartwash.toolbox.delivery.vo;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 取走条目响应对象。
 * 文本条目：take 一步完成认领+焚毁，textContent 直接返回（此后内容不可再读）；
 * 文件条目：take 仅原子认领，内容经 contentUrl 流式下载（下载完成才删除对象，中断释放认领）。
 */
@Data
@Accessors(chain = true)
public class DeliveryItemTakenVo {

    private Long id;

    /** 1文本 2图片 3文件 */
    private Integer kind;

    private String title;

    /** 文本条目正文（认领后焚毁，仅本次响应返回一次） */
    private String textContent;

    /** 文件条目内容下载路径（相对路径，需先 take 认领） */
    private String contentUrl;
}
