import request from '@/utils/http'

export function getRoleList(params) {
    return request({
        url: '/admin/roles/all',
        method: 'get',
        params
    })
}

export function addRole(data) {
    return request({
        url: '/admin/roles/add',
        method: 'post',
        data
    })
}

export function updateRole(data) {
    return request({
        url: '/admin/roles/update',
        method: 'post',
        data
    })
}

export function deleteRole(ids) {
    return request({
        url: `/admin/roles/delete/${ids}`,
        method: 'delete'
    })
}
