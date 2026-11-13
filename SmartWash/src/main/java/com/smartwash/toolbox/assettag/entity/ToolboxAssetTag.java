package com.smartwash.toolbox.assettag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 物品标签明细（一物一码 M2）。
 * 主记录复用 toolbox_short_code（content_type=8，镜像设备投递 contentType=6 会话范式）：
 * code / owner / expire_at 等生命周期归主表管，本表只放物品信息与联系方式密文。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("toolbox_asset_tag")
public class ToolboxAssetTag implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 标签码，关联 toolbox_short_code.code（content_type=8），uk_tag_code 唯一 */
    private String code;

    /** 物品名称 */
    private String itemName;

    /** 物品描述（可选） */
    private String itemDesc;

    /** 联系方式密文 v{ver}:{iv}:{ct}（AES-256-GCM），NULL=未留；仅寻物中状态解密下发 */
    private String contactCipher;

    /** 0 正常 / 1 寻物中 / 2 已寻回（状态裁定在后端，端上只做按钮可见性） */
    private Integer status;

    /** 最近被扫码时间（失主线索；公开解析链路写入） */
    private LocalDateTime lastScannedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
