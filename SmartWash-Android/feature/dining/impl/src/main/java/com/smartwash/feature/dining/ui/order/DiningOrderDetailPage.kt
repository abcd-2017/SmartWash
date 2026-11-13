package com.smartwash.feature.dining.ui.order

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.AppConfirmDialog
import com.smartwash.common.ui.components.AppInputDialog
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SmartWashAsyncImage
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.pressable
import com.smartwash.feature.dining.R
import com.smartwash.feature.dining.api.DiningRoute
import com.smartwash.feature.dining.logic.DiningCartState
import com.smartwash.feature.dining.logic.DiningMenuLink
import com.smartwash.feature.dining.logic.DiningMenuSection
import com.smartwash.feature.dining.logic.DiningMenuItem
import com.smartwash.feature.dining.logic.DiningOrderPageDetail
import com.smartwash.feature.dining.logic.DiningParticipant
import kotlinx.coroutines.launch

/** 左栏宽度（分类名短词为主，84dp 兼顾两行折行） */
private val RAIL_WIDTH = 84.dp

/** 菜品封面缩略图尺寸 */
private val DISH_COVER_SIZE = 56.dp

/**
 * 点单页（09 号票落地，10 号票接入共享购物车同步，11 号票接入结束点单）：左分类栏 +
 * 右菜品列表（封面/名称/已选标识）+ 底部已选栏 + 参与者区 + 发起者「结束点单」入口
 * （确认弹窗 → finish 接口 → 购买清单结果页）。FINISHED 后进入只读态：隐藏勾选/备注/
 * 结束/分享入口，底部栏提供「查看购买清单」。
 *
 * 左右联动：右侧 [LazyColumn] 单列表（分类头 + 菜品行），滚动位置经 [DiningMenuLink] 纯函数
 * 反算高亮分类；点分类经 [DiningMenuLink.scrollTargetIndex] 计算落点后 `scrollToItem`。
 * 联动派生逻辑全部在 [DiningMenuLink] / DiningMenuNavigator（纯 Kotlin，JVM 单测锚点）。
 *
 * 同步（10 号票）：首载全量拉取后经 [com.smartwash.feature.dining.sync.DiningOrderSyncChannel]
 * 订阅订单变更（首实现为可见时约 3s 轮询，页面不可见停止）；本地勾选/备注即时生效，
 * 写操作携带已知 version，冲突拉取最新后重放本地意图，失败回滚并提示。
 */
