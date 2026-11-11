import { AbilityConstant, Configuration, ConfigurationConstant, UIAbility, Want, errorManager } from '@kit.AbilityKit';
import { display, window, KeyboardAvoidMode } from '@kit.ArkUI';
import { logger } from '../utils/Logger';
import { initPreferences } from '../utils/StorageUtil';
import { initPickupReminder } from '../utils/ReminderService';
import { applyDesignTheme } from '../constant/DesignSystem';
import { DarkModeStore } from '../constant/DarkModeStore';
import { ServerConfig } from '../config/ServerConfig'

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

// 卡片 router 事件 params 中的跳转键：与 widget/pages/WidgetCard.ets postCardAction 的
// params 键逐字一致（跨进程唯一通道，sw = SmartWash 前缀，避开系统注入保留键）
const WIDGET_URI_KEY: string = 'swWidgetUri';
// 卡片跳转 uri 合法性：只接受本应用深链 scheme，杜绝卡片数据被篡改后拉起任意页面
const WIDGET_URI_SCHEME_PREFIX: string = 'smartwash://';

// 安全区 AppStorage key：页面根容器按这两个值做内容避让（vp），背景色/渐变自然延伸到系统栏
const SAFE_AREA_TOP_VP_KEY: string = 'safeAreaTopVp';
const SAFE_AREA_BOTTOM_VP_KEY: string = 'safeAreaBottomVp';

export default class EntryAbility extends UIAbility {
  onCreate(want: Want, launchParam: AbilityConstant.LaunchParam): void {
    // 全局兜底最先注册：让后续初始化阶段的未捕获异常也能留下 error 日志
    errorManager.on('error', new CrashLogObserver());
    // 深浅色跟随系统（问题17）：不再强制 COLOR_MODE_LIGHT。
    // 系统色模式检测值写入 AppStorage('darkMode')，DesignColor 的每个令牌据此返回深/浅值。
    this.syncDarkMode();
    // 冷启动深链：onCreate 先于 loadContent 执行，此处暂存 uri，待 Home 页挂载后消费
    this.savePendingDeepLink(want);
    // 桌面卡片 router 事件（postCardAction 拉起）：params 转深链 uri 走同一消费链路
    this.saveWidgetLaunch(want);
    // 服务端公开配置预热（非阻塞，对齐安卓 ServerConfigInitTask）：游戏桥白名单运行期读取
    ServerConfig.prefetch();
  }

  /**
   * 系统深浅色切换（问题17）：更新 AppStorage('darkMode')。
   * 未声明 configChanges，系统色模式变更会重建 Ability，冷启动会再读一次；
   * 这里同步更新，覆盖不重建时的实时切换。
   * 注意：必须使用回调参数 newConfig（最新配置）——this.context.config 在回调时机
   * 仍是上一次的值（更新滞后），直接用会导致「永远拿上一步的值」（切换滞后一拍根因）。
   */
  onConfigurationUpdate(newConfig: Configuration): void {
    this.syncDarkMode(newConfig);
  }

  /**
   * 读取系统色模式（-1 未指定 / 0 亮 / 1 暗）：写入 AppStorage('darkMode') 与
   * DarkModeStore 单例（V2 组件消费链，见 constant/DarkModeStore.ets），并调用
   * applyDesignTheme 刷新 DesignColor 的全部可变令牌值。
   * @param config 优先用调用方传入的 Configuration（onConfigurationUpdate 的 newConfig
   *               是最新值）；缺省时回落 this.context.config（onCreate 路径，冷启动最新）
   * 健壮性：config 缺失或 colorMode 取值意外（-1 未指定等）一律按 LIGHT 兜底，不崩溃；
   * 冷启动（onCreate）与实时切换（onConfigurationUpdate）两条路径均经此函数收敛。
   */
  private syncDarkMode(config?: Configuration): void {
    try {
      const cfg: Configuration | undefined = config ?? this.context.config;
      // 仅精确匹配 DARK 判定为暗色：未指定(-1)/意外取值/undefined 全部落 LIGHT
      const dark: boolean = cfg !== undefined &&
        cfg.colorMode === ConfigurationConstant.ColorMode.COLOR_MODE_DARK;
      AppStorage.setOrCreate('darkMode', dark);
      // V2 消费链双写：@Trace 赋值驱动 ThemeRefreshV2 等订阅方刷新
      DarkModeStore.getInstance().dark = dark;
      applyDesignTheme(dark);
    } catch (err) {
      logger.error('EntryAbility', '读取系统色模式失败', err instanceof Error ? err.message : '');
    }
  }

