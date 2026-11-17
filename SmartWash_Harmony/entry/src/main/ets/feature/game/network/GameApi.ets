/**
 * 游戏中心域契约 —— 游戏库接口 + 白名单映射（对齐安卓 feature/game/network/GameApi.kt + GameConstant.kt）。
 *
 * 后端契约：SmartWash/src/main/java/com/smartwash/game/
 * - GET /web/auth/game/list  需 ROLE_USER，仅 enabled=1，按 sort 升序、id 升序，不分页（规模 <100）；
 * - 下发 GameVo 字段：gameKey/title/description/icon/color/category/badge/coverUrl/url
 *   （coverUrl 服务端缺列或未上传时为空，卡片三级兜底：服务端封面 → 本地 rawfile 封面 → 图标卡）。
 *
 * 严格模式：VO 用 interface + 可选字段（Jackson 可能省略 null 字段），消费侧经转换函数兜底，
 * 不依赖 class 直连 JSON。
 */

import { BaseResponse } from '../../../network/BaseResponse'
import { httpUtil } from '../../../network/HttpUtil'

/** 游戏库条目 VO（对齐后端 game/vo/GameVo.java 与安卓 GameVo，字段名逐一勿改） */
export interface GameVo {
  /** 业务唯一标识（game_config.game_key），兼作列表稳定 key；空值端上回退 game_{index} */
  gameKey?: string
  /** 游戏名（服务端文案） */
  title?: string
  /** 一句话玩法描述，可空 */
  description?: string | null
  /** 图标白名单 key（gamepad/arcade/puzzle/grid/board/word，未知兜底 default） */
  icon?: string | null
  /** 图标色组白名单 key（green/blue/orange/pink/teal/purple，未知兜底 green） */
  color?: string | null
  /** 分类白名单 key（puzzle/arcade/word/board/other，未知兜底 other） */
  category?: string | null
  /** 角标文案（如 新/热），空不显示 */
  badge?: string | null
  /** 封面直链（管理端上传 MinIO game-covers/ 前缀），空则端上用本地兜底封面 */
  coverUrl?: string | null
  /** 最终可玩地址（MinIO 对象直链或外部 https），端上零拼装 */
  url?: string
}

/**
 * 游戏分类枚举（后端 game_config.category 白名单：puzzle/arcade/word/board/other）。
 * 分类筛选 chips 只暴露四类（全部/益智/街机/文字/桌版），OTHER 仅在「全部」下展示。
 */
export enum GameCategory {
  PUZZLE = 'puzzle',
  ARCADE = 'arcade',
  WORD = 'word',
  BOARD = 'board',
  OTHER = 'other'
}

/** 分类筛选 chips 的展示顺序（不含 OTHER——无独立 chip） */
export const GAME_CHIP_ORDER: GameCategory[] = [
  GameCategory.PUZZLE,
  GameCategory.ARCADE,
  GameCategory.WORD,
  GameCategory.BOARD
]

/** 后端 category key → 枚举（大小写不敏感，未知兜底 OTHER） */
export function gameCategoryFromKey(key: string | null | undefined): GameCategory {
  const normalized = key !== undefined && key !== null ? key.toLowerCase() : ''
  switch (normalized) {
    case 'puzzle':
      return GameCategory.PUZZLE
    case 'arcade':
      return GameCategory.ARCADE
    case 'word':
      return GameCategory.WORD
    case 'board':
      return GameCategory.BOARD
    default:
      return GameCategory.OTHER
  }
}

/**
 * 游戏卡图标容器语义色组（规范 §2.1 六色浅底，与后端 color 白名单 key 一一对应）。
 * 未知 color key 兜底 GREEN（契约：未知值显示默认样式）。
 */
export enum GameTint {
  GREEN = 'green',
  BLUE = 'blue',
  ORANGE = 'orange',
  PINK = 'pink',
  TEAL = 'teal',
  PURPLE = 'purple'
}

/** 后端 color key → 色组（大小写不敏感，未知兜底 GREEN） */
export function gameTintFromKey(key: string | null | undefined): GameTint {
  const normalized = key !== undefined && key !== null ? key.toLowerCase() : ''
  switch (normalized) {
    case 'blue':
      return GameTint.BLUE
    case 'orange':
      return GameTint.ORANGE
    case 'pink':
      return GameTint.PINK
    case 'teal':
      return GameTint.TEAL
    case 'purple':
      return GameTint.PURPLE
    default:
      return GameTint.GREEN
  }
}

