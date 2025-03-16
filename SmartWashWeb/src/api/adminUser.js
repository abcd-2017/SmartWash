import request from '@/utils/http'

export function getAdminUserList(params) {
    return request({
        url: '/admin/adminUsers/all',
        method: 'get',
        params
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '获取管理员列表失败')
    })
}

export function addAdminUser(data) {
    return request({
        url: '/admin/adminUsers/add',
        method: 'post',
        data
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '添加失败')
    })
}

export function updateAdminUser(data) {
    return request({
        url: '/admin/adminUsers/update',
        method: 'post',
        data
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '更新失败')
    })
}

export function deleteAdminUser(ids) {
    return request({
        url: `/admin/adminUsers/delete/${ids}`,
        method: 'delete'
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '删除失败')
    })
}

export function getCurrentAdminUser() {
    return request({
        url: '/admin/adminUsers/getAdminUserInfo',
        method: 'get'
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '获取当前用户信息失败')
    })
}