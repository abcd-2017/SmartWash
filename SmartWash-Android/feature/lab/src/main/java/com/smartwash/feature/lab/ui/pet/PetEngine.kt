package com.smartwash.feature.lab.ui.pet

/**
 * 泡泡精灵状态机内核 —— 纯 Kotlin，零 Compose / 零 Android 依赖，便于单测。
 *
 * 职责边界：
 * - 只负责「状态迁移 + 进化判定 + 防抖 + 放置回报分档」，不持有任何持久化与 UI 文案；
 * - 时钟经构造参数 [clock] 注入（默认 [System.currentTimeMillis]），单测可注入虚拟时钟；
 * - 每次迁移整体替换 [PetSnapshot]（不可变快照），VM 侧 StateFlow 发布给 UI。
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
class PetEngine(private val clock: () -> Long = System::currentTimeMillis) {

    private var snapshot = PetSnapshot(stateStartedAt = clock())

    /** 上一次触发的动作与时刻（防抖用；仅同动作在窗口内重复触发才丢弃） */
    private var lastTriggerAction: PetAction? = null
    private var lastTriggerAt: Long = 0L

    /** 当前快照（只读视图） */
    fun snapshot(): PetSnapshot = snapshot

    /**
     * 从持久化恢复（阶段 / 累计互动次数 / 上次见面时刻）。
     * [stateStartedAt] 取上次见面时刻——「独处过久」继续计入入睡计时，
     * 用户隔了很久回来时精灵已在睡（放置回报的沉默表达）；无记录时取 [now]。
     */
    fun restore(stage: PetStage, interactionCount: Int, lastSeenAt: Long, now: Long = clock()) {
        snapshot = PetSnapshot(
            stateStartedAt = if (lastSeenAt > 0L) lastSeenAt else now,
            stage = stage,
            interactionCount = interactionCount,
            lastSeenAt = lastSeenAt,
        )
    }

    /**
     * 距上次见面的时长档位（调用方须在刷新 lastSeenAt **之前**取，否则恒为 [AbsenceBucket.JUST_NOW]）。
     * 仅驱动文案，不伪造数值成长。
     */
    fun absenceBucket(now: Long = clock()): AbsenceBucket {
        val last = snapshot.lastSeenAt
        if (last <= 0L) return AbsenceBucket.FIRST_MEET
        return AbsenceBucket.fromElapsed((now - last).coerceAtLeast(0L))
    }

    /** 标记本次见面（刷新 lastSeenAt） */
    fun markSeen(now: Long = clock()) {
        snapshot = snapshot.copy(lastSeenAt = now)
    }

    /**
     * 一次互动（抚摸 / 喂食 / 玩耍）。
     * 同动作在 [DEBOUNCE_MS] 窗口内重复触发 → [PetInteractResult.Debounced]，不迁移、不计数；
     * 不同动作或窗口外 → 迁移到对应限时状态，累计互动次数 +1，达阈值进化。
     */
    fun interact(action: PetAction, now: Long = clock()): PetInteractResult {
        if (action == lastTriggerAction && now - lastTriggerAt < DEBOUNCE_MS) {
            return PetInteractResult.Debounced
        }
        lastTriggerAction = action
        lastTriggerAt = now

        val current = snapshot
        val newState = when (action) {
            PetAction.PET -> PetState.HAPPY
            PetAction.FEED -> PetState.EATING
            PetAction.PLAY -> PetState.PLAYING
        }
        val newCount = current.interactionCount + 1
        val newStage = PetStage.forCount(newCount)
        snapshot = current.copy(
            state = newState,
            stage = newStage,
            interactionCount = newCount,
            lastAction = action,
            stateStartedAt = now,
            lastSeenAt = now,
        )
        return PetInteractResult.Accepted(snapshot, evolved = newStage != current.stage)
    }

    /**
     * 时间推进：限时状态到时回 [PetState.IDLE]；IDLE 超过 [IDLE_SLEEP_MS] 入睡。
     * [PetTickResult.changed] 为假时无迁移，VM 不必发布新状态。
     */
    fun tick(now: Long = clock()): PetTickResult {
        val current = snapshot
        val elapsed = now - current.stateStartedAt
        if (current.state.isTimed && elapsed >= current.state.durationMs) {
            snapshot = current.copy(state = PetState.IDLE, lastAction = null, stateStartedAt = now)
            return PetTickResult(snapshot, changed = true, fellAsleep = false)
        }
        if (current.state == PetState.IDLE && elapsed >= IDLE_SLEEP_MS) {
            snapshot = current.copy(state = PetState.SLEEPING, lastAction = null, stateStartedAt = now)
            return PetTickResult(snapshot, changed = true, fellAsleep = true)
        }
        return PetTickResult(current, changed = false, fellAsleep = false)
    }

    companion object {
        /** 同动作防抖窗口（ms）：窗口内重复触发视为连点，丢弃 */
        const val DEBOUNCE_MS = 800L

        /** 闲置多久后入睡（ms） */
        const val IDLE_SLEEP_MS = 60_000L
    }
}

