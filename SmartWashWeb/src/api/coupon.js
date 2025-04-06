import request from '@/utils/http'

export function getCouponList(params) {
    return request({
        url: '/admin/coupon/all',
        method: 'get',
        params
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '获取优惠券失败')
    })
}

export function addCoupon(data) {
    return request({
        url: '/admin/coupon/add',
        method: 'post',
        data
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '添加失败')
    })
}

export function updateCoupon(data) {
    return request({
        url: '/admin/coupon/update',
        method: 'post',
        data
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '更新失败')
    })
}

export function deleteCoupon(ids) {
    return request({
        url: `/admin/coupon/delete/${ids}`,
        method: 'delete'
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '删除失败')
    })
}