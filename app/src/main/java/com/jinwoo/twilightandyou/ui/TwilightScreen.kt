package com.jinwoo.twilightandyou.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    message: String?
) {
    var shape by remember { mutableStateOf(WidgetShape.COMPACT) }
    val preview = settings.copy(showSample = true)
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
                Text("PREVIEW 01", fontSize = 9.sp, letterSpacing = 1.sp, color = Muted)
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
                    ComposeWidgetPreview(preview, shape)
                    Text("샘플 화면 · 실제 날씨가 아닙니다", color = Color(0xFFFDF0E8), fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
                }
                Text("미 = 미세먼지   초 = 초미세먼지", color = Muted, fontSize = 11.sp)
            }
            SettingSection("01", "고정 지역") {
                ChoiceRow(listOf("서울", "부산", "제주", "강릉"), settings.region, { it }) { onChange(settings.copy(region = it)) }
                Text("이번 버전은 지역 이름과 배치를 확인하는 단계입니다. 위치 확인과 실제 자료 연결은 다음 단계에 추가합니다.", color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
            }
            SettingSection("02", "지정 박명") {
                ChoiceRow(TwilightKind.entries, settings.twilight, { it.title }) { onChange(settings.copy(twilight = it)) }
                ChoiceRow(EventMode.entries, settings.eventMode, { it.title }) { onChange(settings.copy(eventMode = it)) }
                Text("샘플 시각은 10월 7일 17시 상황입니다. 다음 박명은 저녁 시각을 보여 줍니다.", color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
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
                        Text("끄면 미연결 상태로 표시합니다.", color = Muted, fontSize = 11.sp)
                    }
                    Switch(checked = settings.showSample, onCheckedChange = { onChange(settings.copy(showSample = it)) })
                }
                Text("관측 · 예보 연결 전\n실측 자료가 없는 칸을 예보나 샘플로 채우지 않습니다.", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
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
private fun ComposeWidgetPreview(settings: WidgetSettings, shape: WidgetShape) {
    val model = WidgetPresentation.from(settings)
    val color = Color(settings.palette.foreground)
    Box(
        Modifier.size(shape.width.dp, shape.height.dp).clip(RoundedCornerShape(18.dp))
            .background(Color(settings.palette.background).copy(alpha = settings.opacity / 100f)).padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (shape != WidgetShape.COMPACT) Text(settings.region, color = color.copy(alpha = 0.7f), fontSize = 10.sp)
            Text("${model.temperature} ${model.weather}", fontSize = (24 * settings.fontScale).sp, color = color, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                PreviewDust("미", model.pm10Grade, color)
                PreviewDust("초", model.pm25Grade, color)
            }
            if (shape == WidgetShape.TALL) Spacer(Modifier.height(15.dp))
            Text("${model.eventName} ${model.eventTime}", fontSize = (10 * settings.fontScale).sp, color = color, fontWeight = FontWeight.Medium)
            Text("샘플 10/07 17시", color = color.copy(alpha = 0.65f), fontSize = 8.sp)
        }
    }
}

@Composable
private fun PreviewDust(label: String, grade: DustGrade, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.semantics { contentDescription = "$label ${grade.title}" }) {
        Text(label, fontSize = 10.sp, color = textColor)
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(grade.argb)))
    }
}
