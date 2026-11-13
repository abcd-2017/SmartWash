package com.smartwash.dining.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.dining.constant.DiningOrderStatus;
import com.smartwash.dining.entity.DiningOrder;
import com.smartwash.dining.entity.DiningOrderItem;
import com.smartwash.dining.entity.DiningOrderMember;
import com.smartwash.dining.entity.DiningOrderMenu;
import com.smartwash.dining.from.AddDiningOrderItemFrom;
import com.smartwash.dining.from.UpdateDiningOrderItemFrom;
import com.smartwash.dining.mapper.DiningOrderItemMapper;
import com.smartwash.dining.mapper.DiningOrderMapper;
import com.smartwash.dining.mapper.DiningOrderMemberMapper;
import com.smartwash.dining.mapper.DiningOrderMenuMapper;
import com.smartwash.dining.service.DiningOrderItemService;
import com.smartwash.exception.CustomExceptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 选菜服务实现（共享购物车）。
 * 写入协议（spec「共享购物车并发」）：version 条件 UPDATE 原子递增先行（乐观并发闸门），
 * 0 行影响 = 版本冲突抛业务错误码 409；闸门放行后的业务校验失败经 @Transactional 回滚恢复 version 原值，
 * 保证 version 只反映真正落库的变更。
 */
@Slf4j
@Service
public class DiningOrderItemServiceImpl extends ServiceImpl<DiningOrderItemMapper, DiningOrderItem>
        implements DiningOrderItemService {

    /** version 冲突业务码（HTTP 语义冲突，对齐 GameServiceException 的 409 用法） */
    private static final int VERSION_CONFLICT_CODE = 409;

    private final DiningOrderMapper orderMapper;

    private final DiningOrderMemberMapper memberMapper;

    private final DiningOrderMenuMapper menuMapper;

    public DiningOrderItemServiceImpl(DiningOrderMapper orderMapper,
                                      DiningOrderMemberMapper memberMapper,
                                      DiningOrderMenuMapper menuMapper) {
        this.orderMapper = orderMapper;
        this.memberMapper = memberMapper;
        this.menuMapper = menuMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addItem(Long orderId, AddDiningOrderItemFrom from, Long userId) {
        casIncrementVersion(orderId, from.getVersion());
        requireOngoingOrder(orderId);
        requireMember(orderId, userId);
        requireInMenu(orderId, from.getRecipeId());
        try {
            this.save(new DiningOrderItem()
                    .setOrderId(orderId)
                    .setRecipeId(from.getRecipeId())
                    .setUserId(userId)
                    .setNote(from.getNote()));
        } catch (DuplicateKeyException e) {
            // 并发窗口撞 uk(order+recipe+user)：一人一菜一条，重复勾选拒绝（端上引导走改备注）
            throw new CustomExceptions("你已选过这道菜，可直接修改备注");
        }
        log.debug("勾选菜品, orderId: {}, recipeId: {}, userId: {}", orderId, from.getRecipeId(), userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateItemNote(Long orderId, UpdateDiningOrderItemFrom from, Long userId) {
        casIncrementVersion(orderId, from.getVersion());
        requireOngoingOrder(orderId);
        requireMember(orderId, userId);
        DiningOrderItem item = this.getOne(new LambdaQueryWrapper<DiningOrderItem>()
                .eq(DiningOrderItem::getOrderId, orderId)
                .eq(DiningOrderItem::getRecipeId, from.getRecipeId())
                .eq(DiningOrderItem::getUserId, userId), false);
        if (item == null) {
            throw new CustomExceptions("你还没选这道菜");
        }
        // note 为 null 即清空备注（一人一菜一条，改的只是本人那条）
        item.setNote(from.getNote());
        this.updateById(item);
        log.debug("修改选菜备注, orderId: {}, recipeId: {}, userId: {}", orderId, from.getRecipeId(), userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeItem(Long orderId, Long recipeId, Long version, Long userId) {
        casIncrementVersion(orderId, version);
        requireOngoingOrder(orderId);
        requireMember(orderId, userId);
        boolean removed = this.remove(new LambdaQueryWrapper<DiningOrderItem>()
                .eq(DiningOrderItem::getOrderId, orderId)
                .eq(DiningOrderItem::getRecipeId, recipeId)
                .eq(DiningOrderItem::getUserId, userId));
        if (!removed) {
            throw new CustomExceptions("你还没选这道菜");
        }
        log.debug("取消选菜, orderId: {}, recipeId: {}, userId: {}", orderId, recipeId, userId);
    }

    /**
     * version 乐观并发闸门：条件 UPDATE（WHERE id=? AND version=? SET version=version+1）原子递增，
     * 影响行数 0 = 客户端已知版本过期 → 业务错误码 409，客户端拉取最新详情后重放。
     * 闸门先行于业务校验：后续任何失败都经事务回滚恢复 version 原值。
     */
    private void casIncrementVersion(Long orderId, Long knownVersion) {
        if (knownVersion == null) {
            throw new CustomExceptions("已知版本号 version 不能为空");
        }
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<DiningOrder>()
                .eq(DiningOrder::getId, orderId)
                .eq(DiningOrder::getVersion, knownVersion)
                .setSql("version = version + 1"));
        if (updated == 0) {
            throw new CustomExceptions(VERSION_CONFLICT_CODE, "订单已被他人更新，请刷新后重试");
        }
    }

    private void requireOngoingOrder(Long orderId) {
        DiningOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new CustomExceptions("点单不存在");
        }
        if (!DiningOrderStatus.ONGOING.getCode().equals(order.getStatus())) {
            throw new CustomExceptions("点单已结束，不能修改选菜");
        }
    }

    private void requireMember(Long orderId, Long userId) {
        Long count = memberMapper.selectCount(new LambdaQueryWrapper<DiningOrderMember>()
                .eq(DiningOrderMember::getOrderId, orderId)
                .eq(DiningOrderMember::getUserId, userId));
        if (count == null || count == 0) {
            throw new CustomExceptions("仅订单成员可以选菜");
        }
    }

    private void requireInMenu(Long orderId, Long recipeId) {
        Long count = menuMapper.selectCount(new LambdaQueryWrapper<DiningOrderMenu>()
                .eq(DiningOrderMenu::getOrderId, orderId)
                .eq(DiningOrderMenu::getRecipeId, recipeId));
        if (count == null || count == 0) {
            throw new CustomExceptions("该菜不在本单菜单内");
        }
    }
}
