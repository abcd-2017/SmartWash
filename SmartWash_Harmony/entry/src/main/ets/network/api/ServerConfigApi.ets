/**
 * 服务端公开配置接口（App 启动预热拉取，对齐安卓 network/api/ServerConfigApi.kt）。
 * 后端契约：WebConfigController#config，路径 /web/config（公开只读，SecurityConfig 已放行）。
 * 用途：MinIO 对外端点运行期获取（游戏 H5 桥白名单、封面直链域名），域名变更无需发版。
 */

import { BaseResponse } from '../BaseResponse'
import { httpUtil } from '../HttpUtil'

/** 服务端公开配置 VO（对齐后端 ServerConfigVo 与安卓 ServerConfigVo，字段名逐一勿改） */
export interface ServerConfigVo {
  /** MinIO 对外可访问端点（externalEndpoint 优先，缺失回退 endpoint） */
  minioEndpoint?: string | null
  /** 短链对外跳转域名（后端拼 shortUrl 用；端上仅展示，不拼接） */
  shortLinkBaseUrl?: string | null
}

/** 服务端公开配置（公开接口，无需登录态；请求拦截器不注入 Token 也照常放行） */
export async function getServerConfig(): Promise<BaseResponse<ServerConfigVo>> {
  return httpUtil.get<ServerConfigVo>('/web/config')
}
