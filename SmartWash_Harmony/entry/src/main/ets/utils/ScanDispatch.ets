// 全局扫一扫分发 — 系统默认扫码页识别 + 统一分发（对齐安卓壳层 ScanPage/ScanViewModel）
//
// 职责边界：只做「识别 + 分发」，自身不承接任何业务——
// 1. ScanKit `scanBarcode.startScanForResult` 拉起系统默认扫码页（系统页自带相机授权
//    与取景 UI，应用零权限声明、零自建相机界面；SDK d.ts 无 @permission 标注，对照
//    customScan 自定义界面才需 ohos.permission.CAMERA）；
// 2. 识别结果先经 dining 域归一（normalizeDiningScan：加入 URL 识别知识留域内），
//    命中加入链接转 smartwash:// 深链；
// 3. 其余按内容分发（handleDeepLink：smartwash:// 深链路由 / http(s) 内置浏览器），
//    非链接纯文本复制进剪贴板。
// 业务承接在各域深链注册表与壳层页面，本模块不感知任何业务。
//
// 扫码结果分发后不回退页面（系统扫码页自行关闭，入口页保持原地）。

import { common } from '@kit.AbilityKit'
import { BusinessError, pasteboard } from '@kit.BasicServicesKit'
import { scanBarcode, scanCore } from '@kit.ScanKit'
import { DiningScanKind, normalizeDiningScan } from '../feature/dining/DiningLogic'
import { handleDeepLink } from './deeplink/DeepLinkHandler'
import { logger } from './Logger'
import { resString } from './ResourceString'
import { showToastSafety } from './ToastUtil'

const TAG: string = 'ScanDispatch'

/** 深链 scheme 前缀（与 DeepLinkParser 内建判定同口径） */
const DEEP_LINK_SCHEME: string = 'smartwash://'

/** 扫码失败错误码：相机不可用/系统扫码服务缺失（模拟器典型表现） */
const ERR_CAMERA_UNAVAILABLE: number = 1000500001

/** 扫码失败错误码：用户取消（关闭系统扫码页/侧滑退出，官方建议静默处理） */
const ERR_USER_CANCELLED: number = 1000500002

/**
 * 拉起系统扫码并按内容分发（工作台常驻入口直调）。
 * 仅识别 QR 码（本应用场景全部为二维码；条形码误识会引入噪音）。
 * 全程不抛异常：识别失败/分发失败均 toast 提示。
 */
export function scanAndDispatch(context: common.Context): void {
  const options: scanBarcode.ScanOptions = {
    scanTypes: [scanCore.ScanType.QR_CODE],
    enableMultiMode: false
  }
  scanBarcode.startScanForResult(context, options).then((result: scanBarcode.ScanResult) => {
    dispatch(result.originalValue)
  }).catch((err: BusinessError) => {
    // 用户取消（关闭系统扫码页/侧滑）：静默，不提示错误（官方错误码处理建议）
    if (err.code === ERR_USER_CANCELLED) {
      return
    }
    logger.warn(TAG, `系统扫码失败 code=${err.code} message=${err.message}`)
    // 1000500001：相机/系统扫码服务不可用（模拟器典型表现，真机不出现）；
    // 其余（201 权限拒绝等）走通用失败文案。
    if (err.code === ERR_CAMERA_UNAVAILABLE) {
      showToastSafety(resString('scan_unavailable'))
    } else {
      showToastSafety(resString('scan_failed'))
    }
  })
}

/**
 * 识别结果分发（对齐安卓 ScanViewModel.normalize 的两步归一）：
 * 1. dining 域拦截（加入链接 → smartwash:// 深链 / 结构非法提示）；
 * 2. 通用分发（深链路由 / 内置浏览器 / 纯文本复制兜底）。
 */
function dispatch(raw: string): void {
  const value = raw === null ? '' : raw.trim()
  if (value.length === 0) {
    return
  }

  // 1. dining 域拦截：加入 URL 识别知识留域内，壳层零业务感知
  const dining = normalizeDiningScan(value)
  if (dining.kind === DiningScanKind.MALFORMED) {
    showToastSafety(resString('scan_invalid_link'))
    return
  }
  if (dining.kind === DiningScanKind.DEEP_LINK) {
    handleDeepLink(dining.deepLinkUri)
    return
  }

  // 2. 通用分发：链接交深链体系（smartwash:// 路由 / http(s) 内置浏览器），纯文本复制兜底
  const looksLikeLink = startsWithIgnoreCase(value, 'http://')
    || startsWithIgnoreCase(value, 'https://')
    || startsWithIgnoreCase(value, DEEP_LINK_SCHEME)
  if (looksLikeLink) {
    handleDeepLink(value)
    return
  }
  copyText(value)
}

/** 前缀匹配（大小写不敏感；扫码器可能改写 scheme 大小写） */
function startsWithIgnoreCase(value: string, prefix: string): boolean {
  return value.toLowerCase().startsWith(prefix.toLowerCase())
}

/** 纯文本复制进剪贴板（对齐点单分享页复制先例：pasteboard + toast） */
function copyText(text: string): void {
  try {
    const pasteData = pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN, text)
    pasteboard.getSystemPasteboard().setData(pasteData).then(() => {
      showToastSafety(resString('scan_copy_done'))
    }).catch((err: BusinessError) => {
      logger.warn(TAG, `剪贴板写入失败 code=${err.code}`)
      showToastSafety(resString('scan_copy_done'))
    })
  } catch (e) {
    logger.warn(TAG, `剪贴板写入异常: ${e}`)
  }
}