/**
 * 后端 icon 白名单 key → sys.symbol 图标映射（规范 §4.1：功能图标保持系统符号库）。
 * 符号名仅取仓内已验证清单（与 feature/workbench/api.ets 同款口径），未知 key 与 default
 * 兜底「应用格」图标（对齐安卓 Icons.Default.Apps）。
 */
export function gameIconFromKey(key: string | null | undefined): Resource {
  const normalized = key !== undefined && key !== null ? key.toLowerCase() : ''
  switch (normalized) {
    case 'gamepad':
      return $r('sys.symbol.hand_tap')
    case 'arcade':
      return $r('sys.symbol.circle_below_local_filled')
    case 'puzzle':
      return $r('sys.symbol.questionmark_circle')
    case 'grid':
      return $r('sys.symbol.square_grid_2x2')
    case 'board':
      return $r('sys.symbol.creditcard')
    case 'word':
      return $r('sys.symbol.book_pages')
    default:
      return $r('sys.symbol.square_grid_2x2')
  }
}

/**
 * 游戏卡模型（后端 GameVo 的客户端映射层，由页面完成白名单映射）。
 * 对齐安卓 GameItem：gameKey 兼作网格稳定 key；url 为空串时点击不响应。
 */
export interface GameItem {
  /** 业务唯一标识（后端 gameKey），兼作网格稳定 key */
  gameKey: string
  /** 游戏名（服务端文案） */
  title: string
  /** 一句话玩法描述（服务端文案，可为空串） */
  description: string
  /** 语义图标（sys.symbol，白名单映射产物） */
  icon: Resource
  /** 图标容器语义色组（白名单映射产物） */
  tint: GameTint
  /** 分类（本地筛选 chips 依据） */
  category: GameCategory
  /** 角标文案（如 新/热），空串不显示 */
  badge: string
  /** 封面直链（服务端下发；空串走本地兜底封面） */
  coverUrl: string
  /** 本地兜底封面（resources/rawfile/covers/<gameKey>.png，随包内置 30 款） */
  localCover: Resource
  /** 最终可玩地址（后端下发即用，端上零拼装；空串不响应点击） */
  url: string
}

/**
 * 本地兜底封面资源（resources/rawfile/covers/<gameKey>.png，与仓库
 * static/games-library/<gameKey>/<gameKey>.png 同图随包内置）。
 * $rawfile 接受动态串，新增游戏无需改端上代码；本地无此游戏时由页面 onError 走图标兜底。
 */
export function localCoverRes(gameKey: string): Resource {
  return $rawfile(`covers/${gameKey}.png`)
}

/**
 * 后端游戏 VO → 卡片模型（严格模式：逐字段显式兜底，不依赖 class 直连 JSON）。
 * gameKey 兼作网格稳定 key（空值防御回退 index key，对齐安卓 toItem）。
 */
export function gameVoToItem(vo: GameVo, index: number): GameItem {
  const gameKey = vo.gameKey ?? ''
  return {
    gameKey: gameKey.length > 0 ? gameKey : `game_${index}`,
    title: vo.title ?? '',
    description: vo.description ?? '',
    icon: gameIconFromKey(vo.icon),
    tint: gameTintFromKey(vo.color),
    category: gameCategoryFromKey(vo.category),
    badge: vo.badge ?? '',
    coverUrl: vo.coverUrl ?? '',
    localCover: localCoverRes(gameKey.length > 0 ? gameKey : `game_${index}`),
    url: vo.url ?? ''
  }
}

/**
 * gameKey 稳定性防御（对齐安卓 withUniqueKeys）：服务端 game_key 理论唯一，
 * 但网格按 gameKey 作 ForEach key，重复会导致复用错乱——重复时追加序号去重。
 */
export function withUniqueKeys(games: Array<GameItem>): Array<GameItem> {
  const seen = new Map<string, number>()
  return games.map((game: GameItem): GameItem => {
    const count = seen.get(game.gameKey) ?? 0
    seen.set(game.gameKey, count + 1)
    if (count === 0) {
      return game
    }
    return {
      gameKey: `${game.gameKey}_${count + 1}`,
      title: game.title,
      description: game.description,
      icon: game.icon,
      tint: game.tint,
      category: game.category,
      badge: game.badge,
      coverUrl: game.coverUrl,
      localCover: game.localCover,
      url: game.url
    }
  })
}

