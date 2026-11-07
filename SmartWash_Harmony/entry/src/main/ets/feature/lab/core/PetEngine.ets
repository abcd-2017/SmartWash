/**
 * 泡泡精灵状态机内核 — 纯逻辑（零 UI 依赖），逐行为对齐安卓 PetEngine.kt。
 *
 * 职责边界：只负责「状态迁移 + 进化判定 + 防抖 + 放置回报分档」，
 * 不持有任何持久化与 UI 文案；时钟经构造参数注入（单测可注入虚拟时钟）；
 * 每次迁移整体替换 [PetSnapshot]（不可变快照），页面据此重渲染。
 *
 * 状态机：
 * ```
 *         pet(抚摸)          feed(喂食)         play(玩耍)
 * IDLE ───────────► HAPPY ───────────► EATING ───────────► PLAYING
 *  │                  │ 2s               │ 2.5s             │ 3s
 *  │ 60s              └──────────────────┴──────────────────┘
 *  ▼                  （限时状态到时自动回 IDLE）
 * SLEEPING ◄── 任意互动唤醒为对应状态
 * ```
 */

/** 互动动作：抚摸 / 喂食 / 玩耍 */
export enum PetAction {
  PET,
  FEED,
  PLAY,
}

/**
 * 精灵状态。限时状态（HAPPY/EATING/PLAYING）到时自动回 IDLE；
 * IDLE/SLEEPING 常驻，SLEEPING 仅由互动唤醒。
 */
export enum PetState {
  IDLE,
  HAPPY,
  EATING,
  PLAYING,
  SLEEPING,
}

/** 限时状态时长（ms，0 = 常驻态） */
export function petStateDuration(state: PetState): number {
  switch (state) {
    case PetState.HAPPY:
      return 2000
    case PetState.EATING:
      return 2500
    case PetState.PLAYING:
      return 3000
    default:
      return 0
  }
}

/** 是否为限时状态（到时自动回 idle） */
export function petStateIsTimed(state: PetState): boolean {
  return petStateDuration(state) > 0
}

/**
 * 进化阶段：小泡泡 → 泡沫精灵 → 彩虹泡泡，由累计互动次数驱动（计数持久化）。
 * threshold 为达到该阶段所需的累计互动次数。
 */
export enum PetStage {
  BUBBLE,
  FOAM_SPIRIT,
  RAINBOW_BUBBLE,
}

/** 阶段阈值与还原（对齐安卓 PetStage） */
export class PetStageTable {
  static readonly FOAM_THRESHOLD: number = 10
  static readonly RAINBOW_THRESHOLD: number = 20

  /** 累计互动次数对应的阶段（达阈值即进化，只升不降） */
  static forCount(count: number): PetStage {
    if (count >= PetStageTable.RAINBOW_THRESHOLD) {
      return PetStage.RAINBOW_BUBBLE
    }
    if (count >= PetStageTable.FOAM_THRESHOLD) {
      return PetStage.FOAM_SPIRIT
    }
    return PetStage.BUBBLE
  }

  /** 枚举名还原（脏数据回落 BUBBLE，保证不崩） */
  static fromName(name: string): PetStage {
    if (name === 'FOAM_SPIRIT') {
      return PetStage.FOAM_SPIRIT
    }
    if (name === 'RAINBOW_BUBBLE') {
      return PetStage.RAINBOW_BUBBLE
    }
    return PetStage.BUBBLE
  }

  static toName(stage: PetStage): string {
    switch (stage) {
      case PetStage.FOAM_SPIRIT:
        return 'FOAM_SPIRIT'
      case PetStage.RAINBOW_BUBBLE:
        return 'RAINBOW_BUBBLE'
      default:
        return 'BUBBLE'
    }
  }
}

/**
 * 放置回报档位：按距上次见面的时长分档，仅驱动一句文案（不伪造数值成长）。
 */
