import { hilog } from '@kit.PerformanceAnalysisKit';
import BuildProfile from 'entry/BuildProfile';

const DOMAIN: number = 0x1234;

// TAG 全局前缀：调用方只传页面/模块名，此处统一拼接，与 Android 端 AppLogger
// 的 "SmartWash/" 内核前缀对齐（最终输出形如 SmartWash/axios）
const TAG_PREFIX: string = 'SmartWash/';

// debug 级开关：release 构建（BUILD_MODE_NAME 为 hvigor 生成字段，非 'debug' 即 release）
// 直接短路，info/warn/error 全模式保留（与 Android LogCore 门控的差异：鸿蒙 info 不静默）
const IS_DEBUG_BUILD: boolean = BuildProfile.BUILD_MODE_NAME === 'debug';

/**
 * 统一日志封装（API 形状对齐 Android 端 AppLogger 的 (tag, msg, throwable?) 四方法）：
 * - tag 传页面/模块名（如 'index'、'axios'、'deeplink'），输出统一带 SmartWash/ 前缀；
 * - debug 在 release 构建直接短路；
 * - 鸿蒙无 Throwable 概念，error 的第三参为调用方摘要后的错误描述字符串
 *   （如 error.message），禁止序列化完整 error 对象（敏感红线）。
 */
class Logger {
  private readonly domain: number;

  constructor(domain: number = DOMAIN) {
    this.domain = domain;
  }

  private buildTag(tag: string): string {
    return TAG_PREFIX + tag;
  }

  debug(tag: string, msg: string): void {
    if (!IS_DEBUG_BUILD) {
      return;
    }
    hilog.debug(this.domain, this.buildTag(tag), '%{public}s', msg);
  }

  info(tag: string, msg: string): void {
    hilog.info(this.domain, this.buildTag(tag), '%{public}s', msg);
  }

  warn(tag: string, msg: string): void {
    hilog.warn(this.domain, this.buildTag(tag), '%{public}s', msg);
  }

  error(tag: string, msg: string, errorSummary?: string): void {
    // 第三参省略/空串时只输出 msg；有摘要时拼接输出，不落完整异常对象
    const line: string = errorSummary === undefined || errorSummary === "" ? msg : `${msg}, ${errorSummary}`;
    hilog.error(this.domain, this.buildTag(tag), '%{public}s', line);
  }
}

export const logger: Logger = new Logger()