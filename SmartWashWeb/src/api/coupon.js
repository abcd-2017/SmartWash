import request from '@/utils/http'

export function getCouponList(params) {
    return request({
        url: '/admin/coupon/all',
        method: 'get',
        params
    })
}

export function addCoupon(data) {
    return request({
        url: '/admin/coupon/add',
        method: 'post',
        data
    })
}

export function updateCoupon(data) {
    return request({
        url: '/admin/coupon/update',
        method: 'post',
        data
    })
}

export function deleteCoupon(ids) {
    return request({
        url: `/admin/coupon/delete/${ids}`,
        method: 'delete'
    })
}
