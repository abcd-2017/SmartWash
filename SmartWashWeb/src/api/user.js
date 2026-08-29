import request from '@/utils/http';

export function getUserList(params) {
  return request({
    url: '/admin/users/all',
    method: 'get',
    params,
  });
}

export function addUser(data) {
  return request({
    url: '/admin/users/add',
    method: 'post',
    data,
  });
}

export function updateUser(data) {
  return request({
    url: '/admin/users/update',
    method: 'post',
    data,
  });
}

export function deleteUser(id) {
  return request({
    url: `/admin/users/delete/${id}`,
    method: 'delete',
  });
}
