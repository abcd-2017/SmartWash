package com.smartwash.ui.page.workbench

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.smartwash.R
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.LocalCoverArt
import com.smartwash.common.ui.components.SkeletonBlock
import com.smartwash.common.ui.components.softCardShadow
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.theme.DivBgBottomDark
import com.smartwash.common.ui.theme.DivBgMidDark
import com.smartwash.common.ui.theme.DivBgTopDark
import com.smartwash.common.ui.theme.DivColors
import com.smartwash.common.ui.theme.DivTextPrimaryDark
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.pressable
import com.smartwash.feature.divination.DivRoute
import kotlinx.coroutines.delay

/** 工具网格列数（tile 两列卡） */
private const val GRID_COLUMNS = 2

/** 逐项入场错峰间隔（规范 §7：间隔 ≤60ms；40ms 取中段） */
private const val TOOL_STAGGER_MS = 40

/** 错峰索引上限（40ms × 8 = 320ms，留出入场时长仍在 §7 总预算 400ms 内） */
private const val STAGGER_MAX_INDEX = 8

/** 单项入场淡入时长（reduced motion 由 motionSpec 自动降级 180ms 交叉淡变档） */
private const val TOOL_ENTER_MS = 150

/**
 * 页面渲染块：服务端 type + sectionTitle 下发驱动的构图单元。
 * 按 sort 顺序把条目折成块——sectionTitle 变化处插区块标题，连续 row 归并进一张白卡，
 * 连续 tile 两列成行，wide/notice 各占一行。后台改 type/sort/section_title 即换构图。
 */
private sealed interface WorkbenchBlock {
    /** LazyColumn 稳定 key（entryKey 已在 VM 去重，拼接不碰撞） */
    val key: String

    /** 区块标题（服务端 sectionTitle 分组边界） */
    data class Section(val title: String) : WorkbenchBlock {
        override val key: String get() = "wb_section_$title"
    }

    /** 公告条（可关闭运营位） */
    data class Notice(val tool: WorkbenchTool) : WorkbenchBlock {
        override val key: String get() = "wb_notice_${tool.entryKey}"
    }

    /** 通栏横卡（重点入口） */
    data class Wide(val tool: WorkbenchTool) : WorkbenchBlock {
        override val key: String get() = "wb_wide_${tool.entryKey}"
    }

    /** 两列小卡行（1~2 个 tile，奇数末位补空） */
    data class TileRow(val tools: List<WorkbenchTool>) : WorkbenchBlock {
        override val key: String
            get() = "wb_tilerow_${tools.size}_" + tools.joinToString("-") { it.entryKey }
    }

    /** 列表行组（0.5dp 发丝线只出现在卡内，规范 §3.4） */
    data class RowGroup(val tools: List<WorkbenchTool>) : WorkbenchBlock {
        override val key: String
            get() = "wb_rowgroup_" + tools.joinToString("-") { it.entryKey }
    }
}

/**
 * 按 sort 序折块：sectionTitle 与前一 entry 不同 → 插区块标题（并打断连续归并链，
 * 不同分组的 row/tile 各自成组）；随后按 layout 流式归并。
 */
private fun List<WorkbenchTool>.toBlocks(): List<WorkbenchBlock> {
    val blocks = mutableListOf<WorkbenchBlock>()
    var currentSection: String? = null
    forEach { tool ->
        val section = tool.sectionTitle
        if (section != null && section != currentSection) {
            blocks += WorkbenchBlock.Section(section)
            currentSection = section
        }
        when (tool.layout) {
            WorkbenchLayout.NOTICE -> blocks += WorkbenchBlock.Notice(tool)
            WorkbenchLayout.WIDE -> blocks += WorkbenchBlock.Wide(tool)
            WorkbenchLayout.ROW -> {
                val last = blocks.lastOrNull()
                if (last is WorkbenchBlock.RowGroup) {
                    blocks[blocks.lastIndex] = last.copy(tools = last.tools + tool)
                } else {
                    blocks += WorkbenchBlock.RowGroup(listOf(tool))
                }
            }
            WorkbenchLayout.TILE -> {
                val last = blocks.lastOrNull()
                if (last is WorkbenchBlock.TileRow && last.tools.size < GRID_COLUMNS) {
                    blocks[blocks.lastIndex] = last.copy(tools = last.tools + tool)
                } else {
                    blocks += WorkbenchBlock.TileRow(listOf(tool))
                }
            }
        }
    }
    return blocks
}

