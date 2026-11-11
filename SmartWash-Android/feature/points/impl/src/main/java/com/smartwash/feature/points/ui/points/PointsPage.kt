package com.smartwash.feature.points.ui.points

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.ButtonSpinner
import com.smartwash.common.ui.components.DrumMark
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SkeletonBlock
import com.smartwash.common.ui.components.SmartWashAsyncImage
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.theme.AuthCtaText
import com.smartwash.common.ui.theme.GlassInput
import com.smartwash.common.ui.theme.OnImageSecondary
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.DURATION_CARD_MS
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pressable
import com.smartwash.feature.points.PointsReason
import com.smartwash.feature.points.R
import com.smartwash.feature.points.api.model.LeaderboardEntryVo
import com.smartwash.feature.points.api.model.MyRankVo
import com.smartwash.feature.points.api.model.PointLedgerItemVo
import com.smartwash.feature.points.api.model.PointsLeaderboardVo
import com.smartwash.feature.points.api.model.PointsOverviewVo
import com.smartwash.feature.points.domain.RankTier
import com.smartwash.feature.points.domain.formatLedgerTime
import com.smartwash.feature.points.domain.parseLeaderboardMonth
import com.smartwash.feature.points.domain.rankTier
import com.smartwash.feature.points.domain.streakCycleProgress

/** 积分页内容区三态：首载骨架 / 首载失败 / 内容（签到后静默刷新不回骨架） */
private enum class PointsContentState { Skeleton, Error, Content }

/**
 * 签到有礼页（扩展提案功能 17）：签到 + 余额 + 月榜 + 最近流水单页。
 *
 * 布局（规范 §1 每屏一个锚点）：Hero 余额卡（品牌渐变 + 余额大字 36sp = 唯一锚点，
 * 签到 CTA 与连签进度同卡）→ 本月排行榜卡（TOP 榜 + 我的排名行）→ 积分明细卡
 * （reason 文案 + delta 正负色 + 时间）。榜单请求独立于整页，失败仅榜单卡内小态。
 */
@Composable
fun PointsPage(
    navController: NavHostController,
    pointsViewModel: PointsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val view = currentView()
    val loadState by pointsViewModel.loadState.collectAsState()
    val overview by pointsViewModel.overview.collectAsState()
    val leaderboardState by pointsViewModel.leaderboardState.collectAsState()
    val leaderboard by pointsViewModel.leaderboard.collectAsState()
    val signInState by pointsViewModel.signInState.collectAsState()
    val lastSignInResult by pointsViewModel.lastSignInResult.collectAsState()

    // 首载：概览与月榜并行独立（榜单失败不拖垮签到/余额/流水区块）
    LaunchedEffect(Unit) {
        pointsViewModel.load()
        pointsViewModel.loadLeaderboard()
    }

    // 签到结果反馈（副作用统一在 LaunchedEffect，规范禁组合期副作用）：
    // 成功 → SUCCESS 触感 + 「签到成功 +N」；幂等命中（双端并发场景）→ 「今日已签到」；
    // 失败 → ERROR 触感 + 具体错误文案（ResponseInterceptor 转译的 data 语义优先）
    LaunchedEffect(signInState) {
        when (val state = signInState) {
            is RequestState.Success -> {
                val result = lastSignInResult
                view.performHaptic(HapticEffect.SUCCESS)
                val message = if (result?.isAlreadySigned == true) {
                    context.getString(R.string.points_sign_in_already)
                } else {
                    context.getString(R.string.points_sign_in_success_format, result?.safePointsAwarded ?: 0)
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                pointsViewModel.resetSignInState()
            }

            is RequestState.Error -> {
                view.performHaptic(HapticEffect.ERROR)
                Toast.makeText(context, state.getMessage(context), Toast.LENGTH_SHORT).show()
                pointsViewModel.resetSignInState()
            }

            else -> {}
        }
    }

    // 内容三态：概览未就绪（Idle/Loading）走骨架；概览失败走整页错误态；
    // 签到后静默刷新不置 Loading（overview 非空即保留内容）
    val contentState = when {
        overview == null && (loadState is RequestState.Idle || loadState is RequestState.Loading) ->
            PointsContentState.Skeleton

        overview == null && loadState is RequestState.Error -> PointsContentState.Error

        else -> PointsContentState.Content
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(title = stringResource(R.string.points_title), onBack = { navController.navigateUp() })

            // 骨架 ↔ 内容交叉淡变；reduced motion 经 motionSpec 降级为 180ms 纯淡变
            Crossfade(
                targetState = contentState,
                animationSpec = motionSpec(enterTween(DURATION_CARD_MS)),
                label = "pointsContent",
            ) { state ->
                when (state) {
                    PointsContentState.Skeleton -> LoadingState(modifier = Modifier.fillMaxSize())

                    // 首载失败：说明发生了什么（ResponseInterceptor 转译文案优先）+ 重试
                    PointsContentState.Error -> EmptyState(
                        // EmptyState 只渲染滚筒圆母题（§3.2 禁裸图标着色）：icon 不渲染，仅占位
                        icon = Icons.Default.Refresh,
                        message = (loadState as? RequestState.Error)?.getMessage(context)
                            ?: stringResource(R.string.points_load_failed),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = AppDimens.pagePadding),
                        action = {
                            AppButton(
                                small = true,
                                text = stringResource(R.string.points_action_retry),
                                onClick = { pointsViewModel.load() },
                                variant = AppButtonVariant.SECONDARY,
                            )
                        },
                    )

                    PointsContentState.Content -> PointsContent(
                        overview = overview ?: PointsOverviewVo(),
                        leaderboard = leaderboard,
                        leaderboardState = leaderboardState,
                        signInLoading = signInState is RequestState.Loading,
                        onSignIn = { pointsViewModel.signIn() },
                        onRetryLeaderboard = { pointsViewModel.loadLeaderboard() },
                    )
                }
            }
        }
    }
}