@Composable
fun DiningOrderDetailPage(
    navController: NavHostController,
    viewModel: DiningOrderDetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val loadState by viewModel.loadState.collectAsState()
    val detail by viewModel.detail.collectAsState()
    val cart by viewModel.cart.collectAsState()
    val pendingRecipeIds by viewModel.pendingRecipeIds.collectAsState()
    val syncPending by viewModel.syncPending.collectAsState()
    val message by viewModel.message.collectAsState()
    val finishState by viewModel.finishState.collectAsState()

    // 首载（返回本页经 LaunchedEffect(Unit) 不重放；失败重试显式触发）
    LaunchedEffect(Unit) { viewModel.load() }

    // 结束点单结果（成功进购买清单结果页并弹掉本页；失败 Toast 提示后可重试）
    LaunchedEffect(finishState) {
        when (val state = finishState) {
            is RequestState.Success -> {
                viewModel.consumeFinishState()
                navController.navigate(DiningRoute.ShoppingList(orderId = viewModel.orderId)) {
                    popUpTo<DiningRoute.OrderDetail> { inclusive = true }
                }
            }

            is RequestState.Error -> {
                Toast.makeText(context, state.getMessage(context), Toast.LENGTH_SHORT).show()
                viewModel.consumeFinishState()
            }

            else -> Unit
        }
    }

    // 订阅生命周期（10 号票）：页面可见启动轮询、不可见停止（观察者随组合移除，防泄露）。
    // 注册前已 STARTED 时补一次可见（ON_START 已过，观察者收不到）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onPageVisible()
                Lifecycle.Event.ON_STOP -> viewModel.onPageInvisible()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            viewModel.onPageVisible()
        }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 一次性提示（Toast 收进 effect——组合期禁副作用）
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    // 备注弹层目标（null=收起）；弹层取舍见 NoteDialog 注释
    var noteTarget by remember { mutableStateOf<DiningMenuItem?>(null) }

    // 结束点单确认弹窗（发起者主动作二次确认，防误触锁定订单）
    var showFinishConfirm by remember { mutableStateOf(false) }

    val pageDetail = detail
    val sections = pageDetail?.sections.orEmpty()
    val link = remember(sections) { DiningMenuLink(sections) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 滚动 → 高亮分类（派生逻辑在纯函数，组合期只喂首可见 index）
    val activeCategoryId by remember(sections) {
        derivedStateOf { link.activeCategoryAt(listState.firstVisibleItemIndex) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background),
    ) {
        PageHeader(
            title = pageDetail?.title?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.dining_order_detail_title),
            onBack = { navController.navigateUp() },
            actions = {
                // 已结束订单不再分享（join_token 已失效，避免生成无效二维码）
                if (pageDetail?.isCreator == true && pageDetail.isOngoing) {
                    AppButton(
                        text = stringResource(R.string.dining_order_detail_share),
                        onClick = {
                            pageDetail.let { d ->
                                navController.navigate(
                                    DiningRoute.OrderShare(
                                        orderId = d.id,
                                        title = d.title,
                                        joinToken = d.joinToken.ifBlank { null },
                                    ),
                                )
                            }
                        },
                        variant = AppButtonVariant.TEXT,
                    )
                }
            },
        )

        when {
            // 详情在（成功/刷新/失败留痕）——渲染内容；失败重试显式触发
            pageDetail != null -> OrderDetailContent(
                detail = pageDetail,
                sections = sections,
                listState = listState,
                activeCategoryId = activeCategoryId,
                cart = cart,
                pendingRecipeIds = pendingRecipeIds,
                syncPending = syncPending,
                readOnly = pageDetail.isOngoing.not(),
                onToggle = viewModel::toggleRecipe,
                onNoteClick = { noteTarget = it },
                onCategoryClick = { categoryId ->
                    scope.launch { listState.scrollToItem(link.scrollTargetIndex(categoryId)) }
                },
                onFinish = { showFinishConfirm = true },
                onViewShoppingList = {
                    navController.navigate(DiningRoute.ShoppingList(orderId = pageDetail.id))
                },
            )

            loadState is RequestState.Error -> EmptyState(
                // EmptyState 只渲染滚筒圆母题（§3.2 禁裸图标着色）：icon 不渲染，仅占位
                icon = Icons.Default.Restaurant,
                message = (loadState as? RequestState.Error)?.getMessage(context)
                    ?: stringResource(R.string.dining_order_detail_load_failed),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = AppDimens.pagePadding),
                action = {
                    AppButton(
                        small = true,
                        text = stringResource(R.string.dining_recipe_action_retry),
                        onClick = { viewModel.load() },
                        variant = AppButtonVariant.SECONDARY,
                    )
                },
            )

            // 首载/重试中（pageDetail 尚为空）
            else -> LoadingState(modifier = Modifier.fillMaxSize())
        }
    }

    noteTarget?.let { target ->
        NoteDialog(
            dish = target,
            initialNote = cart.noteOf(target.recipeId),
            onConfirm = { note ->
                viewModel.updateNote(target.recipeId, note)
                noteTarget = null
            },
            onDismiss = { noteTarget = null },
        )
    }

    // 结束点单确认（结束后锁定并生成购买清单，误触不可逆）
    if (showFinishConfirm) {
        AppConfirmDialog(
            title = stringResource(R.string.dining_order_detail_finish_confirm_title),
            message = stringResource(R.string.dining_order_detail_finish_confirm_message),
            confirmText = stringResource(R.string.dining_order_detail_finish),
            onConfirm = {
                showFinishConfirm = false
                viewModel.finishOrder()
            },
            onDismiss = { showFinishConfirm = false },
        )
    }
}

