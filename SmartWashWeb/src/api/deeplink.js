// src/api/deeplink.js
// 跳转路由字典（Deeplink Route）API 层
// 接口已与后端 DeeplinkRouteAdminController 对齐（/admin/deeplink/routes，ROLE_ADMIN）：
// 字段名以 DeeplinkRouteVo / Add·UpdateDeeplinkRouteFrom 为准（驼峰，enabled/requireLogin 为 1/0）。
// 注意：列表 Vo 不含 createdAt/updatedAt 时间字段。
import request from '@/utils/http';

// 路由字典分页列表：page/size + host（模糊）/ enabled（1 启用 0 停用，可空），
// 返回 MyBatis-Plus Page（records/total），按 host 升序
export function getDeeplinkRouteList(params) {
  return request({
    url: '/admin/deeplink/routes/all',
    method: 'get',
    params,
  });
}

// 新增路由：host/name/platform/requireLogin/description/enabled
// （host 全局唯一且创建后不可修改；platform：1 Android 2 HarmonyOS 3 双端）
export function addDeeplinkRoute(data) {
  return request({
    url: '/admin/deeplink/routes/add',
    method: 'post',
    data,
  });
}

// 更新路由：id + name/platform/requireLogin/description/enabled（host 不可修改；空字符串=清空描述）
export function updateDeeplinkRoute(data) {
  return request({
    url: '/admin/deeplink/routes/update',
    method: 'post',
    data,
  });
}

// 删除路由：ids 逗号分隔（如 1,2,3），批量物理删除
export function deleteDeeplinkRoute(ids) {
  return request({
    url: `/admin/deeplink/routes/delete/${ids}`,
    method: 'delete',
  });
}
