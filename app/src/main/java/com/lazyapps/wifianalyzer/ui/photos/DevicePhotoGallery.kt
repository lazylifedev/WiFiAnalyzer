package com.lazyapps.wifianalyzer.ui.photos

import android.graphics.BitmapFactory
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lazyapps.wifianalyzer.domain.DevicePhoto
import com.lazyapps.wifianalyzer.domain.DevicePhotoPolicy
import com.lazyapps.wifianalyzer.ui.theme.AppSpacing
import java.io.File

@Composable
fun DevicePhotoGallery(deviceId: Long, workspaceId: Long, photoViewModel: DevicePhotoViewModel = viewModel()) {
    LaunchedEffect(deviceId, workspaceId) { photoViewModel.bind(deviceId, workspaceId) }
    val state by photoViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAdd by remember { mutableStateOf(false) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var deleteTarget by remember { mutableStateOf<DevicePhoto?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(DevicePhotoPolicy.MAX_PHOTOS_PER_DEVICE)) { photoViewModel.add(it) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved -> cameraFile?.let { file -> if (saved) photoViewModel.add(listOf(Uri.fromFile(file))); else file.delete() } }

    Card(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("写真 ${state.photos.size} / ${DevicePhotoPolicy.MAX_PHOTOS_PER_DEVICE}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(enabled = state.photos.size < DevicePhotoPolicy.MAX_PHOTOS_PER_DEVICE && !state.busy, onClick = { showAdd = true }, modifier = Modifier.testTag("add_photo")) { Icon(Icons.Rounded.Add, null); Text("追加") }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.photos.isEmpty()) Text("写真を追加すると、設置状況やラベルを機器と一緒に管理できます。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                state.photos.forEachIndexed { index, photo ->
                    Box(Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).clickable { viewerIndex = index }.semantics { contentDescription = "写真 ${index + 1}/${state.photos.size}${if (photo.isPrimary) " メイン写真" else ""}${photo.caption.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()}" }) {
                        PhotoImage(photo, photoViewModel, Modifier.fillMaxSize(), thumbnail = true)
                        if (photo.isPrimary) Icon(Icons.Rounded.Star, "メイン写真", tint = Color.Yellow, modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha = .55f), RoundedCornerShape(8.dp)).padding(3.dp))
                    }
                }
            }
            if (state.photos.size >= DevicePhotoPolicy.MAX_PHOTOS_PER_DEVICE) Text("写真は9枚まで登録できます", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        }
    }
    if (showAdd) AlertDialog(onDismissRequest = { showAdd = false }, title = { Text("写真を追加") }, text = { Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
        Button(onClick = { showAdd = false; val file = File.createTempFile("device-photo-", ".jpg", context.cacheDir); cameraFile = file; camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file)) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.CameraAlt, null); Text("カメラで撮影") }
        Button(onClick = { showAdd = false; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.PhotoLibrary, null); Text("端末から選択") }
    } }, confirmButton = {}, dismissButton = { TextButton(onClick = { showAdd = false }) { Text("キャンセル") } })
    viewerIndex?.let { index -> if (state.photos.isNotEmpty()) PhotoViewer(state.photos, index.coerceAtMost(state.photos.lastIndex), photoViewModel, { viewerIndex = null }, { deleteTarget = it }) }
    deleteTarget?.let { photo -> AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text("写真を削除しますか？") }, text = { Text("削除した写真は元に戻せません。欠落・破損したファイルも一覧から削除できます。") }, confirmButton = { Button(onClick = { photoViewModel.delete(photo.id); deleteTarget = null; if (state.photos.size <= 1) viewerIndex = null }) { Text("削除") } }, dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("キャンセル") } }) }
}

@Composable private fun PhotoImage(photo: DevicePhoto, vm: DevicePhotoViewModel, modifier: Modifier, thumbnail: Boolean = false) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, photo.id, photo.updatedAt, thumbnail) { value = runCatching { BitmapFactory.decodeFile(vm.repository.file(photo).absolutePath, BitmapFactory.Options().apply { inSampleSize = if (thumbnail) 8 else 1 }) }.getOrNull() }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), photo.caption.ifBlank { "機器写真" }, modifier, contentScale = if (thumbnail) ContentScale.Crop else ContentScale.Fit) else Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text("画像なし", style = MaterialTheme.typography.labelSmall) }
}

