import request from '@/utils/http'

// 获取用户列表
export function getUserList(params) {
    return request({
        url: '/users/all',
        method: 'get',
        params
    }).then(res => {
        if (res.code === 200) {
            return res.data
        } else {
            throw new Error(res.message || '获取用户列表失败')
        }
    })
}

// 添加用户
export function addUser(data) {
    return request({
        url: '/users/add',
        method: 'post',
        data
    }).then(res => {
        if (res.code === 200) {
            return res.data
        } else {
            throw new Error(res.message || '添加用户失败')
        }
    })
}

// 更新用户
export function updateUser(data) {
    return request({
        url: '/users/update',
        method: 'post',
        data
    }).then(res => {
        if (res.code === 200) {
            return res.data
        } else {
            throw new Error(res.message || '更新用户失败')
        }
    })
}

// 删除用户
export function deleteUser(id) {
    return request({
        url: `/users/delete/${id}`,
        method: 'delete'
    }).then(res => {
        if (res.code === 200) {
            return res.data
        } else {
            throw new Error(res.message || '删除用户失败')
        }
    })
}