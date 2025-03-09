import request from '@/utils/http'

// 获取学校列表
export function getSchoolList(params) {
    return request({
        url: '/schools/all',
        method: 'get',
        params
    }).then(res => {
        if (res.code === 200) {
            return res.data
        } else {
            throw new Error(res.message || '获取学校列表失败')
        }
    })
}

// 添加学校
export function addSchool(data) {
    return request({
        url: '/schools/add',
        method: 'post',
        data
    }).then(res => {
        if (res.code === 200) {
            return res.data
        } else {
            throw new Error(res.data || '添加学校失败')
        }
    })
}

// 更新学校
export function updateSchool(data) {
    return request({
        url: '/schools/update',
        method: 'post',
        data
    }).then(res => {
        if (res.code === 200) {
            return res.data
        } else {
            throw new Error(res.data || '更新学校失败')
        }
    })
}

// 删除学校
export function deleteSchool(id) {
    return request({
        url: `/schools/delete/${id}`,
        method: 'delete'
    }).then(res => {
        if (res.code === 200) {
            return res.data
        } else {
            throw new Error(res.data || '删除学校失败')
        }
    })
}