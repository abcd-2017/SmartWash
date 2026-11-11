import { DesignDimens, DesignFontSize, DesignFontWeight, MotionTiming } from '../constant/DesignSystem'
import vibrator from '@ohos.vibrator'
import { resColor } from '../utils/ResourceColor'

/** 底栏 tab 定义（对齐安卓 homeTabItems：图标对 + 文案） */
export interface BottomNavTab {
  key: string
  label: string
  selectedIcon: Resource
  normalIcon: Resource
}

/**
 * 底部导航栏 —— 对齐安卓 HomePage.kt:145-194 `BottomBar`（规范 §4.5）。
 *
 * 结构：0.5vp outline 顶部分隔线 + 68vp surface 白底 Row；
 * 4 个 tab 项 wrap-content（图标 24 + 横 padding 16），Row 用 SpaceAround 均分剩余空间
 * （安卓 `Arrangement.SpaceAround`，见 HomePage.kt:168）——非等分槽位。
 * 安全区由宿主 NavDestination 的 bottom padding 承载，本组件只画 68vp 内容。
 */
@ComponentV2
export struct BottomNav {
  @Param tabs: BottomNavTab[] = []
  @Param currentIndex: number = 0
  @Event onItemClick: (index: number) => void = (_: number) => {
  }

  build() {
    Column() {
      // 顶部分隔线 — 设计 nav3 border-top .5px line（对齐安卓 BottomBar）
      Divider()
        .width('100%')
        .strokeWidth(0.5)
        .color(resColor('OUTLINE'))

      Row() {
        ForEach(this.tabs, (tab: BottomNavTab, index: number) => {
          BottomNavItem({
            label: tab.label,
            selectedIcon: tab.selectedIcon,
            normalIcon: tab.normalIcon,
            isSelected: this.currentIndex === index,
            onItemClick: () => {
              this.onItemClick(index)
            }
          })
        }, (tab: BottomNavTab) => tab.key)
      }
      .width('100%')
      .height(68)
      .backgroundColor(resColor('SURFACE'))
      // SpaceAround：4 项未铺满宽度时均分剩余空间（对齐安卓 Arrangement.SpaceAround）
      .justifyContent(FlexAlign.SpaceAround)
      .alignItems(VerticalAlign.Center)
    }
    .width('100%')
    .backgroundColor(resColor('SURFACE'))
  }
}

/**
 * 底栏 tab 项 —— 对齐安卓 HomePage.kt:196-245 `BottomNavItem` + `AnimatedBottomNavIcon`（规范 §4.5）。
 *
 * 选中动效（600ms 交叉淡变）：灰实心 → 品牌实心交叉淡变 600ms（Curve.Linear，对齐安卓 600ms 时间线），
 * 图标 scale 1 → 1.06（默认弹簧），标签色 200ms 过渡（对齐安卓 DURATION_MICRO_MS）。
 * 未选中态直接以灰实心起步（不跑入场时间线），切走时只收尾淡变，避免新旧图标同时跑全时序。
 *
 * 标签恒定 12sp/500（labelSmall），只差颜色——杜绝选中瞬间字重跳变（recap §3 [V3]）。
 * 无胶囊指示器（安卓底栏无指示器，recap §3 [V2]）。
 *
 * reduced motion：当前 SDK 无系统动画时长开关可读（同 divReduceMotion 决策），
 * 降级分支待 API 可用后接入；此处动画时长 600ms 与安卓一致。
 */
@ComponentV2
export struct BottomNavItem {
  @Param label: string = ''
  @Param selectedIcon: Resource = $r('sys.symbol.house_fill')
  @Param normalIcon: Resource = $r('sys.symbol.house')
  @Param isSelected: boolean = false
  // 命名注意：不能叫 onClick（与 CustomComponent 基类的同名属性方法冲突导致编译错误）
  @Event onItemClick: () => void = () => {}

  // 交叉淡变系数：normal=1/selected=0 为未选中；选中时 600ms 内互换
  @Local normalOpacity: number = 1
  @Local selectedOpacity: number = 0
  @Local iconScale: number = 1
  @Local labelColor: number = resColor('TEXT_TERTIARY')

  aboutToAppear(): void {
    this.applyState(false)
  }

  @Monitor('isSelected')
  onSelectedChange(): void {
    this.applyState(true)
  }

  /** 同步选中态：animate=false 直接落终态（首帧/初始），true 走 600ms 交叉淡变 */
  private applyState(animate: boolean): void {
    if (animate) {
      animateTo({ duration: 600, curve: Curve.Linear }, () => {
        this.normalOpacity = this.isSelected ? 0 : 1
        this.selectedOpacity = this.isSelected ? 1 : 0
      })
      animateTo({ duration: 600, curve: MotionTiming.SPRING }, () => {
        this.iconScale = this.isSelected ? 1.06 : 1
      })
      animateTo({ duration: 200, curve: Curve.Linear }, () => {
        this.labelColor = this.isSelected ? resColor('PRIMARY') : resColor('TEXT_TERTIARY')
      })
    } else {
      this.normalOpacity = this.isSelected ? 0 : 1
      this.selectedOpacity = this.isSelected ? 1 : 0
      this.iconScale = this.isSelected ? 1.06 : 1
      this.labelColor = this.isSelected ? resColor('PRIMARY') : resColor('TEXT_TERTIARY')
    }
  }

  private triggerClick(): void {
    // tab 点击选中触感（对齐安卓 view.performHaptic(HapticEffect.SELECTION)）
    try {
      vibrator.startVibration({ type: 'time', duration: 10 }, { id: 0, usage: 'unknown' })
    } catch (err) {
      // 忽略不支持触感的设备
    }
    this.onItemClick()
  }

  build() {
    Column() {
      // 线性/实心成对图标交叉淡变（对齐安卓 Canvas evenOdd 实心路径交叉淡变）
      Stack() {
        SymbolGlyph(this.normalIcon)
          .fontSize(24)
          .fontColor([resColor('TEXT_TERTIARY')])
          .opacity(this.normalOpacity)

        SymbolGlyph(this.selectedIcon)
          .fontSize(24)
          .fontColor([resColor('PRIMARY')])
          .opacity(this.selectedOpacity)
      }
      .width(26)
      .height(26)
      .scale({ x: this.iconScale, y: this.iconScale })

      Text(this.label)
        .fontSize(DesignFontSize.CAPTION)
        .fontWeight(DesignFontWeight.MEDIUM)
        .fontColor(this.labelColor)
        .margin({ top: DesignDimens.SPACE_XXS })
    }
    // wrap-content（对齐安卓 BottomNavItem：Column 无宽度修饰，内容宽 = 图标/文字 + 横 padding 16）；
    // 不再 .width('100%') 撑满等分槽位，热区回归内容宽（约 56–68vp）
    .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD, top: 4, bottom: 4 })
    .justifyContent(FlexAlign.Center)
    .onClick(() => {
      this.triggerClick()
    })
  }
}