/** 内容区：LazyColumn 分区（hero / 榜单 / 明细），item key 全量给出（Compose 硬规则） */
@Composable
private fun PointsContent(
    overview: PointsOverviewVo,
    leaderboard: PointsLeaderboardVo?,
    leaderboardState: RequestState,
    signInLoading: Boolean,
    onSignIn: () -> Unit,
    onRetryLeaderboard: () -> Unit,
) {
    // 底部安全区：enableEdgeToEdge 后壳层无全局 insets 兜底，滚动末尾在区块间距之上叠加
    // 导航栏高度避让（audit-20 #6；与 PageHeader 的 statusBarsPadding 对称，RechargePage 同款先例）
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AppDimens.pagePadding,
            end = AppDimens.pagePadding,
            bottom = AppDimens.spaceXl + bottomInset,
        ),
    ) {
        item(key = "hero") {
            PointsHeroCard(
                overview = overview,
                signInLoading = signInLoading,
                onSignIn = onSignIn,
                // 顶距 12 对齐优惠券页 AppPillTabRow 的 PageHeader 节奏补偿先例（PageHeader 自带 bottom 6，合计 18 ≈ 卡间距）
                modifier = Modifier.padding(top = AppDimens.spaceSm),
            )
        }
        item(key = "leaderboard") {
            LeaderboardSection(
                leaderboard = leaderboard,
                leaderboardState = leaderboardState,
                onRetry = onRetryLeaderboard,
                modifier = Modifier.padding(top = AppDimens.sectionSpacing),
            )
        }
        // 流水区块恒驻：空时卡内空态引导（audit-20 #7——此前新用户流水为空时整个区块消失，
        // 页面无引导；key 恒定也避免条件 item 引发的布局跳变）
        item(key = "ledger") {
            LedgerSection(
                items = overview.safeRecentLedger,
                modifier = Modifier.padding(top = AppDimens.sectionSpacing),
            )
        }
    }
}

/**
 * Hero 余额卡（规范 §3.1 Hero 变体：品牌渐变底 + 白字 + 白色 CTA 胶囊，每页至多 1 个）：
 * 余额大字 36sp（tnum）= 每页唯一锚点；连签进度条为静态填充（真实 streak 数据驱动，
 * 非动效，无 reduced-motion 负担）；签到 CTA 已签置灰禁点。
 */
