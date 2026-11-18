package com.smartwash.dining.from;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发起点单（publish）请求 DTO：DRAFT→ONGOING。
 * 草稿期标题可空，publish 时二次校验必填；请求携带标题用于「草稿页原位编辑后定稿」，
 * 不传则沿用库内标题。version 乐观锁：与服务端不一致返回业务错误码 409。
 */
@Data
public class PublishDiningOrderFrom {

    /** 订单标题（草稿页编辑后的最终标题）；不传沿用库内标题，最终仍须非空 */
    @Size(max = 64, message = "订单标题过长（上限64字符）")
    private String title;

    /** 客户端已知的订单 version（与服务端不一致返回业务错误码 409，客户端拉取最新后重放） */
    @NotNull(message = "已知版本号 version 不能为空")
    private Long version;
}
