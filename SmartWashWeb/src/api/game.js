// src/api/game.js
// 游戏库管理 API 层（方案 05 §五契约：/admin/game/**，ROLE_ADMIN）。
// 注意：后端 GameAdminController 可能仍在实现中，字段名/路径以方案 §五 为准，
// 待后端交付 docs/game-api.md 后回核（契约偏差见方案 §八回写约定）。
import request from '@/utils/http';

// 游戏分页列表：page/size + keyword（title 或 gameKey 模糊）/ category / enabled（精确，可空），
// 返回 MyBatis-Plus Page（records/total），管理视角含未启用，按 sort ASC, id ASC
export function getGameList(params) {
  return request({
    url: '/admin/game/list',
    method: 'get',
    params,
  });
}

// 上传游戏：multipart（file + 表单字段 title/gameKey/category/description/icon/color/badge/sort/remark）。
// 校验（.html/.zip 白名单、≤10MB、zip bomb 防护、ZipSlip）由后端负责，前端只做提交前预检；
// onUploadProgress 透传 axios 进度回调供上传对话框展示进度条。
// 成功返回 AdminGameVo（含 url 可玩地址），同 gameKey 重复上传为覆盖语义（方案 §四）
export function uploadGame(formData, onUploadProgress) {
  return request({
    url: '/admin/game/upload',
    method: 'post',
    data: formData,
    // axios 检测到 FormData 会自动补 multipart boundary，这里显式声明类型
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress,
  });
}

// 编辑游戏：id + title/description/icon/color/category/badge/sort/remark
// （gameKey/source_type/url/文件不在此接口修改范围，方案 §五）
export function updateGame(data) {
  return request({
    url: `/admin/game/${data.id}`,
    method: 'put',
    data,
  });
}

// 上下架开关（即「选择哪些下发」）：enabled 1 下发 0 停止，后端 CAS 更新
export function updateGameEnabled(id, enabled) {
  return request({
    url: `/admin/game/${id}/enabled/${enabled}`,
    method: 'post',
  });
}

// 删除游戏：物理删 + 服务器游戏文件（MinIO game/{gameKey}/ 前缀）一并清理
export function deleteGame(id) {
  return request({
    url: `/admin/game/${id}`,
    method: 'delete',
  });
}
