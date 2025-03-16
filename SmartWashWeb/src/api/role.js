import request from '@/utils/http'

export function getRoleList(params) {
    return request({
        url: '/admin/roles/all',
        method: 'get',
        params
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '获取角色列表失败')
    })
}

export function addRole(data) {
    return request({
        url: '/admin/roles/add',
        method: 'post',
        data
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '添加失败')
    })
}

export function updateRole(data) {
    return request({
        url: '/admin/roles/update',
        method: 'post',
        data
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '更新失败')
    })
}

export function deleteRole(ids) {
    return request({
        url: `/admin/roles/delete/${ids}`,
        method: 'delete'
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '删除失败')
    })
}