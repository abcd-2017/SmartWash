package com.smartwash.dining.vo;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 「我的进行中点单」VO：当前用户作为发起者、状态为 DRAFT 的唯一订单（无则 data=null）。
 *
 * 用途：列表 FAB / lite 主 CTA 收敛——已有进行中发起单时不再新建，直接进该单编辑态；
 * create 唯一闸门冲突（业务码 40901）时端上据此渲染提示与跳转。
 * 只暴露入口决策所需的最小字段（orderId/title/status），详情仍走 GET /{id}。
 */
@Data
@Accessors(chain = true)
public class DiningOrderActiveVo {

    private Long orderId;

    private String title;

    /** 恒为 DRAFT（本查询只取进行中）；保留字段便于端上与列表状态口径统一 */
    private String status;
}
