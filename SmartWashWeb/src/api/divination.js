// src/api/divination.js
// 观象台（占卜模块）管理端 API 层
// 接口路径与后端 DivinationAdminController 完全匹配（/admin/div/**，ROLE_ADMIN）
import request from '@/utils/http';

// ---------- Prompt 管理 ----------
export function getPromptList(params) {
  return request({
    url: '/admin/div/prompts',
    method: 'get',
    params,
  });
}

export function createPrompt(data) {
  return request({
    url: '/admin/div/prompts',
    method: 'post',
    data,
  });
}

export function updatePrompt(id, data) {
  return request({
    url: `/admin/div/prompts/${id}`,
    method: 'put',
    data,
  });
}

export function activatePrompt(id) {
  return request({
    url: `/admin/div/prompts/${id}/activate`,
    method: 'post',
  });
}

// ---------- 语料管理（RAG） ----------
export function getRagDocumentList(params) {
  return request({
    url: '/admin/div/rag/documents',
    method: 'get',
    params,
  });
}

export function uploadRagDocument(data) {
  return request({
    url: '/admin/div/rag/documents',
    method: 'post',
    data,
  });
}

// ---------- 审计复审 ----------
export function getAuditList(params) {
  return request({
    url: '/admin/div/audits',
    method: 'get',
    params,
  });
}

// ---------- 用量看板 ----------
export function getUsageStats(params) {
  return request({
    url: '/admin/div/usage',
    method: 'get',
    params,
  });
}

// ---------- 拦截日志 ----------
export function getBlockedList(params) {
  return request({
    url: '/admin/div/blocked',
    method: 'get',
    params,
  });
}

// ---------- 模型管理 ----------
export function getModelList(params) {
  return request({
    url: '/admin/div/models',
    method: 'get',
    params,
  });
}

export function createModel(data) {
  return request({
    url: '/admin/div/models',
    method: 'post',
    data,
  });
}

export function updateModel(id, data) {
  return request({
    url: `/admin/div/models/${id}`,
    method: 'put',
    data,
  });
}

export function deleteModel(id) {
  return request({
    url: `/admin/div/models/${id}`,
    method: 'delete',
  });
}

export function testModelConnectivity(id) {
  return request({
    url: `/admin/div/models/${id}/test`,
    method: 'post',
  });
}

// ---------- 平台设置 ----------
export function getPlatformSettings() {
  return request({
    url: '/admin/div/settings',
    method: 'get',
  });
}

export function updatePlatformSettings(data) {
  return request({
    url: '/admin/div/settings',
    method: 'put',
    data,
  });
}

export function rotateMasterKey() {
  return request({
    url: '/admin/div/settings/rotate-key',
    method: 'post',
  });
}
