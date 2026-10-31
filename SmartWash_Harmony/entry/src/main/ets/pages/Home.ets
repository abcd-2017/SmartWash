import { IndexPage } from "../view/IndexPage"
import { Service } from "../view/Service"
import { UserInfo } from "../view/UserInfo"
import { Workbench } from "../feature/workbench/Workbench"
import { DesignColor } from "../constant/DesignSystem"
import { handleDeepLink } from "../utils/deeplink/DeepLinkHandler"

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
    // handleDeepLink 内部全量兜底不抛异常（网络字典失败静默），此处不 await
    handleDeepLink(uri)
  }

  @Builder
  tabBuilder(title: string, targetIndex: number, selectedImg: Resource, normalImg: Resource) {
    Column() {
      // 图标 24vp（对齐安卓 v0.4 bottomNav：icon.md 24dp）；未选中 TEXT_TERTIARY / 选中 PRIMARY
      SymbolGlyph(this.currentIndex === targetIndex ? selectedImg : normalImg)
        .fontSize(24)
        .fontColor([this.currentIndex === targetIndex ? DesignColor.PRIMARY : DesignColor.TEXT_TERTIARY])
        .margin({ top: 8 })
      Text(title)
        .fontSize(12)
        .margin({ top: 4 })
        .fontColor(this.currentIndex === targetIndex ? DesignColor.PRIMARY : DesignColor.TEXT_TERTIARY)
        .fontWeight(this.currentIndex === targetIndex ? FontWeight.Bold : FontWeight.Normal)

      // 底部指示器 — 选中时显示胶囊形
      if (this.currentIndex === targetIndex) {
        Column()
          .width(24)
          .height(5)
          .borderRadius(2.5)
          .backgroundColor(DesignColor.PRIMARY)
          .margin({ top: 4 })
      }
    }
    .width('100%')
    .justifyContent(FlexAlign.Center)
  }

  build() {
    NavDestination() {
      Tabs({ barPosition: BarPosition.End, controller: this.controller, index: this.currentIndex }) {
        TabContent() {
          Column() {
            IndexPage({
              userInfoClick: () => {
                this.controller.changeIndex(3)
              },
              isActive: this.currentIndex === 0
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
          // 选中态由文字色/加粗 + 胶囊指示器区分
          $r('sys.symbol.square_grid_2x2')))

        TabContent() {
          UserInfo()
        }.tabBar(this.tabBuilder("我的", 3, $r('sys.symbol.person_fill'), $r('sys.symbol.person')))
      }
      .onChange(i => {
        this.currentIndex = i
        if (i === 3) {
          AppStorage.setOrCreate("refreshUserInfo", true)
          AppStorage.setOrCreate("refreshOrderCount", true)
        }
      })
      .barOverlap(true)
      // 底栏高 68（对齐安卓 v0.4 AppDimens.bottomBarHeight = 68dp）
      .barHeight(68)
      .backgroundColor('#F2FFFFFF')  // 半透明白色
      .divider(null)  // 移除硬分隔线
    }
    .hideTitleBar(true)
  }
}
