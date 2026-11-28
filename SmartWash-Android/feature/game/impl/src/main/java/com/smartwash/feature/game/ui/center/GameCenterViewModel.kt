package com.smartwash.feature.game.ui.center

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.game.GameCategory
import com.smartwash.feature.game.GameTint
import com.smartwash.feature.game.R
import com.smartwash.feature.game.filterGames
import com.smartwash.feature.game.gameIconFromKey
import com.smartwash.feature.game.model.GameItem
import com.smartwash.feature.game.api.network.GameVo
import com.smartwash.feature.game.repository.GameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 数据流：init 拉取 `GET /web/auth/game/list`（经 [GameRepository]）——
 * Success → GameVo 映射 [GameItem]（icon/color/category 白名单枚举映射，未知值兜底）；
 * 失败 → Error 态（页面展示错误重试）；空列表 → Success + 空列表（页面展示空态）。
 * 数据源状态用 [RequestState] 暴露给页面做骨架屏。
 *
 * 分类筛选为纯本地过滤（列表全量 <100 不分页，方案 §六）：
 * [selectedCategory] 为 null 表示「全部」，[filteredGames] 据此派生。
 */
@HiltViewModel
class GameCenterViewModel @Inject constructor(
    private val repository: GameRepository,
) : ViewModel() {

    /** 全量游戏列表（服务端下发顺序即后端 sort 序） */
    private val _games = MutableStateFlow<List<GameItem>>(emptyList())

    /** 数据源状态（Idle/Loading/Success/Error）：Loading/Idle 且列表为空时页面展示骨架屏 */
    private val _listState = MutableStateFlow<RequestState>(RequestState.Idle)
    val listState: StateFlow<RequestState> = _listState.asStateFlow()

    /** 当前筛选分类（null = 全部；OTHER 不在 chips 中，仅在「全部」下展示） */
    private val _selectedCategory = MutableStateFlow<GameCategory?>(null)
    val selectedCategory: StateFlow<GameCategory?> = _selectedCategory.asStateFlow()

    /** 搜索关键词（空 = 非搜索态；搜索为本地标题包含过滤，大小写不敏感、去首尾空格） */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * 分类 + 搜索过滤后的列表（本地过滤，无分页，逻辑见 [filterGames]）：
     * - 搜索态（关键词非空）：跨分类对全量列表按标题包含过滤（对齐工作台/菜谱库「搜索即全库」，
     *   分类 chips 让位，避免「chip 选中却看似失效」的困惑）；
     * - 非搜索态：按选中分类过滤（null = 全部）。
     */
    val filteredGames: StateFlow<List<GameItem>> =
        combine(_games, _selectedCategory, _searchQuery) { games, category, query ->
            filterGames(games, category, query)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    init {
        loadGames()
    }

    /** 拉取游戏列表；失败进 Error 态（页面给重试入口），空列表视为 Success 空态 */
    fun loadGames() {
        if (_listState.value is RequestState.Loading) return
        viewModelScope.launch {
            _listState.value = RequestState.Loading
            try {
                val games = repository.getGameList()
                    .mapIndexed { index, vo -> vo.toItem(index) }
                    .withUniqueKeys()
                _games.value = games
                if (games.isEmpty()) {
                    AppLogger.debug(TAG, "loadGames: 游戏列表为空（后端未上架或未配置）")
                }
                _listState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error(TAG, "GameCenterViewModel.loadGames: ${e.message}", e)
                _listState.value = RequestState.Error(R.string.game_error_load)
            }
        }
    }

    /** 切换分类筛选（传 null = 全部） */
    fun selectCategory(category: GameCategory?) {
        _selectedCategory.value = category
    }

    /**
     * 搜索关键词回写（页面搜索框直连）。
     * 本地内存过滤，输入即出结果，无需防抖（防抖用于服务端请求场景，如菜谱库）。
     */
    fun onSearchChange(query: String) {
        _searchQuery.value = query
    }

    /** 后端游戏 VO → 卡片模型：gameKey 兼作列表稳定 key（空值防御回退 index key） */
    private fun GameVo.toItem(index: Int): GameItem = GameItem(
        gameKey = gameKey.takeIf { it.isNotBlank() } ?: "game_$index",
        title = title,
        description = description.orEmpty(),
        icon = gameIconFromKey(icon),
        tint = GameTint.fromKey(color),
        category = GameCategory.fromKey(category),
        badge = badge?.takeIf { it.isNotBlank() },
        url = url,
        coverUrl = coverUrl?.takeIf { it.isNotBlank() },
    )

    /**
     * gameKey 稳定性防御：服务端 game_key 理论唯一（库表唯一键），但网格按 gameKey
     * 作 LazyColumn key，重复会直接崩溃——重复时追加序号去重，宁可展示也别崩
     * （对齐工作台 withUniqueKeys 防御）。
     */
    private fun List<GameItem>.withUniqueKeys(): List<GameItem> {
        val seen = mutableMapOf<String, Int>()
        return map { game ->
            val count = seen.merge(game.gameKey, 1, Int::plus) ?: 1
            if (count == 1) game else game.copy(gameKey = "${game.gameKey}_$count")
        }
    }

    private companion object {
        const val TAG = "GameCenterViewModel"
    }
}
