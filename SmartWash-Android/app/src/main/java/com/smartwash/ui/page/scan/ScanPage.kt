package com.smartwash.ui.page.scan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.smartwash.R
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.pressable
import java.util.concurrent.Executors

/** 扫码框边长（组件尺寸，非间距/圆角令牌；取常见扫码取景框观感） */
private val SCAN_FRAME_SIZE = 260.dp

/** 扫码框圆角（取规范内嵌套块档） */
private val SCAN_FRAME_RADIUS = AppDimens.radiusMd

/** 遮罩半透明黑（相机取景框外压暗，突出取景框；非配色令牌，仅取景叠加层） */
private val SCAN_SCRIM = Color.Black.copy(alpha = 0.55f)

/**
 * 全局扫一扫页（壳层留守，工作台常驻入口）。
 *
 * 职责边界：只做「识别 + 分发」——CameraX 预览 + ML Kit 离线识别原始内容，
 * [ScanViewModel] 归一化后按内容分发：本应用深链（含点单加入，经 dining 域拦截）
 * → 本地路由；http(s) → 内置浏览器；纯文本 → 复制进剪贴板。业务承接在各域
 * 深链贡献者与壳层页面，本页不感知任何业务。
 */
@Composable
fun ScanPage(
    navController: NavHostController,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val clipboardManager = LocalClipboardManager.current

    var hasPermission by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var requestedOnce by remember { mutableStateOf(false) }
    var shouldShowRationale by remember {
        mutableStateOf(activity != null && activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        requestedOnce = true
        hasPermission = granted
        if (!granted && activity != null) {
            shouldShowRationale = activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        }
    }

    // 首次进入自动发起系统申请；拒绝后进入引导态（可再次申请或去系统设置）
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // 从系统设置返回时刷新授权态（onResume 复查，避免设置页授权后仍停在引导态）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = context.checkSelfPermission(Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 分发事件（副作用出组合期）：深链导航 / 纯文本复制 / 无效提示
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ScanDispatchEvent.Navigate -> {
                    viewModel.navigate(navController, event.action)
                    // 扫码页已完成使命：目标页返回不再回到相机页
                    navController.popBackStack()
                }

                is ScanDispatchEvent.CopyAndExit -> {
                    clipboardManager.setText(AnnotatedString(event.text))
                    Toast.makeText(context, R.string.scan_copy_done, Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }

                is ScanDispatchEvent.Invalid -> {
                    Toast.makeText(context, event.messageRes, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasPermission) {
            CameraScanContent(
                onBarcode = viewModel::onCodeScanned,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            PermissionContent(
                showSettings = requestedOnce && !shouldShowRationale,
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = { openAppDetails(context) },
            )
        }

        // 顶部返回栏（相机黑底上自绘白字白箭头，不复用 PageHeader 的浅色主题色）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 12.dp, start = 12.dp)
                .align(Alignment.TopStart)
                .size(48.dp)
                .pressable(onClick = { navController.navigateUp() }),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Default.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = stringResource(R.string.scan_title),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 24.dp),
        )
    }
}

/**
 * 相机取景 + 识别：CameraX Preview + ImageAnalysis（KEEP_ONLY_LATEST），
 * ML Kit 离线 barcode 客户端逐帧识别，取首个条码 rawValue 回调。
 * 分析器线程独立单线程；组合销毁时解绑/关闭，避免泄漏。
 */
@Composable
private fun CameraScanContent(
    onBarcode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnBarcode by rememberUpdatedState(onBarcode)
    val previewView = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    val scanner = remember { BarcodeScanning.getClient() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            scanner.close()
            runCatching { cameraProviderFuture.get().unbindAll() }
        }
    }

    LaunchedEffect(previewView) {
        val provider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
            analyzeBarcode(imageProxy, scanner, currentOnBarcode)
        }
        provider.unbindAll()
        runCatching {
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )
        ScanOverlay(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = AppDimens.spaceXxl),
            // colorScheme 为 @Composable getter，须在组合期取（DrawScope 非组合上下文）
            primaryColor = AppColors.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.scan_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = AppDimens.pagePadding, vertical = AppDimens.spaceXl),
        )
    }
}