/**
 * 工作台 — 底栏 tab 根页（设计稿屏 15）。
 * 清氧页面里的一扇玄墨之窗：观象台特色卡 + 类型化工具区。
 * 工具列表来自 [WorkbenchViewModel]：服务端入口配置下发（`/web/auth/workbench/entries`），
 * type/section_title/image_url 驱动「公告条 / 通栏卡 / 两列小卡 / 列表行组」混合构图；
 * 失败/空配置给真实错误态/空态。端上自闭环：搜索过滤、最近使用（DataStore）、下拉刷新。
 * 观象台玄墨特色卡为端上固定品牌位，不参与服务端下发。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkbenchPage(
    navController: NavHostController,
    workbenchViewModel: WorkbenchViewModel = hiltViewModel(),
) {
    val visibleTools by workbenchViewModel.visibleTools.collectAsState()
    val recentTools by workbenchViewModel.recentTools.collectAsState()
    val searchQuery by workbenchViewModel.searchQuery.collectAsState()
    val entriesState by workbenchViewModel.entriesState.collectAsState()
    val context = LocalContext.current

    // 深链解析回流导航：resolve 在 VM 协程内完成，本收集端只承担导航副作用（规范：副作用出组合期）
    LaunchedEffect(Unit) {
        workbenchViewModel.navigateAction.collect { action ->
            workbenchViewModel.navigate(navController, action)
        }
    }

    val isSearching = searchQuery.isNotBlank()
    val showSkeleton = entriesState is RequestState.Loading && visibleTools.isEmpty()
    val blocks = remember(visibleTools) { visibleTools.toBlocks() }
    val hasSections = remember(blocks) { blocks.any { it is WorkbenchBlock.Section } }
    val searchRows = remember(visibleTools) { visibleTools.chunked(GRID_COLUMNS) }

    // 统一点击入口：兜底占位项提示建设中，其余走深链（并记最近使用）
    val handleToolClick: (WorkbenchTool) -> Unit = { tool ->
        if (tool.target.isBlank()) {
            Toast.makeText(
                context,
                context.getString(R.string.workbench_coming_soon),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            workbenchViewModel.onToolClick(tool)
        }
    }

    PullToRefreshBox(
        // 首载骨架已在表达加载态，指示器仅在已有内容时的下拉刷新出现
        isRefreshing = entriesState is RequestState.Loading && !showSkeleton,
        onRefresh = { workbenchViewModel.loadEntries() },
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        LazyColumn {
            item(key = "wb_title") {
                // 标题区（顶部与其他 tab 页统一取状态栏下 16；副标题底→搜索框 20 为原特色卡节奏）
                Column(
                    modifier = Modifier.padding(
                        start = AppDimens.pagePadding,
                        end = AppDimens.pagePadding,
                        top = 16.dp,
                        bottom = 20.dp
                    )
                ) {
                    Text(
                        text = stringResource(R.string.workbench_title),
                        style = AppTextStyles.RootTitle,
                        color = AppColors.colorScheme.textPrimary
                    )
                    // 设计稿实测：大标题底→副标题 4
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.workbench_subtitle),
                        // 画板副标题 12/400（caption 400 档），非 captionStrong 500
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.colorScheme.textSecondary
                    )
                }
            }

            item(key = "wb_search") {
                // 搜索框（§3.7 输入控件：16sp 正文 / 48dp 行高 / 前缀图标 16dp@0.5 / radiusButton 14）
                WorkbenchSearchField(
                    query = searchQuery,
                    onQueryChange = workbenchViewModel::onSearchChange,
                    modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
                )
            }

        if (showSkeleton) {
            // 加载骨架（规范 §3.6：骨架屏替代转圈，形状与真实网格一致；行距同真实网格 cardSpacing）
            item(key = "wb_tools_skeleton") {
                Column(
                    modifier = Modifier.padding(horizontal = AppDimens.pagePadding),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing)
                ) {
                    repeat(2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing)
                        ) {
                            repeat(GRID_COLUMNS) {
                                SkeletonBlock(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(148.dp),
                                    shape = RoundedCornerShape(AppDimens.radiusLg)
                                )
                            }
                        }
                    }
                }
            }
        } else if (entriesState is RequestState.Error) {
                // 真实错误态（2026-11-01 缺陷 6：原失败静默回落本地假列表）——说明 + 重试
                item(key = "wb_tools_error") {
                    EmptyState(
                        icon = Icons.Rounded.Apps,
                        message = (entriesState as RequestState.Error).getMessage(context),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = AppDimens.sectionSpacing),
                        action = {
                            AppButton(
                                text = stringResource(R.string.workbench_retry),
                                onClick = { workbenchViewModel.loadEntries() },
                                variant = AppButtonVariant.SECONDARY
                            )
                        }
                    )
                }
            } else if (visibleTools.isEmpty()) {
                // 真实空态（后端未配置 / 搜索无结果）
                item(key = "wb_tools_empty") {
                    EmptyState(
                        icon = Icons.Rounded.Apps,
                        message = if (isSearching) {
                            stringResource(R.string.workbench_search_empty)
                        } else {
                            stringResource(R.string.workbench_entries_empty)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = AppDimens.sectionSpacing)
                    )
                }
            } else if (isSearching) {
                // 搜索态：平铺两列（快速扫视优先，分组/类型排版让位）
                itemsIndexed(
                    searchRows,
                    key = { _, rowTools ->
                        "wb_search_${rowTools.size}_" + rowTools.joinToString("-") { it.entryKey }
                    }
                ) { rowIndex, rowTools ->
                    StaggerIn(index = rowIndex, modifier = Modifier.fillMaxWidth()) {
                        TileRowContent(
                            tools = rowTools,
                            onToolClick = handleToolClick,
                            // 首行与搜索框按区块节奏拉开（搜索框 bottom 16 + 此处 8 = 24）
                            topSpacing = if (rowIndex == 0) AppDimens.spaceXs else 0.dp
                        )
                    }
                }
            } else {
                // 常态：玄墨品牌位 → 最近使用 → 类型化分区
                if (recentTools.isNotEmpty()) {
                    item(key = "wb_recent") {
                        RecentSection(
                            tools = recentTools,
                            onToolClick = handleToolClick
                        )
                    }
                }

                item(key = "wb_divination") {
                    // 观象台特色卡 — 玄墨渐变窗（端上固定品牌位，不随服务端下发）
                    // 顶距：无「最近使用」时取 12dp（设计稿 P07 搜索框底→观象台卡顶 = 12，
                    // 2026-11-06 复验原 0 导致卡片与搜索框紧贴）；有「最近使用」时仍按区块 32
                    DivinationEntryCard(
                        modifier = Modifier
                            .padding(horizontal = AppDimens.pagePadding)
                            .padding(top = if (recentTools.isEmpty()) AppDimens.spaceSm else AppDimens.sectionSpacing),
                        onClick = { navController.navigate(DivRoute.Home) }
                    )
                }

                // 全部工具总标题仅在没有分组标题时显示（有 section_title 时各分区自带标题）
                if (!hasSections) {
                    item(key = "wb_tools_header") {
                        Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
                        Text(
                            text = stringResource(R.string.workbench_all_tools),
                            // U-116：v0.4 字阶已废字距，删除 letterSpacing = 0.3.sp
                            style = AppTextStyles.SectionTitle,
                            color = AppColors.colorScheme.textPrimary,
                            modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
                        )
                        Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                    }
                }

                itemsIndexed(blocks, key = { _, block -> block.key }) { index, block ->
                    when (block) {
                        is WorkbenchBlock.Section -> SectionHeader(title = block.title)
                        is WorkbenchBlock.Notice -> StaggerIn(index = index, modifier = Modifier.fillMaxWidth()) {
                            NoticeBar(
                                tool = block.tool,
                                onClick = { handleToolClick(block.tool) },
                                onDismiss = { workbenchViewModel.dismissNotice(block.tool) },
                                modifier = Modifier
                                    .padding(horizontal = AppDimens.pagePadding)
                                    .padding(top = AppDimens.sectionSpacing, bottom = AppDimens.cardSpacing)
                            )
                        }
                        is WorkbenchBlock.Wide -> StaggerIn(index = index, modifier = Modifier.fillMaxWidth()) {
                            WideCard(
                                tool = block.tool,
                                onClick = { handleToolClick(block.tool) },
                                modifier = Modifier
                                    .padding(horizontal = AppDimens.pagePadding)
                                    .padding(top = AppDimens.sectionSpacing, bottom = AppDimens.cardSpacing)
                            )
                        }
                        is WorkbenchBlock.TileRow -> StaggerIn(index = index, modifier = Modifier.fillMaxWidth()) {
                            TileRowContent(
                                tools = block.tools,
                                onToolClick = handleToolClick,
                                // 常态间距全部由前置块（区块标题 bottom / 卡片 bottom cardSpacing）承担
                                topSpacing = 0.dp
                            )
                        }
                        is WorkbenchBlock.RowGroup -> StaggerIn(index = index, modifier = Modifier.fillMaxWidth()) {
                            RowGroupCard(
                                tools = block.tools,
                                onToolClick = handleToolClick,
                                modifier = Modifier
                                    .padding(horizontal = AppDimens.pagePadding)
                                    .padding(top = AppDimens.sectionSpacing, bottom = AppDimens.cardSpacing)
                            )
                        }
                    }
                }
            }

            item(key = "wb_footer") {
                // 页脚弱提示
                // U-105：原 textTertiary #A9AEA9 on #F5F6F3 仅 2.08:1（§10 正文红线 ≥4.5）；
                // 改 textSecondary #6B6E73（实测 4.72:1）
                Text(
                    text = stringResource(R.string.workbench_footer),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.colorScheme.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = AppDimens.pagePadding,
                            end = AppDimens.pagePadding,
                            top = 8.dp,
                            bottom = 8.dp
                        )
                )
            }
        }
    }
}

/**
 * 搜索框（规范 §3.7 输入控件）：48dp 行高、16sp 正文、前缀图标 16dp@0.5、
 * surfaceVariant 底 + inputRadius(14) 圆角、尾缀清除按钮 44dp 热区。
 */
