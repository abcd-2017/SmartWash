/**
 * 服务端公开配置（运行期拉取，对齐安卓 config/ServerConfig.kt + init/ServerConfigInitTask.kt）。
 *
 * 历史做法把 MinIO endpoint 编译进包体，缺点是域名变更必须重新发版。现改为启动时经
 * network/api/ServerConfigApi 拉 `GET /web/config` 写入本对象，供游戏 H5 桥白名单等
 * 运行期读取（游戏页由用户操作打开，此时配置早已就绪）。
 *
 * 失败静默：配置缺失时白名单只含 API host，属可接受降级（对齐安卓 ServerConfigInitTask）。
 */

import { logger } from '../utils/Logger'
import { getServerConfig, ServerConfigVo } from '../network/api/ServerConfigApi'

const TAG: string = 'ServerConfig'

class ServerConfigStore {
  /** MinIO 对外可访问端点（externalEndpoint 优先，缺失回退 endpoint；未就绪为空串） */
  public minioEndpoint: string = ''
  /** 短链对外跳转域名（后端拼 shortUrl 用；端上仅展示，不拼接） */
  public shortLinkBaseUrl: string = ''

  /** 启动预热（非阻塞 fire-and-forget）：成功写入，失败静默降级 */
  public prefetch(): void {
    getServerConfig()
      .then((res): void => {
        if (res.code === 200 && res.data !== null && res.data !== undefined) {
          const vo: ServerConfigVo = res.data
          if (vo.minioEndpoint !== null && vo.minioEndpoint !== undefined && vo.minioEndpoint.length > 0) {
            this.minioEndpoint = vo.minioEndpoint
          }
          if (vo.shortLinkBaseUrl !== null && vo.shortLinkBaseUrl !== undefined
            && vo.shortLinkBaseUrl.length > 0) {
            this.shortLinkBaseUrl = vo.shortLinkBaseUrl
          }
          // 就绪留痕（只记字段存在性，不记具体地址）：白名单排查时确认配置时机
          logger.debug(TAG, `server config prefetch ok (minioEndpoint=${vo.minioEndpoint !== null
            && vo.minioEndpoint !== undefined}, shortLinkBaseUrl=${vo.shortLinkBaseUrl !== null
            && vo.shortLinkBaseUrl !== undefined})`)
        } else {
          logger.warn(TAG, `server config prefetch code=${res.code}（降级：白名单仅含 API host）`)
        }
      })
      .catch((err: Error): void => {
        logger.warn(TAG, `server config prefetch failed（降级：白名单仅含 API host）err=${err.message}`)
      })
  }
}

/** 全局单例（ArkTS 无 object 声明，经类实例导出等价实现） */
export const ServerConfig: ServerConfigStore = new ServerConfigStore()
