import request from '@/utils/http';

// 系统参数配置（sys_config）：管理端实时修改业务参数，修改落库并立即生效
export function getSysConfigList() {
  return request({ url: '/admin/config/list', method: 'get' });
}

export function updateSysConfig(data) {
  return request({ url: '/admin/config/update', method: 'post', data });
}
