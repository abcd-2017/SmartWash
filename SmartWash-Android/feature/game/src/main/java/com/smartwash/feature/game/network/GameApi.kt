package com.smartwash.feature.game.network

import androidx.annotation.Keep
import com.smartwash.common.network.annotation.RequireAuthorization
import com.smartwash.common.utils.model.ApiResult
import retrofit2.http.GET

/**
 * 游戏库接口 —— 契约见 docs/plans/05-游戏中心与H5小游戏方案.md §五（用户端仅此一个端点）。
 * endpoint 走相对路径，复用 :common:network 注入的 Retrofit（baseUrl 即 BuildConfig.BASE_URL），
 * 禁止硬编码地址；鉴权/错误转译拦截器经 NetworkModule 自动生效。
 */
interface GameApi {

    /**
     * 游戏库全量列表（enabled=1 按 sort 升序，规模 <100 不分页，
     * 对齐 workbench entries 语义）。
     */
    @GET("web/auth/game/list")
    @RequireAuthorization
    suspend fun getGameList(): ApiResult<List<GameVo>>
}

/**
 * 游戏条目 VO（字段与后端 GameConfig 下发结构一一对应，见方案 §五）：
 * icon/color/category 为白名单 key，端上经 GameConstant 映射，未知值兜底默认样式。
 */
@Keep
data class GameVo(
    /** 游戏唯一标识（业务 key，兼作端上列表稳定 key） */
    val gameKey: String = "",
    val title: String = "",
    /** 一句话玩法描述，可空 */
    val description: String? = null,
    /** 图标白名单 key（gamepad/arcade/puzzle/grid/board/word…，未知值端上兜底） */
    val icon: String? = null,
    /** 图标色组 key（green/blue/orange/pink/teal/purple，未知值端上兜底 GREEN） */
    val color: String? = null,
    /** 分类 key（puzzle/arcade/word/board/other，未知值端上兜底 other） */
    val category: String? = null,
    /** 角标文案（新/热），空不显示 */
    val badge: String? = null,
    /** 最终可玩地址（上传包走后端转发 https，外链为外部 https；端上零拼装） */
    val url: String = "",
)
