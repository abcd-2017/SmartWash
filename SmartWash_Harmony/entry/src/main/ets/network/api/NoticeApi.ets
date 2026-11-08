// 站内信域用户端接口（契约源：后端 notice/controller/WebNoticeController，需 ROLE_USER）
//   GET  /web/auth/notices?page&size        我的收件箱（全量公告分页，每条带当前用户 isRead）
//   GET  /web/auth/notices/unread-count     未读数（公告总数 − 当前用户已读数）
//   POST /web/auth/notices/{id}/read        标记已读（幂等）
//   POST /web/auth/notices/read-all         全部已读（幂等，返回本次新增条数）
//
// 与安卓 feature/notice/api/NoticeServiceApi.kt 逐接口对齐。
// 未读数/已读数为次要点缀数据：data 缺失或类型漂移一律按 0 处理（角标隐藏），
// 不让点缀请求把页面拖进错误态（对齐安卓 NoticeRepository 口径）。

import { BaseResponse } from '../BaseResponse'
import { httpUtil } from '../HttpUtil'
import { NoticePageData, NoticeVo, noticePageDataFromJSON, noticeVoFromJSON } from '../vo/NoticeVo'

/** 收件箱分页大小（后端 BaseSearchFrom 默认 10、上限 100；公告量级小取 20 平衡首屏与请求数） */
export const NOTICE_PAGE_SIZE: number = 20

/** 我的收件箱（page 从 1 起，最新在前）；records 每条带当前用户 isRead */
export async function getNotices(page: number, size: number): Promise<BaseResponse<NoticePageData<NoticeVo>>> {
  const params: Record<string, Object> = {}
  params['page'] = page
  params['size'] = size
  const res = await httpUtil.get<Object>('/web/auth/notices', params)
  return {
    data: res.data === null ? null : noticePageDataFromJSON<NoticeVo>(res.data, noticeVoFromJSON),
    code: res.code,
    message: res.message
  }
}

/**
 * 未读数（公告总数 − 当前用户已读数）。
 * data 缺失/类型漂移按 0 处理（角标隐藏），仅留痕不上抛。
 */
export async function getUnreadCount(): Promise<BaseResponse<number>> {
  const res = await httpUtil.get<Object>('/web/auth/notices/unread-count')
  const value: number = typeof res.data === 'number' ? res.data : 0
  return { data: value, code: res.code, message: res.message }
}

/** 标记已读（后端幂等：重复标记静默成功；data 恒为 null，不读 data） */
export async function markNoticeRead(id: number): Promise<BaseResponse<null>> {
  return httpUtil.post<null>(`/web/auth/notices/${id}/read`)
}

/** 全部已读（后端幂等：重复键吞掉；返回本次新增条数，data 缺失/漂移按 0） */
export async function readAllNotices(): Promise<BaseResponse<number>> {
  const res = await httpUtil.post<Object>('/web/auth/notices/read-all')
  const value: number = typeof res.data === 'number' ? res.data : 0
  return { data: value, code: res.code, message: res.message }
}