@Composable
private fun WorkbenchSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = AppColors.colorScheme
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.textPrimary),
        cursorBrush = SolidColor(scheme.primary),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(scheme.surfaceVariant, RoundedCornerShape(AppDimens.inputRadius))
                    .padding(start = AppDimens.spaceSm, end = AppDimens.spaceXs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 前缀图标 16dp + opacity .5（§3.7）
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = scheme.textSecondary.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.width(AppDimens.spaceSm))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        // 占位符字号与正文一致（§3.7）
                        Text(
                            text = stringResource(R.string.workbench_search_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.textSecondary
                        )
                    }
                    innerTextField()
                }
                if (query.isNotEmpty()) {
                    // 清除按钮：图标化 + 44dp 方形热区（热区对称溢出行内余量，不参与布局占位观感）
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .pressable(onClick = { onQueryChange("") }, debounce = false),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.workbench_search_clear),
                            modifier = Modifier.size(16.dp),
                            tint = scheme.textSecondary
                        )
                    }
                }
            }
        }
    )
}

/** 区块标题（服务端 section_title 分组边界） */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        // U-116：v0.4 字阶已废字距，删除 letterSpacing = 0.3.sp
        style = AppTextStyles.SectionTitle,
        color = AppColors.colorScheme.textPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = AppDimens.pagePadding,
                end = AppDimens.pagePadding,
                top = AppDimens.sectionSpacing,
                bottom = AppDimens.spaceSm
            )
    )
}

