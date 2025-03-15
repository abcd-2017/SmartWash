import request from '@/utils/http'

export function getLockerStatus() {
    return request({
        url: '/lockers/status',
        method: 'get'
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '获取状态失败')
    })
}

export function getLockerList(params) {
    return request({
        url: '/lockers/all',
        method: 'get',
        params
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '获取寄存柜失败')
    })
}

export function addLocker(data) {
    return request({
        url: '/lockers/add',
        method: 'post',
        data
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '添加失败')
    })
}

export function updateLocker(data) {
    return request({
        url: '/lockers/update',
        method: 'post',
        data
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '更新失败')
    })
}

export function deleteLocker(ids) {
    return request({
        url: `/lockers/delete/${ids}`,
        method: 'delete'
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '删除失败')
    })
}