/** 点单页内容：参与者区 + 左右联动主区 + 底部已选栏 */
@Composable
private fun OrderDetailContent(
    detail: DiningOrderPageDetail?,
    sections: List<DiningMenuSection>,
    listState: LazyListState,
    activeCategoryId: Long?,
    cart: DiningCartState,
    pendingRecipeIds: Set<Long>,
    syncPending: Boolean,
    readOnly: Boolean,
    onToggle: (Long) -> Unit,
    onNoteClick: (DiningMenuItem) -> Unit,
    onCategoryClick: (Long?) -> Unit,
    onFinish: () -> Unit,
    onViewShoppingList: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ParticipantsBar(detail?.members.orEmpty())

        Row(modifier = Modifier.weight(1f)) {
            CategoryRail(
                sections = sections,
                activeCategoryId = activeCategoryId,
                onSelect = onCategoryClick,
                modifier = Modifier.width(RAIL_WIDTH),
            )
            DishList(
                sections = sections,
                listState = listState,
                cart = cart,
                pendingRecipeIds = pendingRecipeIds,
                readOnly = readOnly,
                onToggle = onToggle,
                onNoteTarget = onNoteClick,
                modifier = Modifier.weight(1f),
            )
        }

        SelectedBottomBar(
            detail = detail,
            cart = cart,
            syncPending = syncPending,
            readOnly = readOnly,
            onFinish = onFinish,
            onViewShoppingList = onViewShoppingList,
        )
    }
}

/** 参与者区：横向滚动的成员 chip 行（含发起/我标记），安静灰阶不抢锚点 */
@Composable
private fun ParticipantsBar(members: List<DiningParticipant>) {
    GroupCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.pagePadding, vertical = AppDimens.spaceXs),
        contentPadding = PaddingValues(horizontal = AppDimens.spaceMd, vertical = AppDimens.spaceSm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.dining_order_detail_participants_format, members.size),
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.colorScheme.textTertiary,
            )
            Spacer(modifier = Modifier.width(AppDimens.spaceSm))
            members.forEach { member ->
                ParticipantChip(member)
                Spacer(modifier = Modifier.width(AppDimens.spaceXs))
            }
        }
    }
}

/** 参与者 chip：标识 + 发起/我角标（我=primarySoft 底，发起尾标） */
@Composable
private fun ParticipantChip(member: DiningParticipant) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(AppDimens.radiusFull))
            .background(
                if (member.isMe) AppColors.colorScheme.primarySoft
                else AppColors.colorScheme.surfaceVariant,
            )
            .padding(horizontal = AppDimens.spaceXs, vertical = 4.dp),
    ) {
        Text(
            text = member.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (member.isMe) AppColors.colorScheme.primaryDark
            else AppColors.colorScheme.textSecondary,
        )
        if (member.isCreator) {
            Spacer(modifier = Modifier.width(AppDimens.spaceXxs))
            Text(
                text = stringResource(R.string.dining_order_creator_badge),
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.colorScheme.primaryDark,
            )
        }
    }
}

/** 左分类栏：纵向分类名列表，选中 primarySoft 底 + primary 字；点击滚右侧 */
@Composable
private fun CategoryRail(
    sections: List<DiningMenuSection>,
    activeCategoryId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.surfaceVariant),
    ) {
        items(sections, key = { it.categoryId ?: 0L }) { section ->
            val selected = section.categoryId == activeCategoryId
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (selected) AppColors.colorScheme.primarySoft
                        else Color.Transparent,
                    )
                    .pressable(onClick = { onSelect(section.categoryId) }, debounce = false)
                    .padding(vertical = AppDimens.spaceSm, horizontal = AppDimens.spaceXs),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = section.categoryName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) AppColors.colorScheme.primaryDark
                    else AppColors.colorScheme.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 右菜品列表：单 LazyColumn（分类头 + 菜品行），滚动位置驱动左栏高亮 */