@Composable
private fun PointsHeroCard(
    overview: PointsOverviewVo,
    signInLoading: Boolean,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val signed = overview.isSignedToday
    val cycle = streakCycleProgress(overview.safeNextBonusIn)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.radiusXl),
        // hero 无描边无投影，层级由渐变自身承担（规范 §2.4 豁免）
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .background(
                    brush = Brush.linearGradient(
                        listOf(
                            AppColors.colorScheme.heroGradientStart,
                            AppColors.colorScheme.heroGradientEnd,
                        )
                    ),
                    shape = RoundedCornerShape(AppDimens.radiusXl),
                )
                // audit-20 #1（首页 U-101 同款回归）：次级文字白 78% 压渐变浅板 2.73:1、深板亮端
                // #5BC894 上仅 1.77:1，违反 §10 正文 ≥4.5:1。复刻首页黑纱修法（IndexPage U-101）
                // 并改纯白文字；报告初拟终点「黑 8~0%」逐点复核不足（深板亮端白字达标需 ≥34% 纱）
                // 故上调至 35%——与渐变同对角线，浅板 5.13–8.10:1 / 深板 4.62–5.13:1，全部达标
                .background(
                    brush = Brush.linearGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.20f),
                            Color.Black.copy(alpha = 0.35f),
                        )
                    ),
                    shape = RoundedCornerShape(AppDimens.radiusXl),
                )
                .padding(AppDimens.cardPadding)
        ) {
            Text(
                text = stringResource(R.string.points_balance_label),
                style = MaterialTheme.typography.labelMedium,
                // hero 次级文字改纯白，由黑纱保证 ≥4.5:1（audit-20 #1）；白 78% 仅留已签禁用 CTA
                color = Color.White,
            )
            Text(
                text = "${overview.safeBalance}",
                style = AppTextStyles.DataLarge,
                color = Color.White,
                modifier = Modifier.padding(top = AppDimens.spaceXxs),
            )
            Text(
                text = stringResource(R.string.points_total_earned_format, overview.safeTotalEarned),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier.padding(top = AppDimens.spaceXxs),
            )

            if (overview.safeStreak > 0) {
                Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                Text(
                    text = stringResource(R.string.points_streak_format, overview.safeStreak) +
                        " · " + stringResource(R.string.points_next_bonus_format, overview.safeNextBonusIn),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFeatureSettings = "tnum",
                    ),
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceXs))
                // 连签周期进度条：静态填充无动画。轨道高 6dp 为画板口径登记（细进度条专用尺寸，
                // 4dp 过细 / 8dp 过粗，组件维度无 AppDimens 档位，同 top=spaceSm 节奏注释的留痕写法）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(AppDimens.radiusFull))
                        // 轨道改恒定 18% 黑（audit-20 #13）：原 GlassInput 白 30% 轨道在深板亮端上
                        // 与白填充仅 ~1.6:1 近乎隐形；改后两板填充/轨道 ≥3:1 图形级（叠 hero 纱后余量更大）
                        .background(Color.Black.copy(alpha = 0.18f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(cycle.currentInCycle.toFloat() / cycle.stepDays)
                            .height(6.dp)
                            .clip(RoundedCornerShape(AppDimens.radiusFull))
                            .background(Color.White),
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppDimens.spaceMd))
            // 签到 CTA — hero 白色胶囊（规范 §3.1 Hero 变体）；已签置灰禁点；
            // 防重 = Loading 禁点 + pressable 防抖之外由 ViewModel Loading 忽略兜底
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(AppDimens.radiusFull))
                    .background(if (signed) GlassInput else Color.White)
                    .then(
                        if (!signed && !signInLoading) {
                            Modifier.pressable(onClick = onSignIn)
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // 文字 ↔ spinner 交叉淡变（规范 7.8 CrossfadeAlt 档，reduced motion 内置降级）
                Crossfade(
                    targetState = signInLoading,
                    animationSpec = tween(CROSSFADE_ALT_MS),
                    label = "signInCta",
                ) { loading ->
                    if (loading) {
                        ButtonSpinner(
                            size = 14.dp,
                            strokeWidth = 1.5.dp,
                            color = AuthCtaText,
                        )
                    } else {
                        Text(
                            text = stringResource(
                                if (signed) R.string.points_signed_today else R.string.points_sign_in
                            ),
                            // §3.3 按钮文字 15sp/600（组件级规格）
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFeatureSettings = "tnum",
                            ),
                            // 未签 = 白底 CTA 文字绿（AuthCtaText，AA 已验证）；已签置灰 = 白 78%
                            color = if (signed) OnImageSecondary else AuthCtaText,
                        )
                    }
                }
            }
        }
    }
}

