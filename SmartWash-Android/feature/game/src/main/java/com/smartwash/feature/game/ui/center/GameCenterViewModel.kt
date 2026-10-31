package com.smartwash.feature.game.ui.center

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.game.GameCategory
import com.smartwash.feature.game.GameTint
import com.smartwash.feature.game.R
import com.smartwash.feature.game.gameIconFromKey
import com.smartwash.feature.game.model.GameItem
import com.smartwash.feature.game.network.GameVo
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
 * 游戏中心 ViewModel。
 *
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

    /** 分类过滤后的列表（本地过滤，无分页） */
    val filteredGames: StateFlow<List<GameItem>> =
        combine(_games, _selectedCategory) { games, category ->
            if (category == null) games else games.filter { it.category == category }
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
                _games.value = repository.getGameList()
                    .mapIndexed { index, vo -> vo.toItem(index) }
                    .withUniqueKeys()
                _listState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _listState.value = RequestState.Error(R.string.game_error_load)
            }
        }
    }

    /** 切换分类筛选（传 null = 全部） */
    fun selectCategory(category: GameCategory?) {
        _selectedCategory.value = category
    }

    // ---------- 映射与兜底 ----------

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
}
