import request from '@/utils/http'

export function getLaundryList(params) {
    return request({
        url: '/admin/laundryItems/all',
        method: 'get',
        params
    })
}

export function addLaundry(data) {
    return request({
        url: '/admin/laundryItems/add',
        method: 'post',
        data
    })
}

export function updateLaundry(data) {
    return request({
        url: '/admin/laundryItems/update',
        method: 'post',
        data
    })
}

export function deleteLaundry(ids) {
    return request({
        url: `/admin/laundryItems/delete/${ids}`,
        method: 'delete'
    })
}
