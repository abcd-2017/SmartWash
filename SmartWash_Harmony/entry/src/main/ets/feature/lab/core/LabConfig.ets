/**
 * 解压实验舱入口配置（首页卡片列表的单一事实来源）。
 *
 * 对齐安卓 feature/lab/LabRoute.kt 的 LabToy 枚举：顺序即展示序，
 * 每项携带路由名 / 图标 / 六色浅底语义。模块为纯本地（无网络、无存储），
 * 故入口列表为编译期固定配置；LabHomePage 经 loadEntries 走「本地配置加载」
 * 状态机（加载骨架 / 失败重试 / 空态 / 内容），与后端配置页的三态形态一致。
 */

import { RouteNames } from '../../../constant/RouteNames'
import { DesignColor } from '../../../constant/DesignSystem'

/** 实验舱玩具入口枚举（顺序即首页展示序，对齐安卓 LabToy） */
export enum LabEntryKind {
  BUBBLE_WRAP,
  BREATH,
  FISH,
  SAND,
  FLUID,
  DRUM,
  PAINTING,
  SOUNDSCAPE,
  PET,
  STAR_MAP,
}

/** 单个入口卡配置 */
export class LabEntry {
  readonly kind: LabEntryKind
  readonly routeName: string
  readonly title: string
  readonly desc: string
  readonly icon: Resource
  readonly containerColor: number
  readonly iconColor: number

  constructor(kind: LabEntryKind, routeName: string, title: string, desc: string,
    icon: Resource, containerColor: number, iconColor: number) {
    this.kind = kind
    this.routeName = routeName
    this.title = title
    this.desc = desc
    this.icon = icon
    this.containerColor = containerColor
    this.iconColor = iconColor
  }
}

/**
 * 入口卡列表（编译期固定；文案对齐安卓 feature/lab/values/strings.xml）。
 * 后 5 项（流体 / 绘画 / 声景 / 宠物 / 星图）本批次为占位页，路由已注册，
 * 待对应批次补齐实现。
 */
export function labEntries(): LabEntry[] {
  return [
    new LabEntry(LabEntryKind.BUBBLE_WRAP, RouteNames.LAB_BUBBLE, '泡泡纸', '一戳一个，噼里啪啦',
      $r('sys.symbol.ellipsis_bubble'), DesignColor.ICON_CONTAINER_GREEN, DesignColor.ICON_FOREGROUND_GREEN),
    new LabEntry(LabEntryKind.BREATH, RouteNames.LAB_BREATH, '呼吸引导', '跟着圆环，一呼一吸',
      $r('sys.symbol.ohos_lungs'), DesignColor.ICON_CONTAINER_BLUE, DesignColor.ICON_FOREGROUND_BLUE),
    new LabEntry(LabEntryKind.FISH, RouteNames.LAB_FISH, '指尖鱼群', '鱼群随指尖游走',
      $r('sys.symbol.water_waves'), DesignColor.ICON_CONTAINER_TEAL, DesignColor.ICON_FOREGROUND_TEAL),
    new LabEntry(LabEntryKind.SAND, RouteNames.LAB_SAND, '落沙实验室', '倒水撒沙，看元素互相反应',
      $r('sys.symbol.wind_sand'), DesignColor.ICON_CONTAINER_ORANGE, DesignColor.ICON_FOREGROUND_ORANGE),
    new LabEntry(LabEntryKind.FLUID, RouteNames.LAB_FLUID, '指尖流体', '手指划过，搅出彩色漩涡',
      $r('sys.symbol.drop_fill'), DesignColor.ICON_CONTAINER_PURPLE, DesignColor.ICON_FOREGROUND_PURPLE),
    new LabEntry(LabEntryKind.DRUM, RouteNames.LAB_DRUM, '滚筒节拍机', '8 音色 × 16 步，排出洗衣节奏',
      $r('sys.symbol.music'), DesignColor.ICON_CONTAINER_PINK, DesignColor.ICON_FOREGROUND_PINK),
    new LabEntry(LabEntryKind.PAINTING, RouteNames.LAB_PAINTING, '今日一画', '每天一幅水墨山水',
      $r('sys.symbol.paintpalette'), DesignColor.ICON_CONTAINER_GREEN, DesignColor.ICON_FOREGROUND_GREEN),
    new LabEntry(LabEntryKind.SOUNDSCAPE, RouteNames.LAB_SOUNDSCAPE, '声景混音器', '雨声风声，自由叠混',
      $r('sys.symbol.waveform'), DesignColor.ICON_CONTAINER_BLUE, DesignColor.ICON_FOREGROUND_BLUE),
    new LabEntry(LabEntryKind.PET, RouteNames.LAB_PET, '泡泡精灵', '养一只住在洗衣机里的泡泡精灵',
      $r('sys.symbol.pawprint'), DesignColor.ICON_CONTAINER_PINK, DesignColor.ICON_FOREGROUND_PINK),
    new LabEntry(LabEntryKind.STAR_MAP, RouteNames.LAB_STARMAP, '掌上星图', '举起手机，看头顶的星空',
      $r('sys.symbol.ohos_star'), DesignColor.ICON_CONTAINER_PURPLE, DesignColor.ICON_FOREGROUND_PURPLE),
  ]
}