/** 最近使用区：区块标题 + 横滑 chip 行（本地记忆，点击走同一条深链链路） */
@Composable
private fun RecentSection(
    tools: List<WorkbenchTool>,
    onToolClick: (WorkbenchTool) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppDimens.sectionSpacing)
    ) {
        Text(
            text = stringResource(R.string.workbench_recent_title),
            // U-116：v0.4 字阶已废字距，删除 letterSpacing = 0.3.sp
            style = AppTextStyles.SectionTitle,
            color = AppColors.colorScheme.textPrimary,
            modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceSm))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSm),
            contentPadding = PaddingValues(horizontal = AppDimens.pagePadding)
        ) {
            items(tools, key = { it.entryKey }) { tool ->
                RecentChip(tool = tool, onClick = { onToolClick(tool) })
            }
        }
    }
}

/** 最近使用 chip：白胶囊 + 32dp 小图标容器（§3.2 小档 32/12/20），整颗 48dp 高热区 */
@Composable
private fun RecentChip(
    tool: WorkbenchTool,
    onClick: () -> Unit,
) {
    val scheme = AppColors.colorScheme
    val (containerColor, iconTint) = workbenchTintColors(tool)
    Surface(
        shape = RoundedCornerShape(AppDimens.radiusFull),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outline),
        modifier = Modifier.pressable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(
                start = AppDimens.spaceXxs,
                end = AppDimens.spaceSm,
                top = AppDimens.spaceXs,
                bottom = AppDimens.spaceXs
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSm)
        ) {
            IconBox(
                icon = tool.icon,
                size = 32.dp,
                iconSize = 20.dp,
                containerColor = containerColor,
                iconTint = iconTint
            )
            Text(
                text = tool.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = scheme.textPrimary
            )
        }
    }
}

