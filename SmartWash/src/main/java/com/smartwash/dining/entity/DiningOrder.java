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
 * 吃啥-点单订单。
 * 产品模型：菜单即已选清单，全员共同编辑，发起者一步「提交」→ 订单直接结束。
 * 一人发起（title + 从公共菜谱库勾选本单菜单）生成二维码分享，其他人扫码加入后共同加/移除菜；
 * 创建即 DRAFT（标题/菜单可空、全部成员可加/移除菜），发起者 submit 提交即 FINISHED，
 * 并写 shopping_list 购买清单快照（聚合本单菜单）；发起者放弃置 CANCELLED（保留订单供参与者查看）。
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

    /** 订单状态：DRAFT 草稿 / FINISHED 已结束 / CANCELLED 已取消（DiningOrderStatus） */
    private String status;

    /** 加入凭证（二维码携带，uk_join_token 兜底唯一；重置即旧二维码全部作废） */
    private String joinToken;

    /** 乐观并发版本号：每次选菜变更条件 UPDATE +1，客户端写请求携带已知 version，不匹配返回业务错误码 409 */
    private Integer version;

    /** 购买清单快照（submit 提交时写入：食材名并集去重 + 按菜视图） */
    private String shoppingList;

    private LocalDateTime finishedAt;

    /** 取消时间（发起者放弃草稿时写入；FINISHED/进行中为 null） */
    private LocalDateTime cancelledAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
