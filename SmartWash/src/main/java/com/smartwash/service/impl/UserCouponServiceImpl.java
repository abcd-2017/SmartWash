package com.smartwash.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.entity.Coupon;
import com.smartwash.entity.Orders;
import com.smartwash.entity.UserCoupon;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.from.user_coupon.SearchUserCouponFrom;
import com.smartwash.mapper.CouponMapper;
import com.smartwash.mapper.OrdersMapper;
import com.smartwash.mapper.UserCouponMapper;
import com.smartwash.service.IUserCouponService;
import com.smartwash.vo.user_coupon.UserCouponVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {
    private final CouponMapper couponMapper;
    private final OrdersMapper ordersMapper;

    @Override
    public Page<UserCouponVo> getAllUserCoupon(SearchUserCouponFrom couponFrom) {
        Page<UserCouponVo> page = new Page<>(couponFrom.getPage(), couponFrom.getSize());
        return baseMapper.searchUserCoupon(page, couponFrom);
    }

    @Override
    @CacheEvict(value = "coupon", allEntries = true)
    public Boolean deleteCoupon(String ids) {
        log.info("删除用户优惠券, ids: {}", ids);
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(Long::valueOf)
                .collect(Collectors.toList());
        return removeByIds(idList);
    }

    @Override
    public List<UserCouponVo> getUserCouponByStatus(String status, Long userId, Integer page, Integer pageSize) {
        Page<UserCouponVo> pageObj = new Page<>(page, pageSize);
        return baseMapper.searchUserCouponByStatus(pageObj, status, userId);
    }

    //领取优惠券
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "coupon", key = "'valid:' + #userId")
    public Boolean receiveCoupon(Long couponId, Long userId) {
        //先校验优惠券存在性并读取有效期（读取操作，无并发问题）
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new CustomExceptions("优惠券不存在");
        }

        //原子递增已发放数量（total_limit 为 NULL 表示不限量），影响行数为 0 说明已领完，防止并发超发
        if (couponMapper.increaseIssuedCount(couponId) == 0) {
            log.info("优惠券已领完, couponId: {}, userId: {}", couponId, userId);
            throw new CustomExceptions("优惠券已领完");
        }

        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setCouponId(couponId);
        userCoupon.setUserId(userId);
        //设置优惠券过期时间
        LocalDateTime expiredTime = LocalDateTime.now().plusDays(coupon.getValidDays());
        userCoupon.setExpiredAt(expiredTime);
        try {
            boolean result = save(userCoupon);
            log.info("用户领取优惠券成功, userId: {}, couponId: {}", userId, couponId);
            return result;
        } catch (DuplicateKeyException e) {
            //唯一索引 uk_user_coupon (user_id, coupon_id) 兜底并发重复领取；
            //此处抛出异常使整个事务回滚，上面递增的 issued_count 一并回滚，不残留脏数据
            log.warn("用户重复领取优惠券, userId: {}, couponId: {}", userId, couponId);
            throw new CustomExceptions("该优惠券已领取！");
        }
    }

    @Override
    public List<UserCouponVo> getCanUseCoupon(Long userId, Long orderId) {
        Orders orders = ordersMapper.selectById(orderId);
        if (orders == null) {
            throw new CustomExceptions("订单不存在");
        }
        // 订单归属校验：userId 由 Controller 从登录上下文取得，禁止探测他人订单的金额与优惠券（评审报告后端 #13）
        if (!Objects.equals(orders.getUserId(), userId)) {
            log.warn("获取可用优惠券被拒绝：订单不属于当前用户, orderId: {}, userId: {}", orderId, userId);
            throw new CustomExceptions("无权查看该订单优惠券");
        }
        return baseMapper.getCanUseCoupon(userId, orders.getTotalPrice());
    }

    @Override
    public List<UserCouponVo> getAllUserCoupons(Long userId) {
        return baseMapper.getAllUserCoupons(userId);
    }
}
