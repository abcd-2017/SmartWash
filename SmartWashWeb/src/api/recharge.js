import request from '@/utils/http'

export function getRechargeList(params) {
    return request({
        url: '/rechargeRecords/all',
        method: 'get',
        params
    }).then(res => {
        if (res.code === 200) {
            return res.data
        } else {
            throw new Error(res.message || '获取充值记录失败')
        }
    })
}