import request from '@/utils/http'

export function getPayTypes() {
    return request({
        url: '/admin/payments/payType',
        method: 'get'
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '获取支付类型失败')
    })
}

export function getPayStatus() {
    return request({
        url: '/admin/payments/payStatus',
        method: 'get'
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '获取支付状态失败')
    })
}

export function getPaymentList(params) {
    return request({
        url: '/admin/payments/all',
        method: 'get',
        params
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '获取支付记录失败')
    })
}

export function deletePayment(ids) {
    return request({
        url: `/admin/payments/delete/${ids}`,
        method: 'delete'
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '删除失败')
    })
}