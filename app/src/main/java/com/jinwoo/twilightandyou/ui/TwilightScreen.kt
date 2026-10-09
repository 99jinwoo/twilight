package com.jinwoo.twilightandyou.ui

import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.compose
import com.jinwoo.twilightandyou.widget.TwilightWidget
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jinwoo.twilightandyou.model.*

private val Ink = Color(0xFF14141E)
private val Panel = Color(0xFF232332)
private val Cream = Color(0xFFF7EEE7)
private val Peach = Color(0xFFFFBB8A)
private val Muted = Color(0xFFB5AFC1)

@Composable
fun TwilightTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Peach, onPrimary = Ink, background = Ink, surface = Panel,
            onBackground = Cream, onSurface = Cream, surfaceVariant = Color(0xFF343345),
            onSurfaceVariant = Muted
        ),
        content = content
    )
}

@Composable
fun TwilightScreen(
    settings: WidgetSettings,
    onChange: (WidgetSettings) -> Unit,
    onPin: ((WidgetShape) -> Unit)?,
    onSave: (() -> Unit)?,
    editingWidget: Boolean,
    busy: Boolean,
    message: String?,
    widgetId: Int = 0
) {
    val context = LocalContext.current
    val orientation = androidx.compose.ui.platform.LocalConfiguration.current.orientation
    val installedSize = remember(widgetId, orientation) {
        if (widgetId <= 0) null else {
            val options = android.appwidget.AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
            val portrait = orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val width = options.getInt(if (portrait) android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            val height = options.getInt(if (portrait) android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            if (width > 0 && height > 0) DpSize(width.dp, height.dp) else null
        }
    }
    var shape by remember(widgetId, installedSize) { mutableStateOf(installedSize?.let { WidgetShape.forSize(it.width.value, it.height.value) } ?: WidgetShape.COMPACT) }
    val preview = settings
    var revision by remember { mutableIntStateOf(0) }
    Scaffold(
        containerColor = Ink,
        bottomBar = {
            if (onSave != null) Surface(color = Ink) {
                Button(
                    onClick = onSave, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp).height(52.dp)
                ) { Text(if (busy) "저장 중…" else "이 위젯으로 저장", fontWeight = FontWeight.Bold) }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("TWILIGHT / AND YOU", fontSize = 11.sp, letterSpacing = 2.sp, color = Peach, fontWeight = FontWeight.Bold)
                Text("PREVIEW 03", fontSize = 9.sp, letterSpacing = 1.sp, color = Muted)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (editingWidget) "나의 위젯을\n다듬는 시간." else "날씨와 빛,\n나의 작은 창.", fontSize = 32.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold)
                Text("기온 · 두 가지 먼지 · 내가 고른 박명", color = Muted, fontSize = 13.sp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceRow(WidgetShape.entries, shape, { it.title }) { shape = it }
                Box(
                    modifier = Modifier.fillMaxWidth().height(264.dp).clip(RoundedCornerShape(26.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF3D354E), Color(0xFF88647C), Color(0xFFCCA092)))),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(Color(0x66FFD8B0), radius = size.width * 0.15f, center = Offset(size.width * 0.76f, size.height * 0.31f))
                        drawCircle(Color(0x443D354E), radius = size.width * 0.9f, center = Offset(size.width * 0.8f, size.height * 1.9f))
                    }
                    ComposeWidgetPreview(preview, shape, revision, installedSize)
                    Text(if (settings.showSample) "샘플 화면 · 실제 날씨가 아닙니다" else "박명은 기기 계산 · 날씨는 저장된 실제 자료", color = Color(0xFFFDF0E8), fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
                }
                Text("미 = 미세먼지   초미 = 초미세먼지", color = Muted, fontSize = 11.sp)
                Text("홈 격자에 따라 실제 크기가 달라집니다. 좁은 칸에서는 글자를 줄여 필수 정보를 유지합니다.", color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
            }
            LiveDataPanel(settings, onChange, onUpdated = { revision++ })
            SettingSection("01", "지역과 위치") {
                ChoiceRow(listOf("서울", "부산", "제주", "강릉"), settings.region, { it }) { onChange(settings.copy(region = it, latitude = null, longitude = null, airArea = null, station = "", autoLocation = false, autoStation = true)) }
                ManualRegionSettings(settings, onChange)
            }
            SettingSection("02", "지정 박명") {
                ChoiceRow(TwilightKind.entries, settings.twilight, { it.title }) { onChange(settings.copy(twilight = it)) }
                Text("다음 박명을 굵게 위에, 직전 박명을 작게 아래에 표시합니다. 어제·오늘·내일은 한국 시간을 기준으로 바뀝니다.", color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
            }
            SettingSection("03", "나의 색과 크기") {
                ChoiceRow(WidgetPalette.entries, settings.palette, { it.title }) { onChange(settings.copy(palette = it)) }
                Text("배경 불투명도 ${settings.opacity}%", color = Muted, fontSize = 12.sp)
                Slider(value = settings.opacity.toFloat(), onValueChange = { onChange(settings.copy(opacity = it.toInt())) },
                    valueRange = 25f..100f, steps = 2)
                ChoiceRow(listOf(0.85f, 1f, 1.15f), settings.fontScale,
                    { if (it < 1f) "작게" else if (it == 1f) "기본" else "크게" }) { onChange(settings.copy(fontScale = it)) }
            }
            SettingSection("04", "홈화면 표시") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("홈 위젯에도 샘플 표시", fontSize = 14.sp)
                        Text("켜면 가상 값, 끄면 기기 계산·실제 자료를 표시합니다.", color = Muted, fontSize = 11.sp)
                    }
                    Switch(checked = settings.showSample, onCheckedChange = { onChange(settings.copy(showSample = it)) })
                }
                Text("박명은 선택 지역의 좌표로 기기에서 계산합니다. 실제 날씨·먼지는 API 연결과 새로고침 후 표시됩니다. 샘플을 켜면 실제 자료와 섞지 않고 가상 값만 표시합니다.", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
                if (onPin != null) Button(onClick = { onPin(shape) }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = !busy) {
                    Text("${shape.title} 홈화면에 추가", fontWeight = FontWeight.Bold)
                }
                Text("홈화면 위젯을 누르면 해당 위젯의 설정을 다시 바꿀 수 있습니다.", color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
            }
            if (message != null) Text(message, color = Peach, fontSize = 12.sp)
            Text("TWILIGHT AND YOU\n당신의 하루, 그 사이의 빛.", color = Muted, fontSize = 10.sp, lineHeight = 18.sp, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun <T> ChoiceRow(items: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        items.forEach { item ->
            FilterChip(selected = item == selected, onClick = { onSelect(item) },
                label = { Text(label(item), fontSize = 11.sp, maxLines = 1) },
                modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SettingSection(number: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(number, color = Peach, fontSize = 10.sp)
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        content()
        HorizontalDivider(color = Color(0xFF343241), modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun ComposeWidgetPreview(settings: WidgetSettings, shape: WidgetShape, revision: Int, installedSize: DpSize?) {
    val context = LocalContext.current
    val matches = installedSize != null && WidgetShape.forSize(installedSize.width.value, installedSize.height.value) == shape
    val previewWidth = if (matches) installedSize!!.width.value.toInt() else shape.width
    val previewHeight = if (matches) installedSize!!.height.value.toInt() else shape.height
    var remoteViews by remember { mutableStateOf<RemoteViews?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(settings, shape, revision, previewWidth, previewHeight) {
        failed = false
        try {
            remoteViews = TwilightWidget(settings).compose(context, size = DpSize(previewWidth.dp, previewHeight.dp))
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
    }
    Box(Modifier.size(previewWidth.dp, previewHeight.dp), contentAlignment = Alignment.Center) {
        if (failed) Text("미리보기를 불러오지 못했습니다", fontSize = 10.sp)
        else remoteViews?.let { snapshot ->
            AndroidView(
                factory = { FrameLayout(it) },
                modifier = Modifier.fillMaxSize(),
                update = { host ->
                    if (host.tag !== snapshot) {
                        host.removeAllViews()
                        host.addView(snapshot.apply(context, host))
                        host.tag = snapshot
                    }
                }
            )
        }
    }
}
