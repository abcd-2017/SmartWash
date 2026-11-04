import { AbilityConstant, ConfigurationConstant, UIAbility, Want, errorManager } from '@kit.AbilityKit';
import { window } from '@kit.ArkUI';
import { logger } from '../utils/Logger';
import { initPreferences, setValue } from '../utils/StorageUtil';

// 崩溃摘要最大长度：onUnhandledException 的 errMsg 含完整调用栈，截断防超长刷屏
const CRASH_SUMMARY_MAX_LEN: number = 300;

/**
 * 全局未捕获异常观测：errorManager.on('error') 的观测器。
 * ErrorObserver 在 SDK typings 中仅经 errorManager 模块 type re-export（不可值导入），
 * 故按官方 API 9 样例写法本地定义同形状 class（onUnhandledException 为必选回调）传入。
 * - 只实现 onUnhandledException，不声明可选的 onException——
 *   两者对同一次异常都会被回调，避免同一次崩溃写两条日志；
 * - errMsg 为「异常名 + message + 调用栈」混合串：只取首行并截断作为摘要
 *   （即 name + message），不落完整调用栈——冗长且含本机路径信息；
 *   崩溃细节以系统 faultlogger 为准，此处仅留应用侧定位线索。
 */
class CrashLogObserver {
  onUnhandledException(errMsg: string): void {
    const firstLine: string = errMsg.split('\n')[0];
    const summary: string = firstLine.length > CRASH_SUMMARY_MAX_LEN
      ? firstLine.substring(0, CRASH_SUMMARY_MAX_LEN)
      : firstLine;
    logger.error('EntryAbility', '未捕获异常', summary);
  }
}

// 深链暂存 key：Want.uri 经 AppStorage 过桥到 UI 侧（Home 页挂载后消费）
const PENDING_DEEP_LINK_KEY: string = 'pendingDeepLinkUri';

export default class EntryAbility extends UIAbility {
  onCreate(want: Want, launchParam: AbilityConstant.LaunchParam): void {
    // 全局兜底最先注册：让后续初始化阶段的未捕获异常也能留下 error 日志
    errorManager.on('error', new CrashLogObserver());
    // 决策 D1（差异分析 2026-10-27）：固定亮色模式。
    // DesignSystem.ets 仅有亮色令牌，此前跟随系统会出现"深色模式下界面仍为亮色配色"的不一致；
    // 待暗色主题整套 Dark* 令牌补齐后再切回 COLOR_MODE_NOT_SET 跟随系统。
    this.context.getApplicationContext().setColorMode(ConfigurationConstant.ColorMode.COLOR_MODE_LIGHT);
    // 冷启动深链：onCreate 先于 loadContent 执行，此处暂存 uri，待 Home 页挂载后消费
    this.savePendingDeepLink(want);
  }

  // 热启动深链：应用已在运行（如浏览器拉起），复用既有实例走 onNewWant
  // （对齐安卓 singleTask + onNewIntent 语义），同样暂存由 Home 页 @Watch 感知消费
  onNewWant(want: Want, launchParam: AbilityConstant.LaunchParam): void {
    this.savePendingDeepLink(want);
  }

  onWindowStageCreate(windowStage: window.WindowStage): void {
    initPreferences(this.context)

    windowStage.loadContent('pages/Index', (err) => {
      if (err.code) {
        // 只记 code + message 摘要，不序列化完整 err 对象（敏感红线）
        logger.error('EntryAbility', '首页加载失败', `code=${err.code}, ${err.message}`);
        return;
      }
      // 低频关键动作成功：首页加载完成（冷启动必经一次）
      logger.info('EntryAbility', '首页加载完成');
    });
  }

  /**
   * 暂存深链 Want.uri 到 AppStorage（pendingDeepLinkUri）。
   * 消费时序（pages/Home.ets）：
   * - 冷启动：Index 分流到 Home 后，Home.aboutToAppear 读取暂存并消费；
   * - 热启动：Home 已挂载，AppStorage 变更经 @StorageLink + @Watch 即时触发消费；
   * - 未登录（Home 未挂载）：深链暂存不丢，登录后 Index 分流到 Home 时补消费。
   * 只接 scheme 为 smartwash 的 uri（module.json5 skills 声明的唯一外部入口 scheme，
   * 对齐安卓 intent-filter 只声明 scheme="smartwash"）；http(s) 深链不落地 Want，
   * 仅作为工作台下发的 target 在端内分发。
   */
  private savePendingDeepLink(want: Want): void {
    const uri = want.uri;
    if (uri === undefined || uri === null || uri.length === 0) {
      return;
    }
    if (!uri.toLowerCase().startsWith('smartwash://')) {
      return;
    }
    AppStorage.setOrCreate(PENDING_DEEP_LINK_KEY, uri);
    // 深链入口留痕（原 onNewWant 模板日志删除后补位）：debug 级，release 不输出；
    // 不打 uri 全文（query 可能携带业务参数）
    logger.debug('EntryAbility', 'smartwash 深链已暂存，待 Home 页消费');
  }
}
