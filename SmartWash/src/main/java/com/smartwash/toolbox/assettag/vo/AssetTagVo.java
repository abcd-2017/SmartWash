package com.smartwash.toolbox.assettag.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 物品标签视图对象（owner 视角）。
 * 联系方式只回「是否已留」（hasContact），明文/密文均不回显——编辑时留空即不修改。
 */
@Data
@Accessors(chain = true)
public class AssetTagVo {

    /** 主记录 id（toolbox_short_code.id，删除/详情定位用） */
    private Long id;

    /** 标签码（= toolbox_short_code.code） */
    private String code;

    private String itemName;

    private String itemDesc;

    /** 0 正常 / 1 寻物中 / 2 已寻回 */
    private Integer status;

    /** 是否已留联系方式（编辑侧展示用；明文永不回显） */
    private boolean hasContact;

    /** 分享链接：{toolbox.web-base-url}/tag/{code}；未配置 web-base-url 时为空串 */
    private String tagUrl;

    /** 累计扫码次数（主表 click_count，公开解析链路异步累加） */
    private Long scanCount;

    /** 最近被扫码时间（失主线索） */
    private LocalDateTime lastScannedAt;

    private LocalDateTime createdAt;
}
