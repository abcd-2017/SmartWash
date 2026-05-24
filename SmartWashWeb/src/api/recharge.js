import request from '@/utils/http'

export function getRechargeList(params) {
    return request({
        url: '/admin/rechargeRecords/all',
        method: 'get',
        params
    })
}