/** 单帧识别：mediaImage 为空直接关帧；成功取首个条码原始内容；无论成败必须关帧 */
private fun analyzeBarcode(
    imageProxy: ImageProxy,
    scanner: BarcodeScanner,
    onBarcode: (String) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }
    val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(input)
        .addOnSuccessListener { barcodes: List<Barcode> ->
            barcodes.firstOrNull()?.rawValue?.let(onBarcode)
        }
        .addOnCompleteListener { imageProxy.close() }
}

/**
 * 取景叠加层：框外四边压暗 + 取景框描边 + 四角短描线（品牌色）。
 * 全部绘制在 Canvas，无位图/外部图标依赖。
 */
@Composable
private fun ScanOverlay(
    modifier: Modifier = Modifier,
    primaryColor: Color,
) {
    Canvas(modifier = modifier) {
        val side = SCAN_FRAME_SIZE.toPx()
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        val right = left + side
        val bottom = top + side

        // 四边遮罩
        drawRect(SCAN_SCRIM, size = Size(size.width, top))
        drawRect(SCAN_SCRIM, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
        drawRect(SCAN_SCRIM, topLeft = Offset(0f, top), size = Size(left, side))
        drawRect(SCAN_SCRIM, topLeft = Offset(right, top), size = Size(size.width - right, side))

        // 取景框圆角描边
        drawRoundRect(
            color = Color.White.copy(alpha = 0.9f),
            topLeft = Offset(left, top),
            size = Size(side, side),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(SCAN_FRAME_RADIUS.toPx()),
            style = Stroke(width = 1.dp.toPx()),
        )

        // 四角短描线（品牌主色，2.5dp 线宽圆帽；规范 §3.3 品牌强调）
        val corner = 24.dp.toPx()
        val stroke = 2.5.dp.toPx()
        val cap = StrokeCap.Round
        // 左上
        drawLine(primaryColor, Offset(left, top + corner), Offset(left, top), stroke, cap)
        drawLine(primaryColor, Offset(left, top), Offset(left + corner, top), stroke, cap)
        // 右上
        drawLine(primaryColor, Offset(right - corner, top), Offset(right, top), stroke, cap)
        drawLine(primaryColor, Offset(right, top), Offset(right, top + corner), stroke, cap)
        // 左下
        drawLine(primaryColor, Offset(left, bottom - corner), Offset(left, bottom), stroke, cap)
        drawLine(primaryColor, Offset(left, bottom), Offset(left + corner, bottom), stroke, cap)
        // 右下
        drawLine(primaryColor, Offset(right - corner, bottom), Offset(right, bottom), stroke, cap)
        drawLine(primaryColor, Offset(right, bottom - corner), Offset(right, bottom), stroke, cap)
    }
}

/**
 * 权限引导态：未授权时替代相机预览。首次/可再申请 → 显示申请按钮；
 * 拒绝且不再询问 → 引导去系统设置（对齐权限管理页语义）。
 */
@Composable
private fun PermissionContent(
    showSettings: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = AppDimens.pagePadding, vertical = AppDimens.spaceXxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 规范 §3.2：功能图标必须进容器（44dp 容器 / 12dp 圆角 / 24dp 图标）
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(AppDimens.radiusMd))
                .background(AppColors.colorScheme.iconContainerGreen),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = null,
                tint = AppColors.colorScheme.iconForegroundGreen,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = stringResource(R.string.scan_permission_title),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier.padding(top = AppDimens.spaceMd),
        )
        Text(
            text = stringResource(
                if (showSettings) R.string.scan_permission_denied else R.string.scan_permission_desc
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = AppDimens.spaceXs, start = AppDimens.spaceMd, end = AppDimens.spaceMd),
        )
        AppButton(
            text = stringResource(
                if (showSettings) R.string.scan_permission_settings else R.string.scan_permission_grant
            ),
            onClick = if (showSettings) onOpenSettings else onRequest,
            variant = AppButtonVariant.PRIMARY,
            modifier = Modifier.padding(top = AppDimens.spaceLg),
        )
    }
}

/** 从 Compose 上下文解析宿主 Activity（ContextWrapper 逐层剥离，避免直接强转失败） */
private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** 打开本应用系统详情页（授权被永久拒绝后的引导出口） */
private fun openAppDetails(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
