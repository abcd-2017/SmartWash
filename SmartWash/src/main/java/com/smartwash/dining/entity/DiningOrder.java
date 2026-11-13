package com.smartwash.dining.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 家庭点单-点单订单。
 * 一人发起（title + 从公共菜谱库勾选本单菜单）生成二维码分享，其他人扫码加入后各自勾选想吃的菜；
 * 发起者结束后锁定订单并写 shopping_list 快照（结束接口在 11 号票）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("dining_order")
public class DiningOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 订单标题（如 周五晚火锅） */
    private String title;

    /** 发起者用户 ID(users.user_id) */
    private Long creatorUserId;

    /** 订单状态：ONGOING 进行中 / FINISHED 已结束（DiningOrderStatus） */
    private String status;

    /** 加入凭证（二维码携带，uk_join_token 兜底唯一；重置即旧二维码全部作废） */
    private String joinToken;

    /** 乐观并发版本号：每次选菜变更条件 UPDATE +1，客户端写请求携带已知 version，不匹配返回业务错误码 409 */
    private Integer version;

    /** 购买清单快照（结束时写入：食材名并集去重 + 按菜视图），本票仅定义列，11 号票写入 */
    private String shoppingList;

    /** 结束时间 */
    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