export enum AbsenceBucket {
  /** 首次见面（无上次见面记录） */
  FIRST_MEET,
  /** 刚见过（< 5 分钟） */
  JUST_NOW,
  /** 想你了（5 分钟 ~ 1 小时） */
  SHORT,
  /** 一直在等（1 ~ 24 小时） */
  LONG,
  /** 等了很久（> 24 小时） */
  VERY_LONG,
}

/** 档位阈值与换算（对齐安卓 AbsenceBucket） */
export class AbsenceBucketTable {
  static readonly SHORT_MS: number = 5 * 60 * 1000
  static readonly LONG_MS: number = 60 * 60 * 1000
  static readonly VERY_LONG_MS: number = 24 * 60 * 60 * 1000

  static fromElapsed(elapsedMs: number): AbsenceBucket {
    if (elapsedMs < AbsenceBucketTable.SHORT_MS) {
      return AbsenceBucket.JUST_NOW
    }
    if (elapsedMs < AbsenceBucketTable.LONG_MS) {
      return AbsenceBucket.SHORT
    }
    if (elapsedMs < AbsenceBucketTable.VERY_LONG_MS) {
      return AbsenceBucket.LONG
    }
    return AbsenceBucket.VERY_LONG
  }
}

/** 精灵快照（不可变；每次迁移整体替换） */
export class PetSnapshot {
  readonly state: PetState
  readonly stage: PetStage
  readonly interactionCount: number
  readonly lastAction: PetAction | null
  /** 当前状态起始时刻（ms），限时状态据此判到期 */
  readonly stateStartedAt: number
  /** 上次见面时刻（ms，0 = 从未见面），放置回报据此分档 */
  readonly lastSeenAt: number

  constructor(state: PetState = PetState.IDLE, stage: PetStage = PetStage.BUBBLE,
    interactionCount: number = 0, lastAction: PetAction | null = null,
    stateStartedAt: number = 0, lastSeenAt: number = 0) {
    this.state = state
    this.stage = stage
    this.interactionCount = interactionCount
    this.lastAction = lastAction
    this.stateStartedAt = stateStartedAt
    this.lastSeenAt = lastSeenAt
  }

  /** 整体替换式拷贝（对齐安卓 data class copy） */
  copy(state: PetState, stage: PetStage, interactionCount: number, lastAction: PetAction | null,
    stateStartedAt: number, lastSeenAt: number): PetSnapshot {
    return new PetSnapshot(state, stage, interactionCount, lastAction, stateStartedAt, lastSeenAt)
  }
}

/** 互动结果：接受（含是否进化）/ 防抖丢弃 */
export class PetInteractResult {
  readonly accepted: boolean
  readonly snapshot: PetSnapshot | null
  readonly evolved: boolean

  private constructor(accepted: boolean, snapshot: PetSnapshot | null, evolved: boolean) {
    this.accepted = accepted
    this.snapshot = snapshot
    this.evolved = evolved
  }

  /** 接受：新快照 + 本次是否进化（页面据此发触觉与进化动画） */
  static acceptedResult(snapshot: PetSnapshot, evolved: boolean): PetInteractResult {
    return new PetInteractResult(true, snapshot, evolved)
  }

  /** 同动作窗口内重复触发：不迁移、不计数、无触觉 */
  static debounced(): PetInteractResult {
    return new PetInteractResult(false, null, false)
  }
}

/** tick 结果：快照 + 是否发生状态迁移 + 是否刚入睡 */
export class PetTickResult {
  readonly snapshot: PetSnapshot
  readonly changed: boolean
  readonly fellAsleep: boolean

  constructor(snapshot: PetSnapshot, changed: boolean, fellAsleep: boolean) {
    this.snapshot = snapshot
    this.changed = changed
    this.fellAsleep = fellAsleep
  }
}

export class PetEngine {
  /** 同动作防抖窗口（ms）：窗口内重复触发视为连点，丢弃 */
  static readonly DEBOUNCE_MS: number = 800

  /** 闲置多久后入睡（ms） */
  static readonly IDLE_SLEEP_MS: number = 60000

