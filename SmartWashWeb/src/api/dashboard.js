import request from '@/utils/http'

// 获取工作台统计数据
export function getDashboardStats() {
    return request({
        url: '/admin/dashboard/stats',
        method: 'get'
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '获取统计数据失败')
    })
}