@Composable
private fun DishList(
    sections: List<DiningMenuSection>,
    listState: LazyListState,
    cart: DiningCartState,
    pendingRecipeIds: Set<Long>,
    readOnly: Boolean,
    onToggle: (Long) -> Unit,
    onNoteTarget: (DiningMenuItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sections.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Restaurant,
            message = stringResource(R.string.dining_order_detail_empty_menu),
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = AppDimens.pagePadding),
        )
        return
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AppDimens.spaceLg),
    ) {
        sections.forEach { section ->
            item(key = "section_${section.categoryId ?: 0L}") {
                Text(
                    text = section.categoryName,
                    style = MaterialTheme.typography.titleSmall,
                    color = AppColors.colorScheme.textSecondary,
                    modifier = Modifier.padding(
                        top = AppDimens.spaceSm,
                        bottom = AppDimens.spaceXs,
                        start = AppDimens.spaceSm,
                    ),
                )
            }
            items(section.dishes, key = { it.recipeId }) { dish ->
                DishRow(
                    dish = dish,
                    selected = cart.isSelected(dish.recipeId),
                    note = cart.noteOf(dish.recipeId),
                    pending = !readOnly && dish.recipeId in pendingRecipeIds,
                    readOnly = readOnly,
                    onToggle = { onToggle(dish.recipeId) },
                    onNote = { onNoteTarget(dish) },
                )
            }
        }
    }
}

/**
 * 菜品行：自绘勾选框 + 封面 + 名称 + 选菜人 + 备注入口（整行可点切换）。
 * [pending]=该菜有本地未决写操作（版本冲突重放/写失败回滚窗口），以轻微橙色点区分；
 * 选菜人标识中携带他人备注文本（本人备注单独一行展示，避免重复）。
 */
@Composable
private fun DishRow(
    dish: DiningMenuItem,
    selected: Boolean,
    note: String,
    pending: Boolean,
    readOnly: Boolean,
    onToggle: () -> Unit,
    onNote: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // 只读态（已结束）整行不可点：勾选框仅作展示，不触发写操作
            .then(if (readOnly) Modifier else Modifier.pressable(onClick = onToggle, debounce = false))
            .padding(vertical = AppDimens.spaceSm, horizontal = AppDimens.spaceSm),
    ) {
        DishCheckbox(selected = selected)
        Spacer(modifier = Modifier.width(AppDimens.spaceSm))
        Box(
            modifier = Modifier
                .size(DISH_COVER_SIZE)
                .clip(RoundedCornerShape(AppDimens.radiusMd))
                .background(AppColors.colorScheme.surfaceVariant),
        ) {
            if (dish.coverUrl.isNotBlank()) {
                SmartWashAsyncImage(
                    url = dish.coverUrl,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(modifier = Modifier.width(AppDimens.spaceSm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dish.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) AppColors.colorScheme.primaryDark
                else AppColors.colorScheme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 他人备注随选菜人标识展示（本人备注走下方独立行，避免重复）
            val selectorLabels = dish.selectors.map { selector ->
                if (!selector.isMe && selector.note.isNotBlank()) {
                    "${selector.label}：${selector.note}"
                } else {
                    selector.label
                }
            }
            if (selectorLabels.isNotEmpty()) {
                Text(
                    text = selectorLabels.joinToString("、"),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.colorScheme.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (note.isNotBlank()) {
                Text(
                    text = stringResource(R.string.dining_order_detail_my_note_format, note),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.colorScheme.ongoingInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(AppDimens.spaceXs))
        // 未决操作轻微区分：静态小点（非动效，无 reduced-motion 分支需求）
        if (pending) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        color = AppColors.colorScheme.ongoing,
                        shape = RoundedCornerShape(AppDimens.radiusFull),
                    ),
            )
            Spacer(modifier = Modifier.width(AppDimens.spaceXs))
        }
        // 备注入口（独立热区，点击不触发整行勾选）；只读态隐藏
        if (!readOnly) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimens.radiusFull))
                    .background(AppColors.colorScheme.surfaceVariant)
                    .pressable(onClick = onNote, debounce = false)
                    .padding(horizontal = AppDimens.spaceSm, vertical = AppDimens.spaceXxs),
            ) {
                Text(
                    text = stringResource(R.string.dining_order_detail_note_action),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (note.isNotBlank()) AppColors.colorScheme.primaryDark
                    else AppColors.colorScheme.textSecondary,
                )
            }
        }
    }
}

