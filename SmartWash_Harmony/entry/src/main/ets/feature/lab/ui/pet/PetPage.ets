/**
 * 泡泡精灵页 — 对齐安卓 feature/lab/ui/pet/PetPage.kt。
 *
 * 结构：信息卡（阶段名 + 放置回报 + 水池浅底里的精灵画布 + 累计互动次数）
 * + 「喂食 / 玩耍」双按钮 + 一句「点它抚摸」引导。
 * 互动：点精灵 = 抚摸（HAPPY + 计数）、喂食（EATING）、玩耍（PLAYING）；
 * 累计互动次数达阈值进化（小泡泡 → 泡沫精灵 → 彩虹泡泡），计数经 preferences 持久化
 * （对齐安卓 DataStore 三项：阶段 / 计数 / 上次见面时刻）。
 * 放置回报只出一句文案（按距上次见面时长分档），不伪造数值成长。
 *
 * 动画：反应动画（happy 挤压拉伸 / eating 咀嚼 / playing 弹跳）与进化放大回弹
 * 由 30ms 局部定时器驱动（Android 走 spring Animatable，鸿蒙以正弦脉冲近似），
 * 仅限时态/动画进行中运行，静帧不空转；tick（限时回 idle、闲置入睡）由 1s 定时器驱动。
 */

import { DesignColor, DesignDimens, DesignFontFeature, DesignFontSize, DesignFontWeight } from '../../../../constant/DesignSystem'
import { safeBottomVp, safeTopVp } from '../../../../utils/SafeArea'
import { AppButton, AppButtonVariant, PageHeader } from '../../../../view/common/AppComponents'
import { ThemeRefreshV2 } from '../../../../view/common/ThemeRefresh'
import { getValue, setValue } from '../../../../utils/StorageUtil'
import { logger } from '../../../../utils/Logger'
import { AbsenceBucket, PetAction, PetEngine, PetInteractResult, PetSnapshot, PetStage,
  PetStageTable, PetState, petStateIsTimed } from '../../core/PetEngine'
import { LabHaptic, labReduceMotion } from '../../core/LabCommon'

/** 页面文案（对齐安卓 strings_pet.xml） */
class PetText {
  static readonly title: string = '泡泡精灵'
  static readonly stageBubble: string = '小泡泡'
  static readonly stageFoam: string = '泡沫精灵'
  static readonly stageRainbow: string = '彩虹泡泡'
  static readonly absenceFirst: string = '这是你们第一次见面'
  static readonly absenceJustNow: string = '它刚见过你'
  static readonly absenceShort: string = '它想你了'
  static readonly absenceLong: string = '它一直在等你'
  static readonly absenceVeryLong: string = '它等了你很久很久'
  static readonly feed: string = '喂食'
  static readonly play: string = '玩耍'
  static readonly petHint: string = '点它一下，摸摸它'
}

/** 精灵画布尺寸（vp）——本屏唯一的视觉锚点 */
const PET_CANVAS_SIZE: number = 220

/** 状态机 tick 周期（ms）：驱动限时状态回 idle、idle 超时入睡 */
const PET_TICK_MS: number = 1000

/** 反应动画周期（ms）：0→1→0 正弦脉冲，近似安卓 spring 双段 */
const REACTION_MS: number = 600

/** 进化放大回弹周期（ms） */
const EVOLVE_MS: number = 500

/** 动画帧间隔（ms） */
const ANIM_FRAME_MS: number = 30

/** 持久化 key（归本玩具自持，对齐安卓 PetViewModel 的 key） */
const KEY_STAGE: string = 'lab_pet_stage'
const KEY_COUNT: string = 'lab_pet_interaction_count'
const KEY_LAST_SEEN: string = 'lab_pet_last_seen'

const TAG: string = 'petPage'

@Builder
export function PetBuilder() {
  PetPage()
}

@ComponentV2
export struct PetPage {
  @Local snapshot: PetSnapshot = new PetSnapshot()
  @Local absence: AbsenceBucket = AbsenceBucket.FIRST_MEET
  @Local evolveGeneration: number = 0

