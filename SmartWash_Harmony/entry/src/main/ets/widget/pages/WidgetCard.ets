/**
 * 洗衣订单状态桌面服务卡片（2x2 窄卡 / 2x4 宽卡，单文件双版式）。
 *
 * 平台限制查证结论（SDK 声明文件 + skill 查证，写码前完成，红线：鸿蒙 API 未经核实不落码）：
 * - 卡片页跑在独立卡片进程，仅支持 ArkUI 组件子集（SDK ets/component/*.d.ts 中带 @form
 *   标记的组件/属性：本页仅用 Text/Column/Row/Image + minFontSize/maxFontSize 自适应字号，
 *   均带 @form 标记；heightAdaptivePolicy 无 @form 标记，不使用——窄卡文本一律自适应字号
 *   + maxLines(1)，不用省略号截断）；
 * - 装饰器采用 @Entry(LocalStorage) + @Component + @LocalStorageProp：@LocalStorageProp 带
 *   @form 标记（ets/component/common.d.ts:1660，@since 11），是卡片页接收
 *   formProvider.updateForm 推送数据的标准链路；@ComponentV2/@Local/@Param 无 @form 支持
 *   证据（SDK 声明与 skill 均查证不到），按红线不在此使用 V2 —— 项目「@ComponentV2 优先」
 *   规则适用于 App 内组件，卡片页面以平台查证结果为准；
 * - 点击事件用全局 postCardAction（common.d.ts:1833，@form、@since 11、StageModelOnly），
 *   仅 router 事件允许拉起 UIAbility；params 经 want.parameters 传入应用侧
 *   （EntryAbility 兼容「直接键值」与「parameters.params 内嵌」两种形态），转成
 *   smartwash:// 深链 uri 暂存 pendingDeepLinkUri，复用既有深链分发与登录守卫；
 * - 数据契约：每个 @LocalStorageProp 的 key 与 widget/WidgetOrderModel.ets 的
 *   WidgetBinding 字段一一对应（提供方 EntryFormAbility 装配，卡片页只渲染不加工）。
 */

let storage: LocalStorage = new LocalStorage();

@Entry(storage)
@Component
struct WidgetCard {
  /** 0 = 窄卡（2x2）/ 1 = 宽卡（2x4）—— 提供方按 FormParam.DIMENSION_KEY 装配 */
  @LocalStorageProp('cardWide') cardWide: boolean = false;
  /** 是否有进行中订单（false = 引导态文案） */
  @LocalStorageProp('hasOrder') hasOrder: boolean = false;
  /** 状态文案（统一枚举口径：清洗中/待取件等；引导态为「暂无进行中的洗衣单」） */
  @LocalStorageProp('statusText') statusText: string = '正在加载…';
  /** 状态色（8 位 ARGB hex） */
  @LocalStorageProp('statusColor') statusColor: string = '#FF2D9B6A';
  /** 副行：待取件 → 取件码；清洗中 → 更新于 xx；引导态 → 点击查看订单 */
  @LocalStorageProp('infoText') infoText: string = '';
  /** 宽卡摘要行：订单号尾号 · 实付金额（窄卡不渲染） */
  @LocalStorageProp('summaryText') summaryText: string = '';
  /** 点击跳转目标（smartwash:// 深链 uri，提供方按有无订单装配） */
  @LocalStorageProp('targetUri') targetUri: string = 'smartwash://order?tab=0';

  /**
   * 卡片点击：router 事件拉起 EntryAbility 并携带跳转 uri。
   * params 键 swWidgetUri 与 EntryAbility.ets 的解析键逐字一致（跨进程唯一通道）。
   * 值统一为 smartwash:// 深链（有订单 → order-detail/{orderId}；引导态 → order?tab=0），
   * 应用侧经既有深链处理器分发：登录守卫、路由注册、页面取参全部复用。
   */
  private launchTarget(): void {
    postCardAction(this, {
      action: 'router',
      abilityName: 'EntryAbility',
      params: {
        swWidgetUri: this.targetUri
      }
    });
  }

  build() {
    if (this.cardWide) {
      // 宽卡 2x4（316x150 vp）：左列状态信息 + 右列订单摘要
      Row() {
        Column() {
          this.headerRow()
          this.statusTextBlock(true)
          this.infoTextBlock()
        }
        .layoutWeight(1)
        .height('100%')
        .alignItems(HorizontalAlign.Start)

        // 竖向发丝分隔线
        Column()
          .width(1)
          .height('80%')
          .backgroundColor('#14000000')
          .margin({ left: 10, right: 10 })

        Column() {
          Text('订单摘要')
            .fontSize(12)
            .fontColor('#FF8E8E93')
            .maxLines(1)
          Text(this.summaryText.length > 0 ? this.summaryText : '--')
            .fontSize(15)
            .fontColor('#FF1C1C1E')
            .fontWeight(FontWeight.Medium)
            .maxLines(2)
            .margin({ top: 6 })
            .minFontSize(11)
            .maxFontSize(15)
          Blank()
          Text('查看详情 ›')
            .fontSize(12)
            .fontColor('#FF2D9B6A')
            .maxLines(1)
        }
        .layoutWeight(1)
        .height('100%')
        .alignItems(HorizontalAlign.Start)
      }
      .width('100%')
      .height('100%')
      .padding(12)
      .backgroundColor('#FFF0FBF4')
      .borderRadius(16)
      .onClick(() => this.launchTarget())
    } else {
      // 窄卡 2x2（150x150 vp）：单列三行（标题 / 状态 / 副行）
      Column() {
        this.headerRow()
        this.statusTextBlock(false)
        Blank()
        this.infoTextBlock()
      }
      .width('100%')
      .height('100%')
      .padding(12)
      .backgroundColor('#FFF0FBF4')
      .borderRadius(16)
      .onClick(() => this.launchTarget())
    }
  }

  /** 顶部行：模块名 + 应用 logo（右上角，标识卡片归属） */
  @Builder
  headerRow() {
    Row() {
      Text('洗衣进度')
        .fontSize(12)
        .fontColor('#FF8E8E93')
        .maxLines(1)
        .minFontSize(9)
        .maxFontSize(12)
        .layoutWeight(1)
      Image($r('app.media.logo'))
        .width(16)
        .height(16)
        .objectFit(ImageFit.Contain)
    }
    .width('100%')
  }

  /**
   * 状态主行。有订单：状态大字（待取件加深绿强调）；引导态：引导文案（字号收敛避免溢出）。
   * 自适应字号（minFontSize + maxFontSize + maxLines(1)）：卡片宽度不可控，
   * 被截断的状态文案等于没有，宁可缩号也要读全（WisdomDaily 卡片同款取舍）。
   */
  @Builder
  statusTextBlock(wide: boolean) {
    Text(this.statusText)
      .fontSize(this.hasOrder ? (wide ? 24 : 20) : 16)
      .fontColor(this.hasOrder ? this.statusColor : '#FF6B7280')
      .fontWeight(this.hasOrder ? FontWeight.Bold : FontWeight.Medium)
      .maxLines(1)
      .minFontSize(this.hasOrder ? 12 : 10)
      .maxFontSize(this.hasOrder ? (wide ? 24 : 20) : 16)
      .margin({ top: 8 })
  }

  /** 副行：取件码 / 更新时间 / 点击提示；空串不渲染占位 */
  @Builder
  infoTextBlock() {
    if (this.infoText.length > 0) {
      Text(this.infoText)
        .fontSize(12)
        .fontColor('#FF6B7280')
        .maxLines(1)
        .minFontSize(9)
        .maxFontSize(12)
        .margin({ top: 4 })
    }
  }
}