/**
 * 游戏库全量列表（需登录；请求拦截器按 /web 前缀注入 Bearer Token）。
 * 规模 <100 不分页，端上按服务端 sort 序渲染。
 */
export async function getGameList(): Promise<BaseResponse<Array<GameVo>>> {
  return httpUtil.get<Array<GameVo>>('/web/auth/game/list')
}

// ========== 游戏数据桥接口（H5 经原生桥代理，需 ROLE_USER；对齐安卓 GameBridgeApi.kt） ==========
// H5 不持有 token，一律由原生桥带 Bearer 请求 /web/auth/game/* 后回传（见 GAME-THEME.md §5.2）。

/** 存档视图（对齐后端 GameStateVo；无存档时 stateJson 为 null） */
export interface GameStateVo {
  gameKey?: string
  stateJson?: string | null
  schemaVer?: number | null
  updatedAt?: string | null
}

/** 单个关卡（对齐后端 GameLevelVo） */
export interface GameLevelVo {
  levelNo?: number
  name?: string | null
  dataJson?: string
  difficulty?: number | null
  sort?: number
}

/** 关卡下发视图（对齐后端 GameLevelsVo） */
export interface GameLevelsVo {
  gameKey?: string
  version?: number
  levels?: Array<GameLevelVo>
}

/** 排行榜条目（对齐后端 GameLeaderboardVo.EntryVo） */
export interface GameLeaderboardEntryVo {
  rank?: number
  userId?: number
  maskedPhone?: string | null
  avatar?: string | null
  metricValue?: number
  metricExtra?: number | null
}

/** 我的排名（对齐后端 GameLeaderboardVo.MyRankVo） */
export interface GameMyRankVo {
  rank?: number | null
  metricValue?: number | null
}

/** 排行榜视图（对齐后端 GameLeaderboardVo） */
export interface GameLeaderboardVo {
  gameKey?: string
  metricLabel?: string | null
  metricOrder?: string
  entries?: Array<GameLeaderboardEntryVo>
  my?: GameMyRankVo | null
}

/** 存档写入请求体（对齐后端 GameStateUpdateFrom，字段名逐一勿改） */
export interface GameStateUpdateBody {
  gameKey: string
  stateJson: string
  schemaVer?: number
}

/** 成绩上报请求体（对齐后端 GameScoreReportFrom，字段名逐一勿改） */
export interface GameScoreReportBody {
  gameKey: string
  metricValue: number
  metricExtra?: number
  metricJson?: string
}

/** GET 查询参数构造（ArkTS 禁裸对象字面量作参数，按索引赋值） */
function gameParams(gameKey: string, limit?: number): Record<string, Object> {
  const params: Record<string, Object> = {}
  params['gameKey'] = gameKey
  if (limit !== undefined) {
    params['limit'] = limit
  }
  return params
}

/** 读存档：无存档时 data.stateJson 为 null（首次进入正常态） */
export async function getGameState(gameKey: string): Promise<BaseResponse<GameStateVo>> {
  return httpUtil.get<GameStateVo>('/web/auth/game/state', gameParams(gameKey))
}

/** 写存档：upsert，stateJson 游戏自定义（服务端不解析语义） */
export async function saveGameState(body: GameStateUpdateBody): Promise<BaseResponse<GameStateVo>> {
  return httpUtil.put<GameStateVo>('/web/auth/game/state', body)
}

/** 上报成绩：后端取历史最佳（方向由 game_config.metric_order 决定） */
export async function reportGameScore(body: GameScoreReportBody): Promise<BaseResponse<string>> {
  return httpUtil.post<string>('/web/auth/game/score', body)
}

/** 排行榜 TOP N（含我的排名；limit 由桥归一化，缺省 20） */
export async function getGameLeaderboard(gameKey: string,
  limit: number): Promise<BaseResponse<GameLeaderboardVo>> {
  return httpUtil.get<GameLeaderboardVo>('/web/auth/game/leaderboard', gameParams(gameKey, limit))
}

/** 关卡下发：启用中的关卡列表 + 版本号（端上据版本号判断缓存是否失效） */
export async function getGameLevels(gameKey: string): Promise<BaseResponse<GameLevelsVo>> {
  return httpUtil.get<GameLevelsVo>('/web/auth/game/levels', gameParams(gameKey))
}
