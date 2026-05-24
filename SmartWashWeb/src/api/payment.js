import request from '@/utils/http'

export function getPayTypes() {
    return request({
        url: '/admin/payments/payType',
        method: 'get'
    })
}

export function getPayStatus() {
    return request({
        url: '/admin/payments/payStatus',
        method: 'get'
    })
}

export function getPaymentList(params) {
    return request({
        url: '/admin/payments/all',
        method: 'get',
        params
    })
}

export function deletePayment(ids) {
    return request({
        url: `/admin/payments/delete/${ids}`,
        method: 'delete'
    })
}
