import { IndexPage } from "../view/IndexPage"
import { Service } from "../view/Service"
import { UserInfo } from "../view/UserInfo"
import { Workbench } from "../feature/workbench/Workbench"
import { BottomNav, BottomNavTab } from "../view/BottomNavBar"

import { safeBottomVp } from "../utils/SafeArea"
import { handleDeepLink } from "../utils/deeplink/DeepLinkHandler"
import { logger } from "../utils/Logger"
import { bumpOrderRefresh, bumpUserInfoRefresh, HOME_TAB_BACK_TICK_KEY, ORDER_TICK_KEY, USER_INFO_TICK_KEY } from "../utils/RefreshSignal"
import vibrator from '@ohos.vibrator'
import { ThemeRefresh } from '../view/common/ThemeRefresh'
import { resColor } from '../utils/ResourceColor'
import { resString } from '../utils/ResourceString'

@Builder
export function HomeBuilder() {
  Home()
}

/** 首页当前 tab 下标（Home 写，Index.onBackPress 读）：系统返回键回首页 tab 的判定依据 */
export const HOME_TAB_INDEX_KEY: string = 'homeTabIndex'

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

  // ========== 返回刷新双保险（问题3）==========
  // 单靠 NavDestination.onShown 不可靠（根内容返回时不一定回调）；充值页 aboutToDisappear
  // 等会 bump 自增 tick —— 这里直接监听两个 tick，变化即抬 refreshTick，
  // IndexPage 的 @Monitor 随即拉取最新账户数据。
  // 注意必须用自增 tick：布尔标记重复 setOrCreate(true) 不产生变更事件，@Watch 不触发。
  @StorageLink(USER_INFO_TICK_KEY) @Watch('onUserInfoTick') userInfoTick: number = 0
  @StorageLink(ORDER_TICK_KEY) @Watch('onOrderTick') orderTick: number = 0
  // 系统返回键切回首页 tab 信号：Index.onBackPress 拦截后 bump（见 RefreshSignal 注释）
  @StorageLink(HOME_TAB_BACK_TICK_KEY) @Watch('onHomeTabBackTick') homeTabBackTick: number = 0

  aboutToAppear(): void {
    // 同步初始 tab 下标（0），防止上一次会话的残留值污染 Index.onBackPress 的判定
    AppStorage.setOrCreate(HOME_TAB_INDEX_KEY, 0)
    this.consumePendingDeepLink()
  }

  /** @Watch 回调：热启动 onNewWant 更新暂存时触发 */
  onPendingDeepLinkChange(): void {
    this.consumePendingDeepLink()
  }

  /** @Watch 回调：账户数据刷新 tick（充值/下单/取消等）→ 抬 refreshTick */
  onUserInfoTick(): void {
    if (this.userInfoTick > 0) {
      this.refreshTick++
    }
  }

  /** @Watch 回调：订单刷新 tick → 抬 refreshTick（IndexPage 统一刷新账户卡） */
  onOrderTick(): void {
    if (this.orderTick > 0) {
      this.refreshTick++
    }
  }

  /** @Watch 回调：非首页 tab 按系统返回键（Index.onBackPress 拦截）→ 切回首页 tab */
  onHomeTabBackTick(): void {
    if (this.homeTabBackTick > 0 && this.currentIndex !== 0) {
      this.currentIndex = 0
      this.controller.changeIndex(0)
    }
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

  // 内置底栏隐藏（barHeight 0）后的空 tabBar 占位；底栏由下方自定义 BottomNav 承载
  @Builder
  emptyTabBar() {
    Column()
  }

  // 底栏 tab 定义（对齐安卓 homeTabItems：线性/实心图标对 + 文案）
  private tabs: BottomNavTab[] = [
    { key: 'home', label: resString('tab_home'), selectedIcon: $r('sys.symbol.house_fill'), normalIcon: $r('sys.symbol.house') },
    {
      key: 'service', label: resString('tab_service'), selectedIcon: $r('sys.symbol.list_bullet_square_fill_1'),
      normalIcon: $r('sys.symbol.list_bullet_square')
    },
    {
      // 过渡图标：安卓自绘「三方块 + 一圆」，SDK 5.0.5 无 square_grid_2x2_fill，
      // 选中态由文字色/品牌色实心图标区分
      key: 'workbench', label: resString('tab_workbench'), selectedIcon: $r('sys.symbol.square_grid_2x2'),
      normalIcon: $r('sys.symbol.square_grid_2x2')
    },
    { key: 'mine', label: resString('tab_mine'), selectedIcon: $r('sys.symbol.person_fill'), normalIcon: $r('sys.symbol.person') }
  ]

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，子树内资源色直读点重读新值
      // （静态字段变更不驱动 UI 刷新，机制见 view/common/ThemeRefresh.ets）
      ThemeRefresh() {
      Column() {
        Tabs({ barPosition: BarPosition.End, controller: this.controller, index: this.currentIndex }) {
          TabContent() {
            Column() {
              IndexPage({
                isActive: this.currentIndex === 0,
                refreshTick: this.refreshTick
              })
            }.height("100%")
            .width("100%")
          }.tabBar(this.emptyTabBar)

          TabContent() {
            Service()
          }.tabBar(this.emptyTabBar)

          TabContent() {
            Workbench()
          }.tabBar(this.emptyTabBar)

          TabContent() {
            UserInfo()
          }.tabBar(this.emptyTabBar)
        }
        .onChange(i => {
          this.currentIndex = i
          // 同步当前 tab 下标供 Index.onBackPress 判定（系统返回键回首页 tab）
          AppStorage.setOrCreate(HOME_TAB_INDEX_KEY, i)
          // 滑动切 tab 落定触感（对齐安卓 LaunchedEffect(settledPage) → performHaptic(SELECTION)）
          try {
            vibrator.startVibration({ type: 'time', duration: 10 }, { id: 0, usage: 'unknown' })
          } catch (err) {
            // 忽略不支持触感的设备
          }
          if (i === 3) {
            bumpUserInfoRefresh()
            bumpOrderRefresh()
          }
        })
        // 底栏由下方自定义 BottomNav 承载，隐藏内置 bar（barHeight 0）
        .barOverlap(false)
        .barHeight(0)
        .backgroundColor($r('app.color.BACKGROUND'))
        .layoutWeight(1)

        // 底栏规范 §4.5：68vp 高、纯白底、0.5vp outline 顶部分隔线（对齐安卓 BottomBar）
        BottomNav({
          tabs: this.tabs,
          currentIndex: this.currentIndex,
          onItemClick: (index: number) => {
            if (this.currentIndex !== index) {
              this.controller.changeIndex(index)
            }
          }
        })
      }
      .width("100%")
      .height("100%")
      .backgroundColor($r('app.color.SURFACE'))
      }
    }
    .width("100%")
    .height("100%")
    // 沉浸式：底部手势条区域由底栏同色背景延伸填充，tab 内容与底栏仍在安全区内（问题5）
    .padding({ bottom: safeBottomVp() })
    .backgroundColor($r('app.color.SURFACE'))
    .hideTitleBar(true)
    // 从子页（充值/订单详情/优惠券等）返回时刷新首页账户卡余额与订单（问题9）
    .onShown(() => {
      this.refreshTick++
    })
    // 非首页 tab 的系统返回拦截不在本页：Home 是 Navigation 根内容而非栈成员，
    // NavDestination.onBackPressed 不会触发（API 17 实测按返回直接退后台），
    // 由 @Entry Index.onBackPress 统一拦截并经 homeTabBackTick 通知本页切回 tab 0
  }
}
