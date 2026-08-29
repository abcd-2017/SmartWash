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

// 注：后端批次三已摘除支付凭证删除入口（DELETE /admin/payments/delete/{ids}），
// 支付/充值凭证禁止物理删除，前端不再提供对应函数与按钮。