  private engine: PetEngine = new PetEngine()
  private loaded: boolean = false
  private ctx: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true))
  private tickTimerId: number = -1
  private animTimerId: number = -1
  // 反应动画与进化动画起点（0 = 未进行）
  private reactionStartMs: number = 0
  private evolveStartMs: number = 0
  private reduceMotion: boolean = labReduceMotion()

  aboutToAppear(): void {
    this.tickTimerId = setInterval(() => {
      this.onTick()
    }, PET_TICK_MS)
    this.load()
  }

  aboutToDisappear(): void {
    if (this.tickTimerId !== -1) {
      clearInterval(this.tickTimerId)
      this.tickTimerId = -1
    }
    this.stopAnimLoop()
  }

  // ---------- 持久化装载 ----------

  private async load(): Promise<void> {
    const stageVal = await getValue(KEY_STAGE, 'BUBBLE')
    const stageName: string = typeof stageVal === 'string' ? stageVal : 'BUBBLE'
    const countVal = await getValue(KEY_COUNT, 0)
    const count: number = typeof countVal === 'number' && Number.isFinite(countVal)
      ? Math.floor(countVal) : 0
    const seenVal = await getValue(KEY_LAST_SEEN, 0)
    const seen: number = typeof seenVal === 'number' && Number.isFinite(seenVal)
      ? Math.floor(seenVal) : 0
    const now: number = Date.now()
    this.engine.restore(PetStageTable.fromName(stageName), count, seen, now)
    // 先取放置回报（刷新前），再标记本次见面（对齐安卓 PetViewModel）
    this.absence = this.engine.absenceBucket(now)
    this.engine.markSeen(now)
    this.snapshot = this.engine.current()
    this.loaded = true
    this.persist()
    this.drawPet()
  }

  private persist(): void {
    const snapshot: PetSnapshot = this.engine.current()
    setValue(KEY_STAGE, PetStageTable.toName(snapshot.stage)).catch((err: Error) => {
      logger.warn(TAG, `宠物进度落盘失败，进度回退至上次持久化, ${err.message}`)
    })
    setValue(KEY_COUNT, snapshot.interactionCount).catch((err: Error) => {
      logger.warn(TAG, `宠物进度落盘失败，进度回退至上次持久化, ${err.message}`)
    })
    setValue(KEY_LAST_SEEN, snapshot.lastSeenAt).catch((err: Error) => {
      logger.warn(TAG, `宠物进度落盘失败，进度回退至上次持久化, ${err.message}`)
    })
  }

  // ---------- 互动与 tick ----------

  /** 一次互动：引擎同步迁移后发布新快照；防抖结果静默丢弃 */
  private interact(action: PetAction): void {
    if (!this.loaded) {
      return
    }
    const result: PetInteractResult = this.engine.interact(action)
    if (!result.accepted || result.snapshot === null) {
      return
    }
    this.snapshot = result.snapshot
    if (result.evolved) {
      this.evolveGeneration += 1
      // reduced motion：进化直接切终态，不播放放大回弹（规范 §8）
      if (!this.reduceMotion) {
        this.evolveStartMs = Date.now()
        this.startAnimLoop()
      }
      LabHaptic.success()
    } else if (action === PetAction.PET) {
      LabHaptic.light()
    } else {
      LabHaptic.selection()
    }
    if (petStateIsTimed(this.snapshot.state)) {
      // reduced motion：仅用户主动「玩耍」保留一次轻弹跳，其余反应静帧
      if (!this.reduceMotion || this.snapshot.state === PetState.PLAYING) {
        this.reactionStartMs = Date.now()
        this.startAnimLoop()
      }
    }
    this.persist()
  }

  /** 时间推进（1s 周期）：仅在发生状态迁移时发布 */
  private onTick(): void {
    if (!this.loaded) {
      return
    }
    const result = this.engine.tick()
    if (result.changed) {
      this.snapshot = result.snapshot
    }
  }

  // ---------- 动画循环（仅限时态/动画进行中运行） ----------

  private startAnimLoop(): void {
    if (this.animTimerId !== -1) {
      return
    }
    this.animTimerId = setInterval(() => {
      const now: number = Date.now()
      const reactionActive: boolean = this.reactionStartMs > 0 &&
        now - this.reactionStartMs < REACTION_MS
      const evolveActive: boolean = this.evolveStartMs > 0 && now - this.evolveStartMs < EVOLVE_MS
      if (!reactionActive && !evolveActive) {
        this.reactionStartMs = 0
        this.evolveStartMs = 0
        this.stopAnimLoop()
      }
      this.drawPet()
    }, ANIM_FRAME_MS)
  }

  private stopAnimLoop(): void {
    if (this.animTimerId !== -1) {
      clearInterval(this.animTimerId)
      this.animTimerId = -1
    }
  }

  // ---------- 文案 ----------

  private stageLabel(stage: PetStage): string {
    switch (stage) {
      case PetStage.FOAM_SPIRIT:
        return PetText.stageFoam
      case PetStage.RAINBOW_BUBBLE:
        return PetText.stageRainbow
      default:
        return PetText.stageBubble
    }
  }

  private absenceLabel(bucket: AbsenceBucket): string {
    switch (bucket) {
      case AbsenceBucket.JUST_NOW:
        return PetText.absenceJustNow
      case AbsenceBucket.SHORT:
        return PetText.absenceShort
      case AbsenceBucket.LONG:
        return PetText.absenceLong
      case AbsenceBucket.VERY_LONG:
        return PetText.absenceVeryLong
      default:
        return PetText.absenceFirst
    }
  }

  /** 状态名（无障碍描述用，对齐安卓 strings_pet.xml） */
  private stateLabel(state: PetState): string {
    switch (state) {
      case PetState.HAPPY:
        return '开心'
      case PetState.EATING:
        return '吃东西'
      case PetState.PLAYING:
        return '玩耍中'
      case PetState.SLEEPING:
        return '睡着了'
      default:
        return '发呆中'
    }
  }

  /** 精灵画布无障碍描述：进化阶段 + 当前状态 */
  private petCanvasDescription(): string {
    return `泡泡精灵，${this.stageLabel(this.snapshot.stage)}，${this.stateLabel(this.snapshot.state)}`
  }

  // ---------- 精灵绘制（对齐安卓 drawPet / drawStageAccessory / drawFace） ----------

  /** 阶段体型系数（进化可见：小泡泡最小、彩虹泡泡最大） */
  private stageScale(stage: PetStage): number {
    switch (stage) {
      case PetStage.FOAM_SPIRIT:
        return 0.95
      case PetStage.RAINBOW_BUBBLE:
        return 1.08
      default:
        return 0.82
    }
  }

  private drawPet(): void {
    const ctx: CanvasRenderingContext2D = this.ctx
    const size: number = PET_CANVAS_SIZE
    const cx: number = size / 2
    const cy: number = size / 2
    ctx.clearRect(0, 0, size, size)

    const stage: PetStage = this.snapshot.stage
    const state: PetState = this.snapshot.state
    const baseRadius: number = size / 2 * 0.62
    const radius: number = baseRadius * this.stageScale(stage)

    // 反应动画（0→1→0 正弦脉冲）：happy 挤压拉伸、eating 咀嚼、playing 弹跳
    const now: number = Date.now()
    let pulse: number = 0
    if (this.reactionStartMs > 0) {
      const p: number = (now - this.reactionStartMs) / REACTION_MS
      if (p >= 0 && p < 1) {
        pulse = Math.sin(p * Math.PI)
      }
    }
    let evolveScale: number = 1
    if (this.evolveStartMs > 0 && !this.reduceMotion) {
      const p: number = (now - this.evolveStartMs) / EVOLVE_MS
      if (p >= 0 && p < 1) {
        evolveScale = 1 + 0.28 * Math.sin(p * Math.PI)
      }
    }
    let scaleX: number = 1
    let scaleY: number = 1
    let transY: number = 0
    if (state === PetState.HAPPY) {
      scaleX = 1 + 0.12 * pulse
      scaleY = 1 + 0.12 * pulse
    } else if (state === PetState.EATING) {
      scaleX = 1 + 0.10 * pulse
      scaleY = 1 - 0.12 * pulse
    } else if (state === PetState.PLAYING) {
      // reduced motion 下降幅减半（对齐安卓 reduceMotion 分支）
      // 注：ArkTS 严格模式禁止对三元表达式直接加一元负号（arkts-no-polymorphic-unops），负值内联
      transY = (this.reduceMotion ? -10 : -28) * pulse
    }

    // 水面圆底盘（托住精灵，强化「住在洗衣机里」的意象）
    ctx.globalAlpha = 0.45
    ctx.fillStyle = DesignColor.WATER_SOFT
    ctx.beginPath()
    ctx.arc(cx, cy, size / 2 * 0.94, 0, 2 * Math.PI)
    ctx.fill()
    ctx.globalAlpha = 1

    ctx.save()
    ctx.translate(cx, cy + transY)
    ctx.scale(scaleX * evolveScale, scaleY * evolveScale)

    this.drawStageAccessory(stage, radius)

    // 泡泡身体：径向渐变（高光偏左上，边缘偏 water 深色）
    const gradient: CanvasGradient = ctx.createRadialGradient(
      -radius * 0.3, -radius * 0.3, 0, -radius * 0.3, -radius * 0.3, radius * 1.6)
    gradient.addColorStop(0, this.argbToString(DesignColor.SURFACE))
    gradient.addColorStop(0.5, this.argbToString(DesignColor.WATER_SOFT))
    gradient.addColorStop(1, this.argbToString(DesignColor.WATER, 0.55))
    ctx.fillStyle = gradient
    ctx.beginPath()
    ctx.arc(0, 0, radius, 0, 2 * Math.PI)
    ctx.fill()
    ctx.globalAlpha = 0.35
    ctx.strokeStyle = DesignColor.WATER
    ctx.lineWidth = 1
    ctx.stroke()
    ctx.globalAlpha = 1

    // 高光
    ctx.globalAlpha = 0.8
    ctx.fillStyle = DesignColor.SURFACE
    ctx.beginPath()
    ctx.arc(-radius * 0.34, -radius * 0.34, radius * 0.16, 0, 2 * Math.PI)
    ctx.fill()
    ctx.globalAlpha = 1

    this.drawFace(state, radius)
    ctx.restore()
  }

  /**
   * 阶段小部件（小泡泡无装饰；泡沫精灵加三颗泡沫；彩虹泡泡加五色环）：
   * 进化不只是换名，也是可见的外形成长。
   */
  private drawStageAccessory(stage: PetStage, radius: number): void {
    const ctx: CanvasRenderingContext2D = this.ctx
    if (stage === PetStage.FOAM_SPIRIT) {
      const foams: number[][] = [
        [-radius * 1.22, -radius * 0.55, radius * 0.22],
        [radius * 1.12, -radius * 0.78, radius * 0.16],
        [radius * 1.32, radius * 0.32, radius * 0.26],
      ]
      for (const foam of foams) {
        ctx.fillStyle = DesignColor.WATER_SOFT
        ctx.beginPath()
        ctx.arc(foam[0], foam[1], foam[2], 0, 2 * Math.PI)
        ctx.fill()
        ctx.globalAlpha = 0.5
        ctx.strokeStyle = DesignColor.WATER
        ctx.lineWidth = 1
        ctx.stroke()
        ctx.globalAlpha = 1
      }
    } else if (stage === PetStage.RAINBOW_BUBBLE) {
      const ringColors: number[] = [
        DesignColor.DANGER, DesignColor.ONGOING, DesignColor.PRIMARY,
        DesignColor.WATER, DesignColor.ICON_FOREGROUND_PURPLE,
      ]
      const ringRadius: number = radius * 1.18
      const sweep: number = 2 * Math.PI / ringColors.length
      ctx.lineWidth = radius * 0.14
      ctx.lineCap = 'round'
      for (let i = 0; i < ringColors.length; i++) {
        ctx.strokeStyle = ringColors[i]
        ctx.beginPath()
        ctx.arc(0, 0, ringRadius, -Math.PI / 2 + i * sweep, -Math.PI / 2 + (i + 1) * sweep)
        ctx.stroke()
      }
    }
  }

  /** 表情：眼睛 + 嘴随状态切换（idle 点眼微笑 / happy·playing ^眼大笑 / eating 张口 / sleeping 闭眼 + Z） */
  private drawFace(state: PetState, radius: number): void {
    const ctx: CanvasRenderingContext2D = this.ctx
    const ink: number = DesignColor.TEXT_PRIMARY
    const eyeY: number = -radius * 0.12
    const eyeDX: number = radius * 0.32
    const eyeR: number = radius * 0.075
    ctx.strokeStyle = ink
    ctx.fillStyle = ink
    ctx.lineCap = 'round'

    if (state === PetState.HAPPY || state === PetState.PLAYING) {
      ctx.lineWidth = radius * 0.05
      for (const dx of [-eyeDX, eyeDX]) {
        ctx.beginPath()
        ctx.moveTo(dx - eyeR * 1.4, eyeY + eyeR)
        ctx.lineTo(dx, eyeY - eyeR * 1.2)
        ctx.lineTo(dx + eyeR * 1.4, eyeY + eyeR)
        ctx.stroke()
      }
    } else if (state === PetState.SLEEPING) {
      ctx.lineWidth = radius * 0.05
      for (const dx of [-eyeDX, eyeDX]) {
        ctx.beginPath()
        ctx.ellipse(dx, eyeY + eyeR * 0.2, eyeR * 1.6, eyeR * 1.2, 0,
          20 * Math.PI / 180, 160 * Math.PI / 180)
        ctx.stroke()
      }
    } else {
      for (const dx of [-eyeDX, eyeDX]) {
        ctx.beginPath()
        ctx.arc(dx, eyeY, eyeR, 0, 2 * Math.PI)
        ctx.fill()
      }
    }

    const mouthY: number = radius * 0.22
    if (state === PetState.EATING) {
      ctx.beginPath()
      ctx.arc(0, mouthY, radius * 0.13, 0, 2 * Math.PI)
      ctx.fill()
    } else if (state === PetState.HAPPY || state === PetState.PLAYING) {
      ctx.lineWidth = radius * 0.06
      ctx.beginPath()
      ctx.ellipse(0, mouthY, radius * 0.28, radius * 0.22, 0,
        15 * Math.PI / 180, 165 * Math.PI / 180)
      ctx.stroke()
    } else if (state === PetState.SLEEPING) {
      ctx.lineWidth = radius * 0.05
      ctx.beginPath()
      ctx.ellipse(0, mouthY, radius * 0.14, radius * 0.1, 0,
        20 * Math.PI / 180, 160 * Math.PI / 180)
      ctx.stroke()
      this.drawSleepZ(radius)
    } else {
      ctx.lineWidth = radius * 0.05
      ctx.beginPath()
      ctx.ellipse(0, mouthY, radius * 0.2, radius * 0.14, 0,
        20 * Math.PI / 180, 160 * Math.PI / 180)
      ctx.stroke()
    }
  }

  /** 睡眠小「Z」（textTertiary，避免与表情抢视线） */
  private drawSleepZ(radius: number): void {
    const ctx: CanvasRenderingContext2D = this.ctx
    const zx: number = radius * 0.5
    const zy: number = -radius * 0.72
    const s: number = radius * 0.16
    ctx.globalAlpha = 1
    ctx.strokeStyle = DesignColor.TEXT_TERTIARY
    ctx.lineWidth = radius * 0.045
    ctx.lineCap = 'round'
    ctx.beginPath()
    ctx.moveTo(zx, zy)
    ctx.lineTo(zx + s, zy)
    ctx.lineTo(zx, zy + s)
    ctx.lineTo(zx + s, zy + s)
    ctx.stroke()
  }

  /** ARGB 令牌色 → canvas 可解析的 rgba() 字符串（渐变色 stop 不收 number） */
  private argbToString(color: number, alpha: number = 1): string {
    const a: number = Math.round(Math.min(1, Math.max(0, alpha)) * 255)
    const red: number = (color >>> 16) & 0xFF
    const green: number = (color >>> 8) & 0xFF
    const blue: number = color & 0xFF
    return `rgba(${red}, ${green}, ${blue}, ${a / 255})`
  }

  @Monitor('snapshot')
  onSnapshotChange(): void {
    if (this.snapshot.state === PetState.IDLE || this.snapshot.state === PetState.SLEEPING) {
      this.reactionStartMs = 0
    } else if (petStateIsTimed(this.snapshot.state) && this.reactionStartMs <= 0) {
      // 限时态由 interact 启动反应动画；此处兜底（如恢复装载路径）
      // reduced motion：仅「玩耍」保留一次轻弹跳
      if (!this.reduceMotion || this.snapshot.state === PetState.PLAYING) {
        this.reactionStartMs = Date.now()
        this.startAnimLoop()
      }
    }
    this.drawPet()
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计补齐）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，子树内 DesignColor 静态令牌直读点重读新值，
      // 画布经重建后的 onReady 以新令牌重绘（机制见 view/common/ThemeRefresh.ets）
      ThemeRefreshV2() {
      Column() {
        PageHeader({ title: PetText.title })

        Scroll() {
          Column({ space: DesignDimens.CARD_SPACING }) {
            Column() {
              Text(this.stageLabel(this.snapshot.stage))
                .fontSize(DesignFontSize.CARD_TITLE)
                .fontWeight(DesignFontWeight.SEMI_BOLD)
                .fontColor(DesignColor.TEXT_PRIMARY)
              Text(this.absenceLabel(this.absence))
                .fontSize(DesignFontSize.BODY_SMALL)
                .fontColor(DesignColor.TEXT_SECONDARY)
                .margin({ top: DesignDimens.SPACE_XXS })

              Column() {
                Canvas(this.ctx)
                  .width(PET_CANVAS_SIZE)
                  .height(PET_CANVAS_SIZE)
                  .accessibilityDescription(this.petCanvasDescription())
                  .onClick(() => {
                    this.interact(PetAction.PET)
                  })
                  .onReady(() => {
                    this.drawPet()
                  })
              }
              .width('100%')
              .borderRadius(DesignDimens.RADIUS_MD)
              .backgroundColor(DesignColor.SURFACE_VARIANT)
              .padding({ top: DesignDimens.SPACE_MD, bottom: DesignDimens.SPACE_MD })
              .alignItems(HorizontalAlign.Center)
              .margin({ top: DesignDimens.SPACE_MD })

              Text(`互动 ${this.snapshot.interactionCount} 次`)
                .fontSize(DesignFontSize.CAPTION)
                .fontWeight(DesignFontWeight.MEDIUM)
                .fontColor(DesignColor.TEXT_SECONDARY)
                .fontFeature(DesignFontFeature.TNUM)
                .margin({ top: DesignDimens.SPACE_MD })
            }
            .width('100%')
            .padding(DesignDimens.CARD_PADDING)
            .backgroundColor(DesignColor.SURFACE)
            .borderRadius(DesignDimens.CARD_RADIUS)
            .borderWidth(1)
            .borderColor(DesignColor.OUTLINE)
            .shadow({
              radius: 12,
              color: DesignColor.SHADOW_COLOR_CARD,
              offsetX: 0,
              offsetY: 2
            })
            .alignItems(HorizontalAlign.Center)

            Row({ space: DesignDimens.CARD_SPACING }) {
              Column() {
                AppButton({
                  text: PetText.feed,
                  variant: AppButtonVariant.SECONDARY,
                  click: (): void => {
                    this.interact(PetAction.FEED)
                  }
                })
              }
              .layoutWeight(1)

              Column() {
                AppButton({
                  text: PetText.play,
                  click: (): void => {
                    this.interact(PetAction.PLAY)
                  }
                })
              }
              .layoutWeight(1)
            }
            .width('100%')

            Text(PetText.petHint)
              .fontSize(DesignFontSize.BODY_SMALL)
              .fontColor(DesignColor.TEXT_SECONDARY)
              .textAlign(TextAlign.Center)
              .width('100%')
          }
          .width('100%')
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING,
            top: DesignDimens.SPACE_MD, bottom: DesignDimens.SECTION_SPACING })
        }
        .width('100%')
        .layoutWeight(1)
        .align(Alignment.TopStart)
        .scrollBar(BarState.Off)
      }
      .width('100%')
      .height('100%')
      .padding({ top: safeTopVp(), bottom: safeBottomVp() })
      .backgroundColor(DesignColor.BACKGROUND)
      }
    }
    .hideTitleBar(true)
  }
}