/** 自绘勾选框（规范 §7 禁 M3 Checkbox/ripple）：未选描边空心，选中 primary 实底 + 白对勾 */
@Composable
private fun DishCheckbox(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .then(
                if (selected) {
                    Modifier.background(
                        color = AppColors.colorScheme.primary,
                        shape = RoundedCornerShape(AppDimens.radiusSmall),
                    )
                } else {
                    Modifier
                        .background(
                            color = AppColors.colorScheme.surface,
                            shape = RoundedCornerShape(AppDimens.radiusSmall),
                        )
                        .border(
                            width = 1.dp,
                            color = AppColors.colorScheme.outline,
                            shape = RoundedCornerShape(AppDimens.radiusSmall),
                        )
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** 底部已选栏：我的已选摘要 + 发起者「结束点单」入口（占位） */
@Composable
private fun SelectedBottomBar(
    detail: DiningOrderPageDetail?,
    cart: DiningCartState,
    syncPending: Boolean,
    readOnly: Boolean,
    onFinish: () -> Unit,
    onViewShoppingList: () -> Unit,
) {
    val selectedNames = detail?.sections
        ?.flatMap { it.dishes }
        ?.filter { cart.isSelected(it.recipeId) }
        ?.map { it.name }
        .orEmpty()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.colorScheme.surface)
            .padding(horizontal = AppDimens.pagePadding, vertical = AppDimens.spaceSm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.dining_order_detail_selected_count_format, cart.count),
                    style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                    color = AppColors.colorScheme.textPrimary,
                )
                if (syncPending) {
                    Spacer(modifier = Modifier.width(AppDimens.spaceXs))
                    Text(
                        text = stringResource(R.string.dining_order_detail_syncing),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.colorScheme.ongoingInk,
                    )
                }
            }
            if (selectedNames.isEmpty()) {
                Text(
                    text = stringResource(R.string.dining_order_detail_bottom_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.colorScheme.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = selectedNames.joinToString("、"),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.colorScheme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(AppDimens.spaceSm))
        if (readOnly) {
            // 只读态：有清单则给「查看购买清单」入口（发起者与参与者均可看）
            if (!detail?.shoppingList.isNullOrBlank()) {
                AppButton(
                    text = stringResource(R.string.dining_order_detail_view_shopping_list),
                    onClick = onViewShoppingList,
                    small = true,
                    variant = AppButtonVariant.SECONDARY,
                )
            }
        } else if (detail?.isCreator == true) {
            AppButton(
                text = stringResource(R.string.dining_order_detail_finish),
                onClick = onFinish,
                small = true,
            )
        }
    }
    Spacer(
        modifier = Modifier.height(
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        ),
    )
}

/** 备注弹层（共享 AppInputDialog；初始值取本地备注，确认回写 ViewModel） */
@Composable
private fun NoteDialog(
    dish: DiningMenuItem,
    initialNote: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember(dish.recipeId) { mutableStateOf(initialNote) }
    AppInputDialog(
        title = stringResource(R.string.dining_order_detail_note_title),
        inputLabel = dish.name,
        inputValue = input,
        onValueChange = { input = it },
        onConfirm = { onConfirm(input) },
        onDismiss = onDismiss,
    )
}