/**
 * 公告条（type=notice）：primarySoft 浅底 + 白色小图标容器 + 可选跳转箭头 + 48dp 热区关闭钮。
 * target 为空 = 纯文案公告（整条不可点）；关闭即从列表过滤并落 DataStore。
 */
@Composable
private fun NoticeBar(
    tool: WorkbenchTool,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = AppColors.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (tool.target.isBlank()) Modifier else Modifier.pressable(onClick = onClick))
            .background(scheme.primarySoft, RoundedCornerShape(AppDimens.radiusMd))
            .height(56.dp)
            .padding(start = AppDimens.spaceSm, end = AppDimens.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSm)
    ) {
        // 图标仍在容器里（§3.2）：白色小容器浮在浅绿条上，分层清晰
        IconBox(
            icon = tool.icon,
            size = 32.dp,
            iconSize = 20.dp,
            containerColor = scheme.surface,
            iconTint = scheme.primary
        )
        Text(
            text = tool.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = scheme.onPrimarySoft,
            modifier = Modifier.weight(1f)
        )
        if (tool.target.isNotBlank()) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = scheme.onPrimarySoft.copy(alpha = 0.7f)
            )
        }
        // 关闭钮：48dp 方形热区（列表行高 56 内余量充足）
        Box(
            modifier = Modifier
                .size(48.dp)
                .pressable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.workbench_notice_dismiss),
                modifier = Modifier.size(16.dp),
                tint = scheme.onPrimarySoft.copy(alpha = 0.6f)
            )
        }
    }
}

/** 通栏横卡（type=wide）：可选配图（16:9 顶部通铺）+ 图标容器行 + 右箭头 */
@Composable
private fun WideCard(
    tool: WorkbenchTool,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = AppColors.colorScheme
    val (containerColor, iconTint) = workbenchTintColors(tool)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .softCardShadow(),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outline)
    ) {
        Column {
            WorkbenchCoverImage(
                tool = tool,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )
            Row(
                modifier = Modifier.padding(AppDimens.cardPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceMd)
            ) {
                IconBox(
                    icon = tool.icon,
                    containerColor = containerColor,
                    iconTint = iconTint,
                    iconSize = 24.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tool.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = scheme.textPrimary
                    )
                    if (tool.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
                        Text(
                            text = tool.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.textSecondary
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = scheme.textTertiary
                )
            }
        }
    }
}

/** 两列小卡行（连续 tile 归并产物；奇数个末位补空保持网格） */
@Composable
private fun TileRowContent(
    tools: List<WorkbenchTool>,
    onToolClick: (WorkbenchTool) -> Unit,
    topSpacing: androidx.compose.ui.unit.Dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = AppDimens.pagePadding,
                end = AppDimens.pagePadding,
                top = topSpacing,
                bottom = AppDimens.cardSpacing
            ),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing)
    ) {
        tools.forEach { tool ->
            ToolTile(
                tool = tool,
                onClick = { onToolClick(tool) },
                modifier = Modifier.weight(1f)
            )
        }
        if (tools.size == 1) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/** 列表行组卡（type=row 连续归并产物）：一张白卡 + 0.5dp 发丝线行分隔（规范 §3.4 卡内） */
@Composable
private fun RowGroupCard(
    tools: List<WorkbenchTool>,
    onToolClick: (WorkbenchTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = AppColors.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .softCardShadow(),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outline)
    ) {
        Column {
            tools.forEachIndexed { index, tool ->
                if (index > 0) {
                    // 发丝线只出现在同一容器内部（§1.3/§3.4）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppDimens.cardPadding)
                            .height(0.5.dp)
                            .background(scheme.hairline)
                    )
                }
                WorkbenchRowLine(tool = tool, onClick = { onToolClick(tool) })
            }
        }
    }
}

