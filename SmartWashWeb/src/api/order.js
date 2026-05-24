import request from '@/utils/http'

export function getOrderStatus() {
    return request({
        url: '/admin/orders/status',
        method: 'get'
    })
}

export function getOrderList(params) {
    return request({
        url: '/admin/orders/all',
        method: 'get',
        params
    })
}

export function deleteOrder(ids) {
    return request({
        url: `/admin/orders/delete/${ids}`,
        method: 'delete'
    })
}

export function updateOrderStatus(data) {
    return request({
        url: `/admin/orders/updateOrderStatus`,
        method: 'post',
        data
    })
}
