package com.smartwash.toolbox.delivery.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 投递会话详情：会话元信息 + 全量条目（含已焚毁占位）。
 * 双端轮询同一结构，条目按创建时间正序返回（会话页对话流语义）。
 */
@Data
@Accessors(chain = true)
public class DeliverySessionDetailVo {

    private DeliverySessionVo session;

    private List<DeliveryItemVo> items;
}