  private snapshot: PetSnapshot
  /** 上一次触发的动作与时刻（防抖用；仅同动作在窗口内重复触发才丢弃） */
  private lastTriggerAction: PetAction | null = null
  private lastTriggerAt: number = 0

  constructor(now: number = Date.now()) {
    this.snapshot = new PetSnapshot(PetState.IDLE, PetStage.BUBBLE, 0, null, now, 0)
  }

  /** 当前快照（只读视图） */
  current(): PetSnapshot {
    return this.snapshot
  }

  /**
   * 从持久化恢复（阶段 / 累计互动次数 / 上次见面时刻）。
   * stateStartedAt 取上次见面时刻——「独处过久」继续计入入睡计时，
   * 用户隔了很久回来时精灵已在睡（放置回报的沉默表达）；无记录时取 now。
   */
  restore(stage: PetStage, interactionCount: number, lastSeenAt: number, now: number): void {
    this.snapshot = new PetSnapshot(
      PetState.IDLE,
      stage,
      interactionCount,
      null,
      lastSeenAt > 0 ? lastSeenAt : now,
      lastSeenAt,
    )
  }

  /**
   * 距上次见面的时长档位（调用方须在刷新 lastSeenAt 之前取，否则恒为 JUST_NOW）。
   * 仅驱动文案，不伪造数值成长。
   */
  absenceBucket(now: number = Date.now()): AbsenceBucket {
    const last: number = this.snapshot.lastSeenAt
    if (last <= 0) {
      return AbsenceBucket.FIRST_MEET
    }
    return AbsenceBucketTable.fromElapsed(Math.max(0, now - last))
  }

  /** 标记本次见面（刷新 lastSeenAt） */
  markSeen(now: number): void {
    this.snapshot = this.snapshot.copy(this.snapshot.state, this.snapshot.stage,
      this.snapshot.interactionCount, this.snapshot.lastAction, this.snapshot.stateStartedAt, now)
  }

  /**
   * 一次互动（抚摸 / 喂食 / 玩耍）。
   * 同动作在 DEBOUNCE_MS 窗口内重复触发 → Debounced，不迁移、不计数；
   * 不同动作或窗口外 → 迁移到对应限时状态，累计互动次数 +1，达阈值进化。
   */
  interact(action: PetAction, now: number = Date.now()): PetInteractResult {
    if (action === this.lastTriggerAction && now - this.lastTriggerAt < PetEngine.DEBOUNCE_MS) {
      return PetInteractResult.debounced()
    }
    this.lastTriggerAction = action
    this.lastTriggerAt = now

    const current: PetSnapshot = this.snapshot
    let newState: PetState
    if (action === PetAction.PET) {
      newState = PetState.HAPPY
    } else if (action === PetAction.FEED) {
      newState = PetState.EATING
    } else {
      newState = PetState.PLAYING
    }
    const newCount: number = current.interactionCount + 1
    const newStage: PetStage = PetStageTable.forCount(newCount)
    this.snapshot = current.copy(newState, newStage, newCount, action, now, now)
    return PetInteractResult.acceptedResult(this.snapshot, newStage !== current.stage)
  }

  /**
   * 时间推进：限时状态到时回 IDLE；IDLE 超过 IDLE_SLEEP_MS 入睡。
   * changed 为假时无迁移，页面不必重渲染。
   */
  tick(now: number = Date.now()): PetTickResult {
    const current: PetSnapshot = this.snapshot
    const elapsed: number = now - current.stateStartedAt
    if (petStateIsTimed(current.state) && elapsed >= petStateDuration(current.state)) {
      this.snapshot = current.copy(PetState.IDLE, current.stage, current.interactionCount,
        null, now, current.lastSeenAt)
      return new PetTickResult(this.snapshot, true, false)
    }
    if (current.state === PetState.IDLE && elapsed >= PetEngine.IDLE_SLEEP_MS) {
      this.snapshot = current.copy(PetState.SLEEPING, current.stage, current.interactionCount,
        null, now, current.lastSeenAt)
      return new PetTickResult(this.snapshot, true, true)
    }
    return new PetTickResult(current, false, false)
  }
}