/** 互动动作：抚摸 / 喂食 / 玩耍 */
enum class PetAction { PET, FEED, PLAY }

/**
 * 精灵状态。限时状态（[HAPPY]/[EATING]/[PLAYING]）到时自动回 [IDLE]；
 * [IDLE]/[SLEEPING] 常驻，[SLEEPING] 仅由互动唤醒。
 */
enum class PetState(val durationMs: Long) {
    IDLE(0L),
    HAPPY(2_000L),
    EATING(2_500L),
    PLAYING(3_000L),
    SLEEPING(0L);

    /** 是否为限时状态（到时自动回 idle） */
    val isTimed: Boolean get() = durationMs > 0L
}

/**
 * 进化阶段：小泡泡 → 泡沫精灵 → 彩虹泡泡，由**累计互动次数**驱动（计数持久化）。
 * [threshold] 为达到该阶段所需的累计互动次数。
 */
enum class PetStage(private val threshold: Int) {
    BUBBLE(0),
    FOAM_SPIRIT(10),
    RAINBOW_BUBBLE(20);

    companion object {
        /** 累计互动次数对应的阶段（达阈值即进化，只升不降） */
        fun forCount(count: Int): PetStage = when {
            count >= RAINBOW_BUBBLE.threshold -> RAINBOW_BUBBLE
            count >= FOAM_SPIRIT.threshold -> FOAM_SPIRIT
            else -> BUBBLE
        }
    }
}

/**
 * 放置回报档位：按距上次见面的时长分档，仅驱动一句文案（不伪造数值成长）。
 * 阈值见 [AbsenceBucket.SHORT_MS] / [LONG_MS] / [VERY_LONG_MS]。
 */
enum class AbsenceBucket {
    /** 首次见面（无上次见面记录） */
    FIRST_MEET,

    /** 刚见过（< 5 分钟） */
    JUST_NOW,

    /** 想你了（5 分钟 ~ 1 小时） */
    SHORT,

    /** 一直在等（1 ~ 24 小时） */
    LONG,

    /** 等了很久（> 24 小时） */
    VERY_LONG;

    companion object {
        const val SHORT_MS = 5 * 60 * 1000L
        const val LONG_MS = 60 * 60 * 1000L
        const val VERY_LONG_MS = 24 * 60 * 60 * 1000L

        fun fromElapsed(elapsedMs: Long): AbsenceBucket = when {
            elapsedMs < SHORT_MS -> JUST_NOW
            elapsedMs < LONG_MS -> SHORT
            elapsedMs < VERY_LONG_MS -> LONG
            else -> VERY_LONG
        }
    }
}

/** 精灵快照（不可变；每次迁移整体替换） */
data class PetSnapshot(
    val state: PetState = PetState.IDLE,
    val stage: PetStage = PetStage.BUBBLE,
    val interactionCount: Int = 0,
    val lastAction: PetAction? = null,
    /** 当前状态起始时刻（ms），限时状态据此判到期 */
    val stateStartedAt: Long = 0L,
    /** 上次见面时刻（ms，0 = 从未见面），放置回报据此分档 */
    val lastSeenAt: Long = 0L,
)

/** 互动结果：接受（含是否进化）/ 防抖丢弃 */
sealed interface PetInteractResult {
    /** 接受：新快照 + 本次是否进化（页面据此发 SUCCESS 触觉与进化动画） */
    data class Accepted(val snapshot: PetSnapshot, val evolved: Boolean) : PetInteractResult

    /** 同动作窗口内重复触发：不迁移、不计数、无触觉 */
    data object Debounced : PetInteractResult
}

/** tick 结果：快照 + 是否发生状态迁移 + 是否刚入睡 */
data class PetTickResult(
    val snapshot: PetSnapshot,
    val changed: Boolean,
    val fellAsleep: Boolean,
)
