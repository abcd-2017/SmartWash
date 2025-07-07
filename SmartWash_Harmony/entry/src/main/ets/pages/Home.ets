import { Service } from "../view/Service"
import { UserInfo } from "../view/UserInfo"

@Builder
export function HomeBuilder() {
  Home()
}

@Entry
@Component
struct Home {
  private controller: TabsController = new TabsController()
  @State currentIndex: number = 2

  @Builder
  tabBuilder(title: string, targetIndex: number, selectedImg: Resource, normalImg: Resource) {
    Column() {
      SymbolGlyph(this.currentIndex === targetIndex ? selectedImg : normalImg)
        .fontSize(20)
        .fontColor([$r('sys.color.font_primary')])
        .margin({ top: 6 })
      Text(title)
        .fontSize(14)
        .margin({ top: 8 })
        .fontWeight(this.currentIndex === targetIndex ? FontWeight.Bold : FontWeight.Normal)
    }
    .width('100%')
    .justifyContent(FlexAlign.Center)
  }

  build() {
    NavDestination() {
      Tabs({ barPosition: BarPosition.End, controller: this.controller, index: this.currentIndex }) {
        TabContent() {
          Column() {
            Text("首页")
          }.height("100%")
          .width("100%")
          .backgroundColor(Color.Gray)
        }.tabBar(this.tabBuilder("首页", 0, $r('sys.symbol.house_fill'), $r('sys.symbol.house')))

        TabContent() {
          Service()
        }.tabBar(this.tabBuilder("服务", 1, $r('sys.symbol.list_bullet_square_fill_1'),
          $r('sys.symbol.list_bullet_square')))

        TabContent() {
          UserInfo()
        }.tabBar(this.tabBuilder("主页", 2, $r('sys.symbol.person_fill'), $r('sys.symbol.person')))
      }.onChange(i => {
        this.currentIndex = i
      }).barOverlap(true)
    }
    .hideTitleBar(true)
  }
}