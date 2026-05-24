import request from '@/utils/http'

export function getAdminUserList(params) {
    return request({
        url: '/admin/adminUsers/all',
        method: 'get',
        params
    })
}

export function addAdminUser(data) {
    return request({
        url: '/admin/adminUsers/add',
        method: 'post',
        data
    })
}

export function updateAdminUser(data) {
    return request({
        url: '/admin/adminUsers/update',
        method: 'post',
        data
    })
}

export function deleteAdminUser(ids) {
    return request({
        url: `/admin/adminUsers/delete/${ids}`,
        method: 'delete'
    })
}

export function getCurrentAdminUser() {
    return request({
        url: '/admin/adminUsers/getAdminUserInfo',
        method: 'get'
    })
}
