// 二级信息行组件（标签 + 值）— 与设置页 SettingRow 一级组件区分
//
// 分级口径（2026-11-09 用户拍板）：
// - 一级 = SettingRow（设置页行）：14sp/500 + 64vp 高，可点击导航；
// - 二级 = 本组件（卡内信息行）：12sp caption + 40vp 高，纯信息展示不可点。
//
// 行高 40vp（二级紧凑档，行间 0；12sp 字号在 40vp 行内上下留白约 12vp，比例协调）。

import { DesignFontSize, DesignFontFeature, DesignFontWeight } from '../../constant/DesignSystem'
import { resColor } from '../../utils/ResourceColor'

@ComponentV2
export struct InfoRow {
  @Param label: string = ''
  @Param value: string = ''
  @Param valueColor: number = resColor('TEXT_PRIMARY')
  @Param valueSize: number = DesignFontSize.CAPTION
  @Param valueWeight: number = DesignFontWeight.NORMAL
  @Param valueTnum: boolean = false
  /** 标签定宽（vp）；0 = 按内容自适应。用于多行标签对齐（如订阅详情 88vp） */
  @Param labelWidth: number = 0

  build() {
    Row() {
      Text(this.label)
        .fontSize(DesignFontSize.CAPTION)
        .fontColor(resColor('TEXT_SECONDARY'))
        .width(this.labelWidth > 0 ? this.labelWidth : 'auto')

      Text(this.value)
        .fontSize(this.valueSize)
        .fontWeight(this.valueWeight)
        .fontColor(this.valueColor)
        .fontFeature(DesignFontFeature.TNUM)
        .layoutWeight(1)
        .textAlign(TextAlign.End)
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
    }
    .width('100%')
    .height(40)
    .justifyContent(FlexAlign.SpaceBetween)
    .alignItems(VerticalAlign.Center)
  }
}
