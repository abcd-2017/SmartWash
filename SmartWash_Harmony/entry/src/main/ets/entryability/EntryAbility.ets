import { AbilityConstant, ConfigurationConstant, UIAbility, Want } from '@kit.AbilityKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { window } from '@kit.ArkUI';
import { initPreferences, setValue } from '../utils/StorageUtil';

const DOMAIN = 0x0000;

// 深链暂存 key：Want.uri 经 AppStorage 过桥到 UI 侧（Home 页挂载后消费）
const PENDING_DEEP_LINK_KEY: string = 'pendingDeepLinkUri';

export default class EntryAbility extends UIAbility {
  onCreate(want: Want, launchParam: AbilityConstant.LaunchParam): void {
    // 决策 D1（差异分析 2026-10-27）：固定亮色模式。
    // DesignSystem.ets 仅有亮色令牌，此前跟随系统会出现"深色模式下界面仍为亮色配色"的不一致；
    // 待暗色主题整套 Dark* 令牌补齐后再切回 COLOR_MODE_NOT_SET 跟随系统。
    this.context.getApplicationContext().setColorMode(ConfigurationConstant.ColorMode.COLOR_MODE_LIGHT);
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onCreate');
    // 冷启动深链：onCreate 先于 loadContent 执行，此处暂存 uri，待 Home 页挂载后消费
    this.savePendingDeepLink(want);
  }

  onDestroy(): void {
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onDestroy');
  }

  // 热启动深链：应用已在运行（如浏览器拉起），复用既有实例走 onNewWant
  // （对齐安卓 singleTask + onNewIntent 语义），同样暂存由 Home 页 @Watch 感知消费
  onNewWant(want: Want, launchParam: AbilityConstant.LaunchParam): void {
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onNewWant');
    this.savePendingDeepLink(want);
  }

  onWindowStageCreate(windowStage: window.WindowStage): void {
    // Main window is created, set main page for this ability
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onWindowStageCreate');
    initPreferences(this.context)

    windowStage.loadContent('pages/Index', (err) => {
      if (err.code) {
        hilog.error(DOMAIN, 'testTag', 'Failed to load the content. Cause: %{public}s', JSON.stringify(err));
        return;
      }
      hilog.info(DOMAIN, 'testTag', 'Succeeded in loading the content.');
    });
  }

  onWindowStageDestroy(): void {
    // Main window is destroyed, release UI related resources
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onWindowStageDestroy');
  }

  onForeground(): void {
    // Ability has brought to foreground
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onForeground');
  }

  onBackground(): void {
    // Ability has back to background
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onBackground');
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
  }
}