  // 热启动深链：应用已在运行（如浏览器拉起），复用既有实例走 onNewWant
  // （对齐安卓 singleTask + onNewIntent 语义），同样暂存由 Home 页 @Watch 感知消费
  onNewWant(want: Want, launchParam: AbilityConstant.LaunchParam): void {
    this.savePendingDeepLink(want);
    this.saveWidgetLaunch(want);
  }

  onWindowStageCreate(windowStage: window.WindowStage): void {
    initPreferences(this.context)
    // 取件提醒文案资源注入（fire-and-forget：失败回落 ReminderLogic 内置兜底文案）
    initPickupReminder(this.context)

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
   * 桌面卡片点击落地（postCardAction router 事件）：把卡片 params 里的跳转 uri 暂存到
   * pendingDeepLinkUri，与深链共用同一条消费链路（Index 登出态消费 / Home 挂载与热启动
   * @Watch 消费 → handleDeepLink 分发）——登录守卫（P-2001：未登录点卡片保留暂存、
   * 登录后补消费）与路由注册全部复用，不新增一套导航语义。
   *
   * 拉起时机：应用未运行 → onCreate；后台运行 → onNewWant，两处均调用本方法。
   * params 位置存在两种系统形态（WisdomDaily 实测兼容，此处同样兼容）：
   * ① want.parameters 直接是 params 键值对；
   * ② want.parameters.params 内嵌 params（JSON 字符串或对象）。
   */
  private saveWidgetLaunch(want: Want): void {
    try {
      const parameters = want.parameters;
      if (parameters === undefined) {
        return;
      }
      const bag = EntryAbility.unwrapWidgetParams(parameters);
      if (bag === undefined) {
        return;
      }
      const raw: Object = bag[WIDGET_URI_KEY];
      if (typeof raw !== 'string') {
        return;
      }
      const uri: string = raw;
      // scheme 白名单：仅本应用深链可入暂存（卡片数据键值可被系统重建，防御性收口）
      if (!uri.toLowerCase().startsWith(WIDGET_URI_SCHEME_PREFIX)) {
        return;
      }
      AppStorage.setOrCreate(PENDING_DEEP_LINK_KEY, uri);
      // debug 留痕不打 uri 全文（与深链日志口径一致，host/path 细节由 deeplink 模块按需记录）
      logger.debug('EntryAbility', '洗衣卡片跳转请求已暂存，待深链链路消费');
    } catch (err) {
      // 卡片参数解析永不击穿启动流程：异常仅留摘要日志
      logger.error('EntryAbility', '卡片跳转参数解析失败',
        err instanceof Error ? err.message : '');
    }
  }

  /** 兼容两种 want.parameters 形态：直接键值对 / parameters.params 内嵌（JSON 串或对象） */
  private static unwrapWidgetParams(parameters: Record<string, Object>): Record<string, Object> | undefined {
    const nested: Object | undefined = parameters['params'];
    if (nested === undefined) {
      return parameters;
    }
    if (typeof nested === 'string') {
      const parsed: Object = JSON.parse(nested);
      return parsed instanceof Object ? parsed as Record<string, Object> : undefined;
    }
    if (nested instanceof Object) {
      return nested as Record<string, Object>;
    }
    return undefined;
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
