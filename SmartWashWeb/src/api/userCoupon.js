import request from '@/utils/http'

export function getUserCouponList(params) {
    return request({
        url: '/admin/userCoupon/all',
        method: 'get',
        params
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '获取记录失败')
    })
}

export function deleteUserCoupon(ids) {
    return request({
        url: `/admin/userCoupon/delete/${ids}`,
        method: 'delete'
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '删除失败')
    })
}