import request from '@/utils/http'

export function login(data) {
    return request({
        url: '/auth/adminUsers/login',
        method: 'post',
        data
    })
}