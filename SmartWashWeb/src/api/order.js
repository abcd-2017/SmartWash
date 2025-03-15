import request from '@/utils/http'

export function getOrderStatus() {
    return request({
        url: '/orders/status',
        method: 'get'
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '获取状态失败')
    })
}

export function getOrderList(params) {
    return request({
        url: '/orders/all',
        method: 'get',
        params
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '获取订单失败')
    })
}

export function deleteOrder(ids) {
    return request({
        url: `/orders/delete/${ids}`,
        method: 'delete'
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '删除失败')
    })
}