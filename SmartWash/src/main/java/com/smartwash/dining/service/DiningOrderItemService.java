package com.smartwash.dining.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.dining.entity.DiningOrderItem;
import com.smartwash.dining.from.AddDiningOrderItemFrom;
import com.smartwash.dining.from.UpdateDiningOrderItemFrom;

/**
 * 选菜服务（共享购物车）：勾选 / 改备注 / 取消（一人一菜一条，取消即删行）。
 * 全部变更走 dining_order.version 乐观并发协议：
 * 写请求携带客户端已知 version，服务端条件 UPDATE（WHERE id=? AND version=? SET version=version+1）
 * 原子递增，0 行影响 = 版本冲突，返回业务错误码 409（客户端拉取最新后重放）。
 * 所有操作要求：成员身份 + 订单 ONGOING + 菜在本单菜单内。
 */
public interface DiningOrderItemService extends IService<DiningOrderItem> {

    /** 勾选菜品：一人一菜一条，重复勾选拒绝（uk_order_recipe_user 兜底并发） */
    void addItem(Long orderId, AddDiningOrderItemFrom from, Long userId);

    /** 修改本人选菜备注（note 传 null 即清空） */
    void updateItemNote(Long orderId, UpdateDiningOrderItemFrom from, Long userId);

    /** 取消本人勾选（删除自己的选菜记录） */
    void removeItem(Long orderId, Long recipeId, Long version, Long userId);
}
