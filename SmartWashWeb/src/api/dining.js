// src/api/dining.js
// 家庭点单（dining）API 层
// 接口已与后端 DiningCategoryAdminController / DiningRecipeAdminController 对齐
// （/admin/dining/**，ROLE_ADMIN）：字段名以 DiningCategoryVo / DiningRecipeVo
// 及 Add·Update{DiningCategory,DiningRecipe}From 为准（驼峰）。
// 点单订单为用户端接口（/web/auth/dining/orders/**），管理后台不消费，故无对应封装。
import request from '@/utils/http';

// 菜谱分类分页列表：page/size + name（模糊），返回 MyBatis-Plus Page（records/total），按 sort/id 升序
export function getDiningCategoryList(params) {
  return request({
    url: '/admin/dining/categories/all',
    method: 'get',
    params,
  });
}

// 新增菜谱分类：name/sort（name 全局唯一，重复时后端拒绝并提示）
export function addDiningCategory(data) {
  return request({
    url: '/admin/dining/categories/add',
    method: 'post',
    data,
  });
}

// 更新菜谱分类：id + name/sort（字段不传即不修改，改名与其他分类重名时后端拒绝）
export function updateDiningCategory(data) {
  return request({
    url: '/admin/dining/categories/update',
    method: 'post',
    data,
  });
}

// 删除菜谱分类：ids 逗号分隔（如 1,2,3），批量物理删除
export function deleteDiningCategory(ids) {
  return request({
    url: `/admin/dining/categories/delete/${ids}`,
    method: 'delete',
  });
}

// ==================== 菜谱管理（/admin/dining/recipes，02 号票） ====================

// 菜谱分页列表：page/size + categoryId（精确，可空）/status（1 上架 0 下架，可空）/keyword（菜名模糊），
// 返回 MyBatis-Plus Page（records/total），按 id 降序；records 内含 categoryName/coverUrl/ingredients
export function getDiningRecipeList(params) {
  return request({
    url: '/admin/dining/recipes/all',
    method: 'get',
    params,
  });
}

// 新增菜谱：name/categoryId（可空=未分类）/ingredients（食材名数组）/steps/coverObjectKey（可空）
// （后端默认上架并记录创建管理员；同名菜谱允许重复）
export function addDiningRecipe(data) {
  return request({
    url: '/admin/dining/recipes/add',
    method: 'post',
    data,
  });
}

// 更新菜谱：id + 同新增字段（字段不传即不修改；上下架走专用状态接口）
export function updateDiningRecipe(data) {
  return request({
    url: '/admin/dining/recipes/update',
    method: 'post',
    data,
  });
}

// 删除菜谱：ids 逗号分隔（如 1,2,3），批量物理删除（封面 MinIO 对象不随删回收）
export function deleteDiningRecipe(ids) {
  return request({
    url: `/admin/dining/recipes/delete/${ids}`,
    method: 'delete',
  });
}

// 菜谱上下架：id + status（1=上架 0=下架）；下架后不再出现在用户端上架列表，历史点单订单不受影响
export function changeDiningRecipeStatus(id, status) {
  return request({
    url: `/admin/dining/recipes/${id}/status/${status}`,
    method: 'post',
  });
}

// 上传菜谱封面：multipart（JPG/PNG/WebP，魔数+扩展名校验，≤5MB），返回 dining/covers/ 前缀对象键，
// 对象键回填新增/修改接口的 coverObjectKey 字段（列表接口按对象键拼好 coverUrl 直链供回显）
export function uploadDiningRecipeCover(formData) {
  return request({
    url: '/admin/dining/recipes/cover',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

// ==================== 菜谱批量导入（03 号票，两阶段：预览不落库 → 确认整批事务） ====================

// 下载菜谱导入模板（xlsx 文件流）：与充值记录导出同模式，
// 响应为 blob 不走统一信封（http.js 已对 responseType='blob' 放行）；
// 后端错误分支以 JSON 信封返回，按 Content-Type 识别后转业务 Error
export async function downloadDiningRecipeImportTemplate() {
  const res = await request({
    url: '/admin/dining/recipes/import/template',
    method: 'get',
    responseType: 'blob',
  });
  const blob = res.data;
  if (blob && blob.type && blob.type.includes('json')) {
    const text = await blob.text();
    let envelope;
    try {
      envelope = JSON.parse(text);
    } catch {
      throw new Error('模板下载失败，请稍后重试');
    }
    throw new Error(envelope.message || '模板下载失败');
  }
  return blob;
}

// 上传导入文件解析预览（.xlsx/.csv，≤5MB、≤500 行）：multipart 上传，返回逐行校验结果
// { total, validCount, errorCount, rows: [{rowNo, categoryName, categoryId, name, ingredients, steps, coverUrl, errors}] }，
// 本阶段后端不落库，预览确认后才提交 confirmDiningRecipeImport
export function previewDiningRecipeImport(formData) {
  return request({
    url: '/admin/dining/recipes/import',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

// 确认导入：提交预览通过校验的行（归一后结构化数据），整批事务——任一行失败全批不落库
// 并返回逐行失败报告（rolledBack=true）；全部通过返回逐行菜谱 ID
export function confirmDiningRecipeImport(data) {
  return request({
    url: '/admin/dining/recipes/import/confirm',
    method: 'post',
    data,
  });
}
