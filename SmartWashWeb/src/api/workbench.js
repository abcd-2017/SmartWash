// src/api/workbench.js
// 工作台入口配置 API 层
// 接口已与后端 WorkbenchEntryAdminController 对齐（/admin/workbench/entries，ROLE_ADMIN）：
// 字段名以 AdminWorkbenchEntryVo / Add·UpdateWorkbenchEntryFrom 为准（驼峰，enabled 为 1/0）。
import request from '@/utils/http';

// 工作台入口分页列表：page/size + title（模糊）/ enabled（1 启用 0 停用，可空），
// 返回 MyBatis-Plus Page（records/total），按 sort/id 升序
export function getWorkbenchEntryList(params) {
  return request({
    url: '/admin/workbench/entries/all',
    method: 'get',
    params,
  });
}

// 新增工作台入口：entryKey/title/description/icon/color/target/badge/enabled/sort/remark
// （entryKey 全局唯一且创建后不可修改；icon/color 走端上白名单，后端二次校验）
export function addWorkbenchEntry(data) {
  return request({
    url: '/admin/workbench/entries/add',
    method: 'post',
    data,
  });
}

// 更新工作台入口：id + 同新增字段（entryKey 不可修改，不传即不更新；空字符串=清空对应字段）
export function updateWorkbenchEntry(data) {
  return request({
    url: '/admin/workbench/entries/update',
    method: 'post',
    data,
  });
}

// 删除工作台入口：ids 逗号分隔（如 1,2,3），批量物理删除
export function deleteWorkbenchEntry(ids) {
  return request({
    url: `/admin/workbench/entries/delete/${ids}`,
    method: 'delete',
  });
}
