import request from '@/utils/http'

export function getUserCouponList(params) {
    return request({
        url: '/admin/userCoupon/all',
        method: 'get',
        params
    })
}

export function deleteUserCoupon(ids) {
    return request({
        url: `/admin/userCoupon/delete/${ids}`,
        method: 'delete'
    })
}