@Composable private fun PhotoViewer(photos: List<DevicePhoto>, start: Int, vm: DevicePhotoViewModel, onClose: () -> Unit, onDelete: (DevicePhoto) -> Unit) {
    val pager = rememberPagerState(start) { photos.size }
    var captionPhoto by remember { mutableStateOf<DevicePhoto?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var immersive by remember { mutableStateOf(false) }
    var zoomed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val originalOrientation = remember(activity) { activity.requestedOrientation }
    DisposableEffect(activity) {
        onDispose {
            activity.requestedOrientation = originalOrientation
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).show(WindowInsetsCompat.Type.systemBars())
        }
    }
    LaunchedEffect(immersive) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (immersive) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
    }
    LaunchedEffect(pager.currentPage) { zoomed = false }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize().navigationBarsPadding(), userScrollEnabled = !zoomed) { page ->
                ZoomablePhoto(photos[page], vm) { if (page == pager.currentPage) zoomed = it }
            }
            Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).background(Color.Black.copy(alpha = .6f)).statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "写真ビューアを閉じる", tint = Color.White) }
                Text("${pager.currentPage + 1} / ${photos.size}", Modifier.weight(1f), color = Color.White)
                IconButton(onClick = { immersive = !immersive }) { Icon(if (immersive) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, if (immersive) "全画面を終了" else "全画面", tint = Color.White) }
                IconButton(onClick = {
                    activity.requestedOrientation = if (activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }) { Icon(Icons.Rounded.ScreenRotation, "画面の縦横を切り替え", tint = Color.White) }
                Box {
                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Rounded.MoreVert, "写真メニュー", tint = Color.White) }
                    DropdownMenu(menuExpanded, { menuExpanded = false }) {
                        DropdownMenuItem({ Text("メイン写真に設定") }, onClick = { menuExpanded = false; vm.primary(photos[pager.currentPage].id) }, leadingIcon = { Icon(Icons.Rounded.Star, null) })
                        DropdownMenuItem({ Text("キャプション編集") }, onClick = { menuExpanded = false; captionPhoto = photos[pager.currentPage] }, leadingIcon = { Icon(Icons.Rounded.Edit, null) })
                        DropdownMenuItem({ Text("削除") }, onClick = { menuExpanded = false; onDelete(photos[pager.currentPage]) }, leadingIcon = { Icon(Icons.Rounded.Delete, null) })
                    }
                }
            }
            photos[pager.currentPage].caption.takeIf { it.isNotBlank() }?.let { caption ->
                Text(caption, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().background(Color.Black.copy(alpha = .6f)).padding(12.dp), color = Color.White)
            }
        }
    }
    captionPhoto?.let { photo -> var text by remember(photo.id) { mutableStateOf(photo.caption) }; AlertDialog(onDismissRequest = { captionPhoto = null }, title = { Text("キャプション") }, text = { OutlinedTextField(text, { text = it }, label = { Text("例: 背面ラベル") }) }, confirmButton = { Button(onClick = { vm.caption(photo.id, text); captionPhoto = null }) { Text("保存") } }, dismissButton = { TextButton(onClick = { captionPhoto = null }) { Text("キャンセル") } }) }
}

@Composable private fun ZoomablePhoto(photo: DevicePhoto, vm: DevicePhotoViewModel, onZoomedChange: (Boolean) -> Unit) {
    var scale by remember(photo.id) { mutableFloatStateOf(1f) }; var x by remember(photo.id) { mutableFloatStateOf(0f) }; var y by remember(photo.id) { mutableFloatStateOf(0f) }
    val transform = rememberTransformableState { zoom, pan, _ -> scale = (scale * zoom).coerceIn(1f, 5f); onZoomedChange(scale > 1.01f); if (scale > 1f) { x += pan.x; y += pan.y } else { x = 0f; y = 0f } }
    Box(Modifier.fillMaxSize().transformable(transform).pointerInput(photo.id) { detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; onZoomedChange(scale > 1f); if (scale == 1f) { x = 0f; y = 0f } }) }, contentAlignment = Alignment.Center) { PhotoImage(photo, vm, Modifier.fillMaxWidth().graphicsLayer(scaleX = scale, scaleY = scale, translationX = x, translationY = y)) }
}

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("Activity context is required")
}
