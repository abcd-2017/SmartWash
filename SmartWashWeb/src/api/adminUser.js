import request from '@/utils/http'

export function getAdminUserList(params) {
    return request({
        url: '/adminUsers/all',
        method: 'get',
        params
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '获取管理员列表失败')
    })
}

export function addAdminUser(data) {
    return request({
        url: '/adminUsers/add',
        method: 'post',
        data
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '添加失败')
    })
}

export function updateAdminUser(data) {
    return request({
        url: '/adminUsers/update',
        method: 'post',
        data
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '更新失败')
    })
}

export function deleteAdminUser(ids) {
    return request({
        url: `/adminUsers/delete/${ids}`,
        method: 'delete'
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '删除失败')
    })
}