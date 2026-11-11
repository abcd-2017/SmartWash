// 设备投递域共享 UI 组件 — 静态标签 chip 与条目行（创建页 / 会话页共用）
//
// 拆出原因：DeliveryTagChip 与 DeliveryItemRow 被发起页与会话页同时消费，
// 且条目行依赖 direction/kind/taken 状态渲染，按已知坑用 @ComponentV2 + @Param
// （@Builder 按值传参不响应状态更新）。

import { DesignColor, DesignDimens, DesignFontSize, DesignFontFeature, DesignFontWeight } from '../../../../constant/DesignSystem'
import {
  DeliveryDirection,
  DeliveryItemKind,
  DeliveryItemVo,
  DeliveryText,
  directionFromCode,
  formatFileSize,
  formatItemTime,
  kindFromCode
} from '../../DeliveryApi'

/** 静态标签 chip（胶囊 + caption 12，对齐安卓 ToolboxTagChip） */
@ComponentV2
export struct DeliveryTagChip {
  @Require @Param text: string
  @Require @Param containerColor: number
  @Require @Param contentColor: number

  build() {
    Text(this.text)
      .fontSize(DesignFontSize.CAPTION)
      .fontColor(this.contentColor)
      .padding({ left: 8, right: 8, top: 2, bottom: 2 })
      .backgroundColor(this.containerColor)
      .borderRadius(DesignDimens.RADIUS_FULL)
  }
}

/**
 * 条目行：kind 图标容器（蓝·物流语义）+ 标题 + 时间·大小 + 方向/取走角标；
 * 已焚毁整行灰态不可点（对齐安卓 DeliveryItemRow）。
 */
@ComponentV2
export struct DeliveryItemRow {
  @Require @Param item: DeliveryItemVo
  @Event onRowClick: () => void = () => {
  }

  private kindIcon(): Resource {
    const kind = kindFromCode(this.item.kind ?? 1)
    if (kind === DeliveryItemKind.TEXT) {
      return $r('sys.symbol.doc_text')
    }
    if (kind === DeliveryItemKind.IMAGE) {
      return $r('sys.symbol.shutter_photo')
    }
    return $r('sys.symbol.doc')
  }

  private kindName(): string {
    const kind = kindFromCode(this.item.kind ?? 1)
    if (kind === DeliveryItemKind.TEXT) {
      return DeliveryText.toolbox_delivery_kind_text
    }
    if (kind === DeliveryItemKind.IMAGE) {
      return DeliveryText.toolbox_delivery_kind_image
    }
    return DeliveryText.toolbox_delivery_kind_file
  }

  private isTaken(): boolean {
    return (this.item.taken ?? 0) !== 0
  }

  /** 收到（对端发来、待我取走）= 蓝·物流语义强调 */
  private isIncoming(): boolean {
    return directionFromCode(this.item.direction ?? 2) === DeliveryDirection.COMPUTER_TO_PHONE && !this.isTaken()
  }

  private objectSizeOf(): number | null {
    return this.item.objectSize ?? null
  }

  private itemTime(): string {
    return formatItemTime(this.item.createdAt ?? null)
  }

  private directionText(): string {
    return directionFromCode(this.item.direction ?? 2) === DeliveryDirection.PHONE_TO_COMPUTER
      ? DeliveryText.toolbox_delivery_direction_out : DeliveryText.toolbox_delivery_direction_in
  }

  /** 时间 + 文件大小（无大小仅时间） */
  private metaText(): string {
    const size = this.objectSizeOf()
    const time = this.itemTime()
    return size !== null ? `${time} · ${formatFileSize(size)}` : time
  }

  build() {
    Row() {
      // kind 图标容器（32vp / 20fp；已取走灰态）
      Column() {
        SymbolGlyph(this.kindIcon())
          .fontSize(20)
          .fontColor([this.isTaken() ? DesignColor.TEXT_TERTIARY : DesignColor.ICON_FOREGROUND_BLUE])
      }
      .width(32)
      .height(32)
      .borderRadius(DesignDimens.RADIUS_MD)
      .backgroundColor(this.isTaken() ? DesignColor.SURFACE_VARIANT : DesignColor.ICON_CONTAINER_BLUE)
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)

      Column() {
        Text(this.item.title ?? this.kindName())
          .fontSize(DesignFontSize.BODY_SMALL)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor(this.isTaken() ? DesignColor.TEXT_TERTIARY : DesignColor.TEXT_PRIMARY)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')

        Text(this.metaText())
          .fontSize(DesignFontSize.CAPTION)
          // 时间·大小为可读元信息，不用 textTertiary（compare-07 #5，对齐安卓 textSecondary）
          .fontColor(DesignColor.TEXT_SECONDARY)
          .fontFeature(DesignFontFeature.TNUM)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')
          .margin({ top: DesignDimens.SPACE_XXS })
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1)
      .margin({ left: DesignDimens.SPACE_SM })

      Column() {
        DeliveryTagChip({
          text: this.directionText(),
          containerColor: this.isIncoming() ? DesignColor.ICON_CONTAINER_BLUE : DesignColor.SURFACE_VARIANT,
          contentColor: this.isIncoming() ? DesignColor.ICON_FOREGROUND_BLUE : DesignColor.TEXT_SECONDARY
        })
        DeliveryTagChip({
          text: this.isTaken() ? DeliveryText.toolbox_delivery_state_taken : DeliveryText.toolbox_delivery_state_pending,
          containerColor: this.isTaken() ? DesignColor.SURFACE_VARIANT : DesignColor.WARNING_CONTAINER,
          // 功能色浅底 + 成对文字色（规范 §5）：#D98A26 on warningContainer 仅 2.51:1 违反 §10 红线
          //（compare-07 #3，对齐安卓 onWarningContainer）
          contentColor: this.isTaken() ? DesignColor.TEXT_TERTIARY : DesignColor.ON_WARNING_CONTAINER
        })
        .margin({ top: DesignDimens.SPACE_XXS })
      }
      .alignItems(HorizontalAlign.End)
      .margin({ left: DesignDimens.SPACE_XS })
    }
    .width('100%')
    .padding({ top: DesignDimens.SPACE_SM, bottom: DesignDimens.SPACE_SM })
    .alignItems(VerticalAlign.Center)
    .opacity(this.isTaken() ? 0.45 : 1)
    .onClick(() => {
      if (!this.isTaken()) {
        this.onRowClick()
      }
    })
  }
}