/** 列表行：36dp 次级图标容器（§2.4 v0.4 36/12/20）+ 14/500 主文案 + 角标 + 箭头 */
@Composable
private fun WorkbenchRowLine(
    tool: WorkbenchTool,
    onClick: () -> Unit,
) {
    val scheme = AppColors.colorScheme
    val (containerColor, iconTint) = workbenchTintColors(tool)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .pressable(onClick = onClick)
            .padding(horizontal = AppDimens.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSm)
    ) {
        IconBox(
            icon = tool.icon,
            size = 36.dp,
            iconSize = 20.dp,
            containerColor = containerColor,
            iconTint = iconTint
        )
        Text(
            text = tool.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = scheme.textPrimary,
            modifier = Modifier.weight(1f)
        )
        tool.badge?.let { badge ->
            // U-104：原 #2D9B6A on #F0FBF4 仅 3.30:1；#1E8C5C on #F0FBF4 为 3.99:1 仍 <4.5，
            // 且 §10 绿色文字仅限 ≥16sp/600（本角标 12sp/500），故改 textPrimary #1C1C1E（16.05:1）
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.textPrimary,
                modifier = Modifier
                    .background(scheme.primaryLight, RoundedCornerShape(AppDimens.radiusFull))
                    .padding(horizontal = AppDimens.spaceSm, vertical = 2.dp)
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = scheme.textTertiary
        )
    }
}

/**
 * 逐项错峰入场：淡入 + 轻位移（规范 §7 列表逐项入场节奏）；
 * reduced motion（规范 §8）：取消错峰延迟与位移，纯 180ms 淡入。
 *
 * shown 用 rememberSaveable（2026-11-05 修复④根因之二）：工作台是 HomePage HorizontalPager
 * 的一页（同一 nav destination），进二级页返回时整页组合销毁重建——原 remember 使 shown 归零、
 * AnimatedVisibility 全部收起，列表项高度塌陷至近零，内容总高小于视口把恢复的滚动位置
 * 钳制回顶部（与 VM 流重置叠加造成「返回必回顶」）。saveable 后返回即恢复 shown=true，
 * 不塌陷、不重放入场动画（首次进入仍按规范错峰入场）。
 */
@Composable
private fun StaggerIn(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    var shown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (reduceMotion) {
            shown = true
        } else {
            delay((index.coerceAtMost(STAGGER_MAX_INDEX) * TOOL_STAGGER_MS).toLong())
            shown = true
        }
    }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(animationSpec = motionSpec(tween(TOOL_ENTER_MS))) +
            slideInVertically(
                animationSpec = motionSpec(defaultSpring()),
                initialOffsetY = { if (reduceMotion) 0 else it / 6 }
            ),
        modifier = modifier
    ) {
        content()
    }
}

/** 配图（Coil）：placeholder = surfaceVariant 色块，交叉淡变 180ms（reduced motion 关闭淡变） */
@Composable
private fun WorkbenchImage(
    url: String,
    modifier: Modifier = Modifier,
) {
    val scheme = AppColors.colorScheme
    val reduceMotion = LocalReduceMotion.current
    val context = LocalContext.current
    val request = remember(url, reduceMotion) {
        ImageRequest.Builder(context)
            .data(url)
            .apply { if (reduceMotion) crossfade(false) else crossfade(CROSSFADE_ALT_MS) }
            .build()
    }
    AsyncImage(
        model = request,
        // 装饰性配图：语义由标题/描述承载
        contentDescription = null,
        modifier = modifier.background(scheme.surfaceVariant),
        placeholder = ColorPainter(scheme.surfaceVariant),
        contentScale = ContentScale.Crop
    )
}

/**
 * 工作台配图统一槽：服务端 imageUrl 优先（Coil），未下发时按 entryKey 用本地自绘封面兜底，
 * 两者皆无则不渲染（调用方据此回退 IconBox 布局）。
 */
