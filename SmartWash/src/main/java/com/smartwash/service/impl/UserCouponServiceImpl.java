package com.smartwash.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

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
    public Boolean deleteCoupon(String ids) {
        String[] idList = ids.split(",");
        for (String id : idList) {
            removeById(Integer.parseInt(id));
        }
        return true;
    }

    @Override
    public List<UserCouponVo> getUserCouponByStatus(String status, Long userId, Integer pageSize, Integer size) {
        Page<UserCouponVo> page = new Page<>(pageSize, size);
        return baseMapper.searchUserCouponByStatus(page, status, userId);
    }

    //领取优惠券
    @Override
    public Boolean receiveCoupon(Long couponId, Long userId) {
        //判断当前优惠券用户是否已经领取
        LambdaQueryWrapper<UserCoupon> queryWrapper = new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getCouponId, couponId)
                .and(q -> q.eq(UserCoupon::getUserId, userId));
        UserCoupon userCoupon = baseMapper.selectOne(queryWrapper);
        if (userCoupon != null) {
            throw new CustomExceptions("该优惠券已领取！");
        }

        //让当前用户领取到优惠券
        Coupon coupon = couponMapper.selectById(couponId);
        userCoupon = new UserCoupon();
        userCoupon.setCouponId(couponId);
        userCoupon.setUserId(userId);
        //设置优惠券过期时间
        LocalDateTime expiredTime = LocalDateTime.now().plusDays(coupon.getValidDays());
        userCoupon.setExpiredAt(expiredTime);
        return save(userCoupon);
    }

    @Override
    public List<UserCouponVo> getCanUseCoupon(Long userId, Long orderId) {
        Orders orders = ordersMapper.selectById(orderId);
        return baseMapper.getCanUseCoupon(userId,orders.getTotalPrice().floatValue());
    }
}
