import request from '@/utils/http';

export function getLockerStatus() {
  return request({
    url: '/admin/lockers/status',
    method: 'get',
  });
}

export function getLockerList(params) {
  return request({
    url: '/admin/lockers/all',
    method: 'get',
    params,
  });
}

export function addLocker(data) {
  return request({
    url: '/admin/lockers/add',
    method: 'post',
    data,
  });
}

export function updateLocker(data) {
  return request({
    url: '/admin/lockers/update',
    method: 'post',
    data,
  });
}

export function deleteLocker(ids) {
  return request({
    url: `/admin/lockers/delete/${ids}`,
    method: 'delete',
  });
}
