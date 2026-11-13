package com.smartwash.toolbox.assettag.vo;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 物品标签公开解析结果（扫码寻物页数据源）。
 * 联系方式仅「寻物中 + 失主留了联系方式」时解密下发，正常/已寻回状态不下发（防批量扫码扒信息）。
 */
@Data
@Accessors(chain = true)
public class AssetTagPublicVo {

    private String itemName;

    private String itemDesc;

    /** 0 正常 / 1 寻物中 / 2 已寻回 */
    private Integer status;

    /** 是否下发联系方式（true 时 contact 字段有值） */
    private boolean hasContact;

    /** 失主联系方式（明文；仅寻物中状态解密下发） */
    private String contact;
}
