import request from '@/utils/http'

export function getLaundryList(params) {
    return request({
        url: '/admin/laundryItems/all',
        method: 'get',
        params
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '获取套餐失败')
    })
}

export function addLaundry(data) {
    return request({
        url: '/admin/laundryItems/add',
        method: 'post',
        data
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '添加失败')
    })
}

export function updateLaundry(data) {
    return request({
        url: '/admin/laundryItems/update',
        method: 'post',
        data
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '更新失败')
    })
}

export function deleteLaundry(ids) {
    return request({
        url: `/admin/laundryItems/delete/${ids}`,
        method: 'delete'
    }).then(res => {
        if (res.code === 200) return res.data
        throw new Error(res.message || '删除失败')
    })
}