/** 本月排行榜区块：区块标题 + 月份角标 + TOP 榜卡（含我的排名行）；失败卡内小态 */
@Composable
private fun LeaderboardSection(
    leaderboard: PointsLeaderboardVo?,
    leaderboardState: RequestState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val entries = leaderboard?.safeEntries.orEmpty()
    val monthLabel = parseLeaderboardMonth(leaderboard?.month)
        ?.let { (year, month) -> stringResource(R.string.points_leaderboard_month_format, year, month) }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.points_leaderboard_title),
                style = AppTextStyles.SectionTitle,
                // 区块标题属主文案层级，对齐全 app SectionTitle=textPrimary 惯例（audit-20 #8）
                color = AppColors.colorScheme.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (monthLabel != null) {
                Text(
                    text = monthLabel,
                    style = MaterialTheme.typography.labelMedium,
                    // 月份是榜单口径信息非装饰角标，textTertiary 压页面底仅 2.08:1 触 §10 红线（audit-20 #2）
                    color = AppColors.colorScheme.textSecondary,
                )
            }
        }
        Spacer(modifier = Modifier.height(AppDimens.spaceXs))
        GroupCard(contentPadding = PaddingValues(horizontal = AppDimens.cardPadding)) {
            when {
                // 首载/重试进行中且无数据：卡内骨架占位（audit-20 #5——此前 Idle/Loading 落入空榜
                // 分支闪「本月还没有人上榜」误导；entries 非空的静默刷新不回骨架）。行形状对齐真实
                // 榜单行（§3.6 骨架形状 = 内容形状），reduced motion 由 SkeletonBlock 内置静态降级
                (leaderboardState is RequestState.Idle || leaderboardState is RequestState.Loading) &&
                    entries.isEmpty() -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AppDimens.cardPadding),
                ) {
                    repeat(2) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SkeletonBlock(
                                modifier = Modifier.size(28.dp),
                                shape = CircleShape,
                            )
                            Spacer(modifier = Modifier.width(AppDimens.spaceSm))
                            SkeletonBlock(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(AppDimens.spaceMd),
                            )
                            Spacer(modifier = Modifier.width(AppDimens.spaceMd))
                            SkeletonBlock(
                                modifier = Modifier.size(
                                    width = AppDimens.spaceXl,
                                    height = AppDimens.spaceMd,
                                ),
                            )
                        }
                    }
                }

                // 首载失败且无数据：卡内错误小态（说明 + 重试），不伪装成空榜
                leaderboardState is RequestState.Error && entries.isEmpty() -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AppDimens.cardPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = (leaderboardState as? RequestState.Error)?.getMessage(context)
                            ?: stringResource(R.string.points_leaderboard_load_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.colorScheme.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                    AppButton(
                        text = stringResource(R.string.points_action_retry),
                        onClick = onRetry,
                        variant = AppButtonVariant.SECONDARY,
                        small = true,
                    )
                }

                // 空榜：滚筒母题空态（规范 §3.6 禁灰色图标了事）
                entries.isEmpty() -> EmptyState(
                    icon = Icons.Default.Refresh,
                    message = stringResource(R.string.points_leaderboard_empty),
                )

                else -> Column {
                    entries.forEachIndexed { index, entry ->
                        if (index > 0) {
                            // 发丝线只在同一容器内部行之间（规范 §1.3）
                            HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.hairline)
                        }
                        LeaderboardRow(entry)
                    }
                    // 我的排名行固定卡底（未上榜显示引导文案）
                    HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.hairline)
                    MyRankRow(my = leaderboard?.my)
                }
            }
        }
    }
}

