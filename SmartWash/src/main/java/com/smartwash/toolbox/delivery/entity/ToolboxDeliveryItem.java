package com.smartwash.toolbox.delivery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工具箱-设备投递条目。
 * 会话主记录复用 toolbox_short_code（content_type=6 设备投递），本表只存条目；
 * 条目以 session_code 冗余关联（无外键，对齐 toolbox_short_visit 冗余风格）。
 * 无痕承诺：不建访问明细表，仅会话级 click_count 计数，日志不记内容。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("toolbox_delivery_item")
public class ToolboxDeliveryItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 投递码（冗余关联 toolbox_short_code.code，无外键） */
    private String sessionCode;

    /** 1手机→电脑 2电脑→手机 */
    private Integer direction;

    /** 1文本 2图片 3文件 */
    private Integer kind;

    /** 文件名或文本摘要 */
    private String title;

    /** kind=1:文本正文;焚毁后置 NULL */
    private String textContent;

    /** kind=2/3:MinIO对象名 delivery/{code}/{uuid}.{ext} */
    private String objectKey;

    /** 字节数 */
    private Long objectSize;

    private String mimeType;

    /** 完整性校验（LocalSend 协议同名字段;015 秒传思路预留） */
    private String sha256;

    /** 1取走即焚(默认) 0保留至会话结束 */
    private Integer burnAfterTake;

    /** 0未取走 1已取走（原子置位，PsiTransfer claim 语义） */
    private Integer taken;

    private LocalDateTime takenAt;

    /** 条目过期,NULL继承会话,读路径判断 */
    private LocalDateTime expireAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
