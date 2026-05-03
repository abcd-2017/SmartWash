package com.smartwash.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.common.CouponStatus;
import com.smartwash.entity.Coupon;
import com.smartwash.from.coupon.AddCouponFrom;
import com.smartwash.from.coupon.SearchCouponFrom;
import com.smartwash.from.coupon.UpdateCouponFrom;
import com.smartwash.mapper.CouponMapper;
import com.smartwash.mapper.UserCouponMapper;
import com.smartwash.service.ICouponService;
import com.smartwash.vo.coupon.CouponVo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {
    private final UserCouponMapper userCouponMapper;

    @Override
    public Page<CouponVo> getAllCoupon(SearchCouponFrom searchCouponFrom) {
        Page<Coupon> page = new Page<>(searchCouponFrom.getPage(), searchCouponFrom.getSize());
        LambdaQueryWrapper<Coupon> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.and(searchCouponFrom.getCouponId() != null, b -> b.eq(Coupon::getCouponId, searchCouponFrom.getCouponId()));
        queryWrapper.and(StringUtils.hasText(searchCouponFrom.getTitle()), b -> b.like(Coupon::getTitle, searchCouponFrom.getTitle()));
        queryWrapper.and(StringUtils.hasText(searchCouponFrom.getStatus()), b -> b.eq(Coupon::getStatus, searchCouponFrom.getStatus()));

        List<Coupon> couponList = list(page, queryWrapper);
        Page<CouponVo> couponVoPage = new Page<>();
        BeanUtils.copyProperties(page, couponVoPage);
        couponVoPage.setRecords(couponList.stream().map(it -> {
            CouponVo couponVo = new CouponVo();

            BeanUtils.copyProperties(it, couponVo);
            return couponVo;
        }).toList());

        return couponVoPage;
    }

    @Override
    public void addCoupon(AddCouponFrom addCouponFrom) {
        Coupon coupon = new Coupon();
        BeanUtils.copyProperties(addCouponFrom, coupon);
        save(coupon);
    }

    @Override
    public Coupon getSearchByName(String title) {
        LambdaQueryWrapper<Coupon> queryWrapper = new LambdaQueryWrapper<Coupon>().eq(Coupon::getTitle, title);
        return this.getOne(queryWrapper);
    }

    @Override
    public void updateCoupon(UpdateCouponFrom couponFrom) {
        Coupon coupon = getById(couponFrom.getCouponId());
        BeanUtils.copyProperties(couponFrom, coupon);
        updateById(coupon);
    }

    @Override
    public Boolean deleteCoupon(String ids) {
        String[] idList = ids.split(",");
        for (String id : idList) {
            removeById(Integer.parseInt(id));
        }
        return true;
    }

    //获取所有可以领取的优惠券
    @Override
    public List<CouponVo> getAllValidCoupon(Long userId) {
        //查询所有可领取的优惠券
        LambdaQueryWrapper<Coupon> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Coupon::getStatus, CouponStatus.ACTIVE.getStatus());
        LocalDateTime nowTime = LocalDateTime.now();
        queryWrapper.and(q -> q.le(Coupon::getStartTime, nowTime));
        queryWrapper.and(q -> q.ge(Coupon::getEndTime, nowTime));
        List<Coupon> couponList = list(queryWrapper);

        //判断所有可以领取的优惠券，用户是否已经领取过
        List<Long> couponIds = userCouponMapper.getCouponIdsByUserId(userId);
        HashSet<Long> couponSet = new HashSet<>(couponIds);
        return couponList.stream().map(it -> {
            CouponVo couponVo = new CouponVo();
            BeanUtils.copyProperties(it, couponVo);
            if (couponSet.contains(it.getCouponId())) {
                couponVo.setStatus(String.valueOf(CouponStatus.RECEIVE.getStatus()));
            }
            return couponVo;
        }).toList();
    }
}
