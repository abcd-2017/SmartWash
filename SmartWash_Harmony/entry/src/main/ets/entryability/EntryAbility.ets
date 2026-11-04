import { AbilityConstant, ConfigurationConstant, UIAbility, Want, errorManager } from '@kit.AbilityKit';
import { display, window, KeyboardAvoidMode } from '@kit.ArkUI';
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

// 安全区 AppStorage key：页面根容器按这两个值做内容避让（vp），背景色/渐变自然延伸到系统栏
const SAFE_AREA_TOP_VP_KEY: string = 'safeAreaTopVp';
const SAFE_AREA_BOTTOM_VP_KEY: string = 'safeAreaBottomVp';

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
      this.setupImmersiveWindow(windowStage);
    });
  }

  /**
   * 沉浸式窗口基建（问题1/5/2 修复）：
   * - setWindowLayoutFullScreen(true)：窗口内容延伸到状态栏/导航条下方，页面背景色与渐变
   *   自然铺满全屏，消除状态栏/控制栏与页面的色差与割裂感；
   * - 各页面根容器按 AppStorage 的 safeAreaTopVp / safeAreaBottomVp 做 padding 避让内容
   *   （utils/SafeArea.ets 提供读取函数），背景层不避让；
   * - 键盘避让显式设为 RESIZE（对齐安卓默认 adjustResize）：键盘弹出压缩窗口高度，
   *   输入框所在滚动容器自动收进可视区，替代默认 OFFSET 的整页上推；
   * - 窗口尺寸变化（折叠/旋转）时重算安全区，保证避让值始终有效。
   */
  private setupImmersiveWindow(windowStage: window.WindowStage): void {
    let mainWindow: window.Window;
    try {
      mainWindow = windowStage.getMainWindowSync();
    } catch (err) {
      logger.error('EntryAbility', 'getMainWindowSync 失败', err instanceof Error ? err.message : '');
      return;
    }
    mainWindow.setWindowLayoutFullScreen(true).then(() => {
      this.updateSafeArea(mainWindow);
      // 尺寸/折叠态变化时重算安全区
      mainWindow.on('windowSizeChange', () => {
        this.updateSafeArea(mainWindow);
      });
    }).catch((err: Object) => {
      logger.error('EntryAbility', 'setWindowLayoutFullScreen 失败', err instanceof Error ? err.message : '');
    });
    // 键盘 RESIZE 避让：经窗口 UIContext 设置（API 11+）
    try {
      mainWindow.getUIContext().setKeyboardAvoidMode(KeyboardAvoidMode.RESIZE);
    } catch (err) {
      logger.error('EntryAbility', 'setKeyboardAvoidMode 失败', err instanceof Error ? err.message : '');
    }
  }

  /** 计算状态栏高度与底部导航指示器高度（px→vp）写入 AppStorage，供页面根容器避让 */
  private updateSafeArea(mainWindow: window.Window): void {
    try {
      const density = display.getDefaultDisplaySync().densityPixels;
      if (density <= 0) {
        return;
      }
      // 顶部：系统状态栏（TYPE_SYSTEM 的 topRect）
      const systemArea = mainWindow.getWindowAvoidArea(window.AvoidAreaType.TYPE_SYSTEM);
      // 底部：手势指示条（NEXT 设备统一手势导航形态）
      const indicatorArea = mainWindow.getWindowAvoidArea(window.AvoidAreaType.TYPE_NAVIGATION_INDICATOR);
      AppStorage.setOrCreate(SAFE_AREA_TOP_VP_KEY, Math.ceil(systemArea.topRect.height / density));
      AppStorage.setOrCreate(SAFE_AREA_BOTTOM_VP_KEY, Math.ceil(indicatorArea.bottomRect.height / density));
    } catch (err) {
      logger.error('EntryAbility', 'updateSafeArea 失败', err instanceof Error ? err.message : '');
    }
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
