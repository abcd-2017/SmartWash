/**
 * 实验舱占位页 — 供本批次未实现的 5 个实验（流体 / 绘画 / 声景 / 宠物 / 星图）落地前
 * 提供统一「建设中」落点，避免首页入口点击成空白页。
 *
 * 后续批次只需用真实页面替换对应 ui/<name>/<Name>Page.ets 的构建内容
 * （router_map 已按约定文件名注册，无需改动共享文件）。
 */

import { DesignColor } from '../../../constant/DesignSystem'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'
import { EmptyState, PageHeader } from '../../../view/common/AppComponents'

/** 占位页文案 */
export class LabPlaceholderText {
  static readonly message: string = '该实验正在搭建，敬请期待'
}

@ComponentV2
export struct LabPlaceholder {
  @Param title: string = ''
  @Param message: string = LabPlaceholderText.message

  build() {
    NavDestination() {
      Column() {
        PageHeader({ title: this.title })

        Column() {
          EmptyState({ message: this.message })
        }
        .width('100%')
        .layoutWeight(1)
        .justifyContent(FlexAlign.Center)
      }
      .width('100%')
      .height('100%')
      .padding({ top: safeTopVp(), bottom: safeBottomVp() })
      .backgroundColor(DesignColor.BACKGROUND)
    }
    .hideTitleBar(true)
  }
}
