import { IndexPage } from "../view/IndexPage"
import { Service } from "../view/Service"
import { UserInfo } from "../view/UserInfo"
import { Workbench } from "../feature/workbench/Workbench"
import { DesignColor } from "../constant/DesignSystem"

@Builder
export function HomeBuilder() {
  Home()
}

@Component
struct Home {
  private controller: TabsController = new TabsController()
  @State currentIndex: number = 0

  @Builder
  tabBuilder(title: string, targetIndex: number, selectedImg: Resource, normalImg: Resource) {
    Column() {
      SymbolGlyph(this.currentIndex === targetIndex ? selectedImg : normalImg)
        .fontSize(22)
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
      .backgroundColor('#F2FFFFFF')  // 半透明白色
      .divider(null)  // 移除硬分隔线
    }
    .hideTitleBar(true)
  }
}
