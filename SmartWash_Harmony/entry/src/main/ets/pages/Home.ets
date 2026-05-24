import { IndexPage } from "../view/IndexPage"
import { Service } from "../view/Service"
import { UserInfo } from "../view/UserInfo"
import { DesignColor } from "../constant/DesignSystem"

@Builder
export function HomeBuilder() {
  Home()
}

@Entry
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

      // 底部短横线指示器
      if (this.currentIndex === targetIndex) {
        Divider()
          .strokeWidth(2)
          .width(24)
          .color(DesignColor.PRIMARY)
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
                this.controller.changeIndex(2)
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
          UserInfo()
        }.tabBar(this.tabBuilder("主页", 2, $r('sys.symbol.person_fill'), $r('sys.symbol.person')))
      }
      .onChange(i => {
        this.currentIndex = i
        if (i == 2) {
          AppStorage.setOrCreate("refreshUserInfo", true)
          AppStorage.setOrCreate("refreshOrderCount", true)
        }
      })
      .barOverlap(true)
      .backgroundColor(Color.White)
      .divider({
        strokeWidth: 0.5,
        color: DesignColor.DIVIDER
      })
    }
    .hideTitleBar(true)
  }
}