@Composable
private fun WorkbenchCoverImage(
    tool: WorkbenchTool,
    modifier: Modifier = Modifier,
): Boolean {
    val local = LocalCoverArt.workbenchCover(tool.entryKey)
    if (tool.imageUrl != null) {
        WorkbenchImage(url = tool.imageUrl!!, modifier = modifier)
        return true
    }
    if (local != null) {
        Image(
            painter = painterResource(local),
            contentDescription = null,
            modifier = modifier.background(AppColors.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        return true
    }
    return false
}

/** 六色浅底语义色组（icon/color 白名单映射产物，卡片组件共用） */
@Composable
private fun workbenchTintColors(tool: WorkbenchTool): Pair<Color, Color> {
    val scheme = AppColors.colorScheme
    return when (tool.tint) {
        WorkbenchTint.GREEN -> scheme.iconContainerGreen to scheme.iconForegroundGreen
        WorkbenchTint.BLUE -> scheme.iconContainerBlue to scheme.iconForegroundBlue
        WorkbenchTint.ORANGE -> scheme.iconContainerOrange to scheme.iconForegroundOrange
        WorkbenchTint.PINK -> scheme.iconContainerPink to scheme.iconForegroundPink
        WorkbenchTint.TEAL -> scheme.iconContainerTeal to scheme.iconForegroundTeal
        WorkbenchTint.PURPLE -> scheme.iconContainerPurple to scheme.iconForegroundPurple
    }
}

/**
 * 观象台玄墨特色卡（设计稿 darkcard3）：清氧页面里开一扇通往玄墨世界的窗。
 * **端上固定品牌位**：不参与服务端工作台入口下发，样式与跳转（DivRoute.Home）端上写死。
 * 深色自带分层不加投影；皮肤取 DivColors 玄墨暗色令牌，浅深模式同款（设计稿两板对此卡同款）。
 * 渐变对角 135° 三段 #201D18→#1A1917@55%→#151412 = 规范 §1.5 divination.ink（画板实测）。
 */
@Composable
private fun DivinationEntryCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val div = DivColors.current
    // 画板实测圆角 16（= radiusLg 卡片唯一档，非 hero 20）
    val shape = RoundedCornerShape(AppDimens.radiusLg)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .background(
                // 画板渐变三段色标 0 / 0.55 / 1（#201D18→#1A1917@55%→#151412）
                brush = Brush.linearGradient(
                    0f to DivBgTopDark,
                    0.55f to DivBgMidDark,
                    1f to DivBgBottomDark,
                ),
                shape = shape
            )
            // [P07] 2026-11-05 画板复裁：卡描边 1px 实线为主题 outline（浅 #E3E7E2 / 深 #2C312D，
            // 不透明、随主题换色）；原固定鎏金 32% 与当前画板不符
            .border(1.dp, AppColors.colorScheme.outline, shape)
            .padding(AppDimens.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceMd)
    ) {
        DivinationCompass(modifier = Modifier.size(64.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.workbench_divination_name),
                // U-116：v0.4 字阶已废字距，删除 letterSpacing = 0.3.sp
                style = AppTextStyles.SectionTitle.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = DivTextPrimaryDark
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceXs))
            // 设计稿两板副标题同为宣纸主文本 #EFE9DA（弱层级靠字号 12，字重 400）
            Text(
                text = stringResource(R.string.workbench_divination_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = DivTextPrimaryDark
            )
        }
        // 32dp 圆形描边箭头：浅 #8F7233 / 深 #C9A961 均 32% = div.gold 主题感知（画板实测），
        // 内 chevron_right 20dp 鎏金
        Box(
            modifier = Modifier
                .size(32.dp)
                .border(1.dp, div.gold.copy(alpha = 0.32f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = div.gold,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 罗盘圆 — 玄墨金线几何母题（规范 §5：滚筒圆同一几何的观象台变体），
 * 画布 64dp 基准按设计稿 66px 罗盘 SVG 等比换算（外环 26 / 虚线中环 18 / 内环 9）。
 */
@Composable
private fun DivinationCompass(modifier: Modifier = Modifier) {
    val gold = DivColors.current.gold
    val goldHi = DivColors.current.goldHi
    Canvas(modifier = modifier) {
        val stroke = 2.dp.toPx()
        val hairStroke = 1.5.dp.toPx()
        // 外环
        drawCircle(
            color = gold.copy(alpha = 0.45f),
            radius = 26.dp.toPx(),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        // 虚线中环（设计 dasharray 3 5）
        drawCircle(
            color = gold.copy(alpha = 0.26f),
            radius = 18.dp.toPx(),
            style = Stroke(
                width = hairStroke,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(3.dp.toPx(), 5.dp.toPx())
                )
            )
        )
        // 内环
        drawCircle(
            color = gold.copy(alpha = 0.5f),
            radius = 9.dp.toPx(),
            style = Stroke(width = hairStroke, cap = StrokeCap.Round)
        )
        // 四向刻度（12/6/3/9 点位，自外缘向内 5）
        listOf(
            0f to -1f, 0f to 1f, -1f to 0f, 1f to 0f
        ).forEach { (dx, dy) ->
            drawLine(
                color = gold,
                start = center + Offset(dx * 24.dp.toPx(), dy * 24.dp.toPx()),
                end = center + Offset(dx * 19.dp.toPx(), dy * 19.dp.toPx()),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
        // 水波线（穿过环心的观象台波纹）
        val wave = Path().apply {
            moveTo(center.x - 7.dp.toPx(), center.y)
            quadraticBezierTo(
                center.x - 4.dp.toPx(), center.y - 3.dp.toPx(),
                center.x - 1.dp.toPx(), center.y
            )
            quadraticBezierTo(
                center.x + 2.dp.toPx(), center.y + 3.dp.toPx(),
                center.x + 5.dp.toPx(), center.y
            )
        }
        drawPath(
            path = wave,
            color = goldHi,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        // 中心点
        drawCircle(color = gold, radius = 2.5.dp.toPx(), center = center)
    }
}

/**
 * 工具卡（type=tile，设计稿 tile3）：白卡 1px 描边 + level1 阴影 + 44dp 语义图标容器（icon.md 24）
 * + 14sp 标题 + 12sp 描述；可选配图（顶部 96dp 通铺，图卡不再叠图标容器）；右上角 badge 角标
 * （清氧小胶囊：radiusFull、主色浅底/主色文字）。icon/color 由 WorkbenchTint 白名单映射（未知值兜底），
 * 触控整卡 ≥48dp、按压反馈走 pressable（scale 0.97 + indication = null，禁止 Material ripple）。
 * 描述为空不下发占位行（卡内节奏不因空文案错位）。
 */
@Composable
private fun ToolTile(
    tool: WorkbenchTool,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = AppColors.colorScheme
    val hasCover = tool.imageUrl != null || LocalCoverArt.workbenchCover(tool.entryKey) != null
    val (containerColor, iconTint) = workbenchTintColors(tool)
    Surface(
        modifier = modifier.pressable(onClick = onClick).softCardShadow(),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = scheme.surface,
        // 规范 §2.4：卡片 = 1px 描边 + 0 2px 12px rgba(20,40,30,.05) → level1
        border = BorderStroke(1.dp, scheme.outline)
    ) {
        Box {
            Column {
                WorkbenchCoverImage(
                    tool = tool,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                )
                Column(modifier = Modifier.padding(AppDimens.cardPadding)) {
                    if (!hasCover) {
                        IconBox(
                            icon = tool.icon,
                            containerColor = containerColor,
                            iconTint = iconTint,
                            iconSize = 24.dp
                        )
                        Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                    }
                    Text(
                        text = tool.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.textPrimary
                    )
                    if (tool.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
                        Text(
                            text = tool.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.textSecondary
                        )
                    }
                }
            }
            // badge 角标（服务端下发文案）：清氧小胶囊 — radiusFull + 主色浅底（caption 档）
            // U-104：文字改 textPrimary #1C1C1E（原 #2D9B6A on #F0FBF4 3.30:1；绿字红线要求
            // ≥16sp/600，本角标 12sp/500 不适用 green.700）
            if (!tool.badge.isNullOrBlank()) {
                Text(
                    text = tool.badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.textPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = AppDimens.spaceSm, end = AppDimens.spaceSm)
                        .background(scheme.primaryLight, RoundedCornerShape(AppDimens.radiusFull))
                        .padding(horizontal = AppDimens.spaceSm, vertical = 2.dp)
                )
            }
        }
    }
}
