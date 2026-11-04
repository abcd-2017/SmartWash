import { IndexPage } from "../view/IndexPage"
import { Service } from "../view/Service"
import { UserInfo } from "../view/UserInfo"
import { Workbench } from "../feature/workbench/Workbench"
import { BottomNavItem } from "../view/BottomNavBar"
import { DesignColor } from "../constant/DesignSystem"
import { safeBottomVp } from "../utils/SafeArea"
import { handleDeepLink } from "../utils/deeplink/DeepLinkHandler"
import { logger } from "../utils/Logger"
import vibrator from '@ohos.vibrator'

@Builder
export function HomeBuilder() {
  Home()
}

@Component
struct Home {
  private controller: TabsController = new TabsController()
  @State currentIndex: number = 0

  // ========== 深链消费时序（对齐安卓 MainActivity → pendingDeepLinkUri → NavHost 就绪后消费） ==========
  // EntryAbility.onCreate/onNewWant 把 Want.uri 暂存到 AppStorage('pendingDeepLinkUri')，
  // 本页作为消费点：
  // - 冷启动：Index 分流到 Home 后 aboutToAppear 消费（此时 Navigation 已挂载完成——Home
  //   分支仅在启动 loading + token/学校信息异步判定之后渲染，不存在栈未就绪的时序）；
  // - 热启动：AppStorage 变更经 @StorageLink + @Watch 即时消费；
  // - 未登录（Home 未挂载）：暂存不丢，登录/完善学校信息后 Index 分流到 Home 时补消费。
  // 未登录放行（对齐安卓取舍）：消费点不做登录前置判断，需要登录的目标页由 401 统一兜底
  // （清 token → 清内存登录态 → replace 登录页）。
  @StorageLink('pendingDeepLinkUri') @Watch('onPendingDeepLinkChange') pendingDeepLinkUri: string = ''

  // 首页可见性刷新信号（问题9）：从充值/订单详情等子页返回时 NavDestination.onShown 触发，
  // 经 refreshTick 传给 IndexPage（V2 组件不能直接 @StorageLink 消费，由 V1 宿主桥接）
  @State refreshTick: number = 0

  aboutToAppear(): void {
    this.consumePendingDeepLink()
  }

  /** @Watch 回调：热启动 onNewWant 更新暂存时触发 */
  onPendingDeepLinkChange(): void {
    this.consumePendingDeepLink()
  }

  /** 消费暂存深链：先清空再分发（防重复消费；置空写回再次触发的 @Watch 读到空串直接返回） */
  private consumePendingDeepLink(): void {
    const uri = this.pendingDeepLinkUri
    if (uri.length === 0) {
      return
    }
    this.pendingDeepLinkUri = ''
    // 低频动作 debug 留痕：交由深链处理器分发（uri 内容不落本层日志，由 deeplink 模块按需记录摘要）
    logger.debug('home', '检测到待消费深链，交由处理器分发')
    // handleDeepLink 内部全量兜底不抛异常（网络字典失败静默），此处不 await
    handleDeepLink(uri)
  }

  @Builder
  tabBuilder(title: string, targetIndex: number, selectedImg: Resource, normalImg: Resource) {
    BottomNavItem({
      label: title,
      selectedIcon: selectedImg,
      normalIcon: normalImg,
      isSelected: this.currentIndex === targetIndex,
      onItemClick: () => {
        if (this.currentIndex !== targetIndex) {
          this.controller.changeIndex(targetIndex)
        }
      }
    })
  }

  build() {
    NavDestination() {
      Tabs({ barPosition: BarPosition.End, controller: this.controller, index: this.currentIndex }) {
        TabContent() {
          Column() {
            IndexPage({
              isActive: this.currentIndex === 0,
              refreshTick: this.refreshTick
            })
          }.height("100%")
          .width("100%")
        }.tabBar(this.tabBuilder("首页", 0, $r('sys.symbol.house_fill'), $r('sys.symbol.house')))

        TabContent() {
          Service()
        }.tabBar(this.tabBuilder("服务", 1, $r('sys.symbol.list_bullet_square_fill_1'),
          $r('sys.symbol.list_bullet_square')))

        TabContent() {
          Workbench()
        }.tabBar(this.tabBuilder("工作台", 2, $r('sys.symbol.square_grid_2x2'),
          // 过渡图标：安卓自绘「三方块 + 一圆」，SDK 5.0.5 无 square_grid_2x2_fill，
          // 选中态由文字色/品牌色实心图标区分
          $r('sys.symbol.square_grid_2x2')))

        TabContent() {
          UserInfo()
        }.tabBar(this.tabBuilder("我的", 3, $r('sys.symbol.person_fill'), $r('sys.symbol.person')))
      }
      .onChange(i => {
        this.currentIndex = i
        // 滑动切 tab 落定触感（对齐安卓 LaunchedEffect(settledPage) → performHaptic(SELECTION)）
        try {
          vibrator.startVibration({ type: 'time', duration: 10 }, { id: 0, usage: 'unknown' })
        } catch (err) {
          // 忽略不支持触感的设备
        }
        if (i === 3) {
          AppStorage.setOrCreate("refreshUserInfo", true)
          AppStorage.setOrCreate("refreshOrderCount", true)
        }
      })
      // 底栏规范 §4.5：68dp 高、纯白底、0.5dp outline 顶部分隔线（对齐安卓 BottomBar）
      .barOverlap(false)
      .barHeight(68)
      .backgroundColor(DesignColor.BACKGROUND)
      .barBackgroundColor(DesignColor.SURFACE)
      .divider({ strokeWidth: 0.5, color: DesignColor.OUTLINE })
      .layoutWeight(1)
    }
    .width("100%")
    .height("100%")
    // 沉浸式：底部手势条区域由底栏同色背景延伸填充，tab 内容与底栏仍在安全区内（问题5）
    .padding({ bottom: safeBottomVp() })
    .backgroundColor(DesignColor.SURFACE)
    .hideTitleBar(true)
    // 从子页（充值/订单详情/优惠券等）返回时刷新首页账户卡余额与订单（问题9）
    .onShown(() => {
      this.refreshTick++
    })
    // 非首页 tab 系统返回先回首页 tab（对齐安卓 BackHandler），首页则交还系统退出
    .onBackPressed(() => {
      if (this.currentIndex !== 0) {
        this.currentIndex = 0
        this.controller.changeIndex(0)
        return true
      }
      return false
    })
  }
}
