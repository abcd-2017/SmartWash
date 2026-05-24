import request from '@/utils/http'

export function getSchoolList(params) {
    return request({
        url: '/admin/schools/all',
        method: 'get',
        params
    })
}

export function addSchool(data) {
    return request({
        url: '/admin/schools/add',
        method: 'post',
        data
    })
}

export function updateSchool(data) {
    return request({
        url: '/admin/schools/update',
        method: 'post',
        data
    })
}

export function deleteSchool(id) {
    return request({
        url: `/admin/schools/delete/${id}`,
        method: 'delete'
    })
}