/** 榜单行（56dp）：名次（TOP3 品牌色强调）+ 头像（空则滚筒母题占位）+ 打码手机号 + 月积分 */
@Composable
private fun LeaderboardRow(entry: LeaderboardEntryVo) {
    val tier = rankTier(entry.rank)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "#${entry.safeRank}",
            style = AppTextStyles.AmountMedium,
            color = if (tier == RankTier.NORMAL) {
                AppColors.colorScheme.textSecondary
            } else {
                // TOP3 名次与积分值统一 primaryDark（audit-20 #9：同排语义对同色档）
                AppColors.colorScheme.primaryDark
            },
        )
        Spacer(modifier = Modifier.width(AppDimens.spaceSm))
        // 头像 28dp 圆：URL 空则滚筒圆母题占位（规范 §5 空态母题，禁灰色图标）
        val avatarUrl = entry.avatar
        if (avatarUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(AppColors.colorScheme.primaryLight),
                contentAlignment = Alignment.Center,
            ) {
                DrumMark(size = 16.dp, tint = AppColors.colorScheme.primaryDark)
            }
        } else {
            SmartWashAsyncImage(
                url = avatarUrl,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape),
            )
        }
        Spacer(modifier = Modifier.width(AppDimens.spaceSm))
        Text(
            text = entry.safeMaskedPhone,
            style = MaterialTheme.typography.titleSmall,
            color = AppColors.colorScheme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${entry.safePoints}",
            style = AppTextStyles.AmountMedium,
            color = AppColors.colorScheme.primaryDark,
        )
        Spacer(modifier = Modifier.width(AppDimens.spaceXxs))
        Text(
            text = stringResource(R.string.points_unit),
            style = MaterialTheme.typography.labelMedium,
            // 单位是数值读数的一部分（可读内容非装饰），textTertiary 压白卡 2.25:1 触 §10 红线（audit-20 #3）
            color = AppColors.colorScheme.textSecondary,
        )
    }
}

/** 我的排名行（56dp）：上榜 = 名次 + 月积分；未上榜 = 签到引导 */
@Composable
private fun MyRankRow(my: MyRankVo?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (my != null) {
            Text(
                text = stringResource(R.string.points_my_rank_format, my.safeRank, my.safePoints),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFeatureSettings = "tnum",
                ),
                color = AppColors.colorScheme.textPrimary,
            )
        } else {
            Text(
                text = stringResource(R.string.points_my_rank_none),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.colorScheme.textSecondary,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

/** 积分明细区块：reason 文案 + delta 正负色 + 时间（append-only 流水，时间倒序）；空流水卡内空态 */
@Composable
private fun LedgerSection(
    items: List<PointLedgerItemVo>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.points_ledger_title),
            style = AppTextStyles.SectionTitle,
            color = AppColors.colorScheme.textPrimary,
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceXs))
        GroupCard(contentPadding = PaddingValues(horizontal = AppDimens.cardPadding)) {
            if (items.isEmpty()) {
                // 空流水：滚筒母题空态（audit-20 #7：points_ledger_empty 既有文案接线；
                // icon 仅占位不渲染，EmptyState 只画滚筒圆）
                EmptyState(
                    icon = Icons.Default.Refresh,
                    message = stringResource(R.string.points_ledger_empty),
                )
            } else {
                items.forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.hairline)
                    }
                    LedgerRow(item)
                }
            }
        }
    }
}

/** 明细行（56dp）：左 = reason 文案 + 时间；右 = delta（正绿负红小字变体，tnum） */
@Composable
private fun LedgerRow(item: PointLedgerItemVo) {
    val reason = PointsReason.from(item.reason)
    val reasonLabel = reason.labelRes?.let { stringResource(it) }
        ?: item.reasonDesc?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.points_reason_unknown)
    val delta = item.safeDelta
    val deltaColor = when {
        delta > 0 -> AppColors.colorScheme.textGreen
        delta < 0 -> AppColors.colorScheme.dangerInk
        else -> AppColors.colorScheme.textSecondary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = reasonLabel,
                style = MaterialTheme.typography.titleSmall,
                color = AppColors.colorScheme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 时间解析失败（格式漂移）时隐藏而非展示垃圾串
            formatLedgerTime(item.createdAt)?.let { time ->
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFeatureSettings = "tnum",
                    ),
                    // 时间戳是流水唯一时序信息，textTertiary 双板均 <4.5:1（audit-20 #4，
                    // 照 NoticeInboxPage 审查19 P1-4 先例改 textSecondary）
                    color = AppColors.colorScheme.textSecondary,
                )
            }
        }
        Text(
            text = if (delta > 0) "+$delta" else "$delta",
            style = AppTextStyles.AmountMedium,
            color = deltaColor,
        )
    }
}
