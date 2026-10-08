package com.jinwoo.twilightandyou.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import com.jinwoo.twilightandyou.widget.widgetIds
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.glance.appwidget.updateAll
import com.jinwoo.twilightandyou.astronomy.Coordinates
import com.jinwoo.twilightandyou.astronomy.SolarCalculator
import com.jinwoo.twilightandyou.data.*
import com.jinwoo.twilightandyou.data.remote.ApiFailure
import com.jinwoo.twilightandyou.model.*
import com.jinwoo.twilightandyou.widget.TwilightWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Composable
fun LiveDataPanel(settings: WidgetSettings, onChange: (WidgetSettings) -> Unit, onUpdated: () -> Unit,
    dataRepository: LiveRepository? = null,
    automaticLocator: (suspend (WidgetSettings) -> LocationUpdate)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context, dataRepository) { dataRepository ?: LiveRepository(context) }
    var data by remember { mutableStateOf(LiveSnapshot()) }
    var busy by remember { mutableStateOf(false) }
    var keyDialog by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var stationMessage by remember { mutableStateOf<String?>(null) }
    var stations by remember { mutableStateOf<List<AirStation>>(emptyList()) }
    var stationText by remember(settings.station) { mutableStateOf(settings.station) }
    val latestSettings by rememberUpdatedState(settings)
    val now = ZonedDateTime.now(SEOUL)
    LaunchedEffect(settings.region, settings.latitude, settings.longitude, settings.station, settings.forecastArea) {
        data = LiveSnapshot()
        stations = emptyList()
        try { data = repository.cached(settings) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { message = "연결 정보를 읽지 못했습니다. API 연결에서 키를 다시 저장해주세요." }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var refreshJob by remember { mutableStateOf<Job?>(null) }
    val latestOnChange by rememberUpdatedState(onChange)
    suspend fun refreshData(requested: WidgetSettings, locate: Boolean) {
            if (busy) return
            var target = requested
            busy = true; message = null
            try {
                var locationMessage: String? = null
                if (locate && requested.autoLocation) {
                    val update = automaticLocator?.invoke(requested) ?: resolveAutomaticLocation(requested, locate = {
                        val allowed = listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
                            .any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
                        if (allowed) currentCoordinates(context) else null
                    }, describe = { describeCoordinates(context, it) }, stations = { repository.nearestStations(it) })
                    // Discard a result if the user changed location/mode while the request was running.
                    if (latestSettings.point != requested.point || latestSettings.autoLocation != requested.autoLocation ||
                        latestSettings.station != requested.station || latestSettings.forecastArea != requested.forecastArea) return
                    locationMessage = update.message
                    if (update.located) {
                        target = latestSettings.copy(region = update.settings.region, latitude = update.settings.latitude,
                            longitude = update.settings.longitude, airArea = update.settings.airArea, station = update.settings.station)
                        latestOnChange(target)
                        SettingsStore(context).updateAutomaticLocations(widgetIds(context).toList() + 0, target)
                        data = LiveSnapshot()
                    }
                }
                val refreshed = repository.refresh(target, manual = true)
                // This coroutine belongs to the currently displayed location, not an earlier draft.
                if (latestSettings.point == target.point && latestSettings.station == target.station && latestSettings.forecastArea == target.forecastArea) {
                    data = refreshed
                    onUpdated()
                    message = locationMessage ?: "저장된 지역의 자료를 확인했습니다. 항목별 상태와 기준 시각을 확인해주세요."
                }
                TwilightWidget().updateAll(context)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = "자료를 확인하지 못했습니다. 연결 설정을 확인해주세요." }
            finally { busy = false }
    }
    fun refresh(target: WidgetSettings = settings, locate: Boolean = true) {
        if (!busy) refreshJob = scope.launch { refreshData(target, locate) }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) refreshJob?.cancel()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); refreshJob?.cancel() }
    }
    LaunchedEffect(lifecycle, settings.autoLocation) {
        if (settings.autoLocation) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            refreshData(latestSettings, locate = true)
            awaitCancellation()
        }
    }
    fun applyStation(name: String) {
        val target = latestSettings.copy(station = name.trim())
        onChange(target)
        stations = emptyList()
        stationMessage = "${target.station} 측정소를 적용하고 자료를 조회합니다."
        refresh(target, locate = false)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("실제 자료", style = MaterialTheme.typography.titleMedium)
        if (settings.showSample) Text("위 미리보기와 홈 위젯은 샘플 모드입니다. 아래는 실제 자료입니다.", fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { refresh() }, enabled = !busy && settings.point != null) { Text(if (busy) "확인 중…" else "새로고침") }
            OutlinedButton(onClick = { keyDialog = true }, enabled = !busy) { Text("API 연결") }
        }
        val weather = data.weather
        Text("기상청 실황  ${temperatureLabel(weather?.temperature)}", style = MaterialTheme.typography.titleMedium)
        Text("관측 ${timeLabel(weather?.at)}${if (weather != null && isStale(weather.at, now)) " · 오래된 자료" else ""}", fontSize = 11.sp)
        Text("강수가 없다는 이유로 맑음으로 표시하지 않습니다. 하늘상태를 예보로 보충한 아이콘에는 ‘예’를 붙입니다.", fontSize = 11.sp)
        val sky = data.currentSkyForecast(now)
        if (weather?.precipitation == 0 && !isStale(weather.at, now) && sky?.sky in setOf(1, 3, 4)) {
            Text("아이콘 하늘상태: 기상청 예보 · 대상 ${timeLabel(sky?.at)} · 발표 ${timeLabel(data.forecastIssuedAt)}", fontSize = 11.sp)
        }
        Text("시간별 날씨 예보", style = MaterialTheme.typography.titleSmall)
        Text("발표 ${timeLabel(data.forecastIssuedAt)}${if (data.forecastIssuedAt?.isBefore(now.minusHours(24)) == true) " · 오래된 발표" else ""}", fontSize = 11.sp)
        val hours = data.forecast.filter { it.at.isAfter(now) }.take(24)
        if (hours.isEmpty()) Text("예보 자료 없음", fontSize = 12.sp)
        else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            hours.forEach { hour ->
                Column {
                    Text(relativeDay(hour.at.toLocalDate(), now.toLocalDate()), fontSize = 10.sp)
                    Text(hour.at.format(DateTimeFormatter.ofPattern("HH시")), fontSize = 12.sp)
                    val night = settings.point?.let { SolarCalculator.solarAltitude(hour.at.toInstant(), it) < -0.8333 } ?: false
                    Text(weatherEmoji(hour.precipitation, hour.sky, night), fontSize = 20.sp)
                    Text(temperatureLabel(hour.temperature), fontSize = 14.sp)
                }
            }
        }
        HorizontalDivider()
        Text("에어코리아 실측", style = MaterialTheme.typography.titleSmall)
        Text("가까운 측정소 목록에서 선택하면 바로 조회합니다. 직접 입력할 때는 시·군 이름이 아닌 정확한 측정소 이름을 사용해주세요.", fontSize = 11.sp)
        if (settings.autoLocation) Text("현재 위치 모드에서는 다음 위치 갱신 때 가까운 측정소를 다시 선택합니다.", fontSize = 11.sp)
        OutlinedTextField(stationText, { stationText = it.take(60) }, label = { Text("선택한 측정소 이름") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { applyStation(stationText) }, enabled = !busy && stationText.isNotBlank()) { Text("적용 후 조회") }
            TextButton(onClick = {
                scope.launch {
                    busy = true; stationMessage = null
                    try {
                        val found = repository.nearestStations(settings)
                        if (latestSettings.point == settings.point) {
                            stations = found
                            if (stations.isEmpty()) stationMessage = "측정소를 찾지 못했습니다. 이름을 직접 입력할 수 있습니다."
                        }
                    }
                    catch (e: CancellationException) { throw e }
                    catch (e: ApiFailure) { stationMessage = SourceStatus("측정소", issue = e.problem, httpStatus = e.httpStatus, providerCode = e.providerCode).description() }
                    catch (_: Exception) { stationMessage = "측정소를 찾지 못했습니다. 에어코리아 측정소정보 서비스 승인 상태를 확인해주세요." }
                    finally { busy = false }
                }
            }, enabled = !busy && settings.point != null) { Text("가까운 측정소 찾기") }
        }
        stations.filter { settings.point != null }.forEach { station ->
            TextButton(onClick = { applyStation(station.name) }, enabled = !busy) {
                Text("${station.name} · ${"%.1f".format(settings.point!!.distanceKm(station.point))}km\n${station.address}", fontSize = 12.sp)
            }
        }
        stationMessage?.let { Text(it, fontSize = 11.sp) }
        val air = data.air
        Text("${settings.station.ifBlank { "측정소 미선택" }} · 관측 ${timeLabel(air?.at)}${if (air != null && isStale(air.at, now)) " · 오래된 자료" else ""}", fontSize = 11.sp)
        Text("미  ${air?.pm10?.let { "$it μg/m³ · ${DustGrade.fromConcentration(it, false).title}" } ?: "자료 없음"}${air?.pm10Flag?.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()}", fontSize = 13.sp)
        Text("초미  ${air?.pm25?.let { "$it μg/m³ · ${DustGrade.fromConcentration(it, true).title}" } ?: "자료 없음"}${air?.pm25Flag?.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()}", fontSize = 13.sp)
        Text("색상은 한 시간 관측 농도를 앱 기준으로 분류합니다. 2시간을 넘긴 관측은 위젯에서 빈 값으로 표시하고 여기서 기준 시각과 함께 확인할 수 있습니다.", fontSize = 11.sp)
        Text("지역별 먼지 예보 · ${settings.forecastArea.ifBlank { "권역 미선택" }}", style = MaterialTheme.typography.titleSmall)
        for (offset in 0L..1L) {
            val date = now.toLocalDate().plusDays(offset)
            val pm10 = data.airForecasts.firstOrNull { it.date == date && it.pollutant == "PM10" }
            val pm25 = data.airForecasts.firstOrNull { it.date == date && it.pollutant == "PM25" }
            Text("${relativeDay(date, now.toLocalDate())} 예보 · 미 ${pm10?.grade(settings.forecastArea) ?: "자료 없음"} / 초미 ${pm25?.grade(settings.forecastArea) ?: "자료 없음"}", fontSize = 13.sp)
            Text("발표 · 미 ${timeLabel(pm10?.issuedAt)} / 초미 ${timeLabel(pm25?.issuedAt)}", fontSize = 10.sp)
        }
        data.statuses.forEach { status ->
            Text("${status.name} · ${status.description()}\n조회 ${timeLabel(status.attemptedAt)} · 수신 ${timeLabel(status.receivedAt)}" +
                if (status.issue != null) if (status.receivedAt == null) "\n저장된 자료 없음" else "\n마지막 수신 자료 유지" else "", fontSize = 10.sp)
        }
        Text("수동 재조회는 같은 자료당 1분 간격입니다. 측정소 목록은 나오는데 실측·예보만 인증 오류라면 대기오염정보 서비스의 활용 승인과 기능 선택을 확인해주세요.", fontSize = 11.sp)
        if (message != null) Text(message!!, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.data.go.kr/data/15084084/openapi.do"))) }) { Text("기상청 자료 출처", fontSize = 11.sp) }
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.data.go.kr/data/15073861/openapi.do"))) }) { Text("에어코리아 자료 출처", fontSize = 11.sp) }
    }
    if (keyDialog) ApiKeyDialog(onDismiss = { keyDialog = false }, onSaved = { keyDialog = false; refresh() }, onCleared = {
        keyDialog = false; data = LiveSnapshot(); onUpdated(); scope.launch { TwilightWidget().updateAll(context) }
    })
}

@Composable
private fun ApiKeyDialog(onDismiss: () -> Unit, onSaved: () -> Unit, onCleared: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var existing by remember { mutableStateOf(ApiKeys()) }
    var kma by remember { mutableStateOf("") }
    var air by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try { existing = ApiKeyStore(context).read() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "저장된 연결 정보를 읽지 못했습니다. 키를 다시 입력해주세요." }
        finally { busy = false }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text("공공데이터 연결") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("공공데이터포털의 일반 인증키를 입력하세요. 해당 공공 API 호출에만 사용하며 기기에 암호화해서 저장합니다. 저장소로 보내지 않습니다.", fontSize = 12.sp)
                OutlinedTextField(kma, { kma = it.take(4096) }, label = { Text("기상청 키${if (existing.kma.isNotBlank()) " · 저장됨" else ""}") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(air, { air = it.take(4096) }, label = { Text("에어코리아 키${if (existing.air.isNotBlank()) " · 저장됨" else ""}") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                Text("기상청 단기예보, 에어코리아 대기오염정보·측정소정보의 활용 승인이 필요합니다. 빈 칸은 기존 키를 유지합니다.", fontSize = 11.sp)
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.data.go.kr/data/15084084/openapi.do"))) }) { Text("기상청 신청") }
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.data.go.kr/data/15073861/openapi.do"))) }) { Text("에어코리아 신청") }
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.data.go.kr/data/15073877/openapi.do"))) }) { Text("측정소정보 신청") }
                TextButton(onClick = {
                    scope.launch {
                        busy = true
                        try { ApiKeyStore(context).clear(); LiveRepository(context).clearCache(); onCleared() }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { error = "연결 정보를 지우지 못했습니다." }
                        finally { busy = false }
                    }
                }, enabled = !busy) { Text("연결 정보와 저장 자료 지우기") }
            }
        },
        confirmButton = { TextButton(onClick = {
            scope.launch {
                busy = true; error = null
                try {
                    ApiKeyStore(context).save(ApiKeys(kma.trim().ifBlank { existing.kma }, air.trim().ifBlank { existing.air }))
                    LiveRepository(context).clearCache()
                    kma = ""; air = ""; onSaved()
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { error = "키를 저장하지 못했습니다. 다시 시도해주세요." }
                finally { busy = false }
            }
        }, enabled = !busy) { Text("저장 후 연결") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("닫기") } }
    )
}

@Composable
fun ManualRegionSettings(settings: WidgetSettings, onChange: (WidgetSettings) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editor by remember { mutableStateOf(false) }
    var locatedDraft by remember { mutableStateOf<WidgetSettings?>(null) }
    val latestSettings by rememberUpdatedState(settings)
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    fun locate() {
        scope.launch {
            busy = true; message = null
            try {
                val point = currentCoordinates(context)
                if (point == null) message = "위치를 확인하지 못했습니다. 기존 지역을 유지합니다."
                else if (point.latitude !in 32.0..40.0 || point.longitude !in 123.0..133.0) message = "현재는 국내 지역을 지원합니다. 기존 지역을 유지합니다."
                else { locatedDraft = latestSettings.copy(region = "내 위치", latitude = point.latitude, longitude = point.longitude, airArea = "", station = ""); editor = true }
            } catch (_: TimeoutCancellationException) { message = "위치 확인 시간이 초과됐습니다. 기존 지역을 유지합니다." }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = "위치 서비스를 확인해주세요. 기존 지역을 유지합니다." }
            finally { busy = false }
        }
    }
    var enableAutomatic by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) {
            if (enableAutomatic) onChange(latestSettings.copy(autoLocation = true)) else locate()
        } else message = "위치 권한이 없어 기존 지역을 유지합니다."
        enableAutomatic = false
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("앱 실행·새로고침 때 현재 위치", modifier = Modifier.weight(1f), fontSize = 13.sp)
            Switch(checked = settings.autoLocation, onCheckedChange = { enabled ->
                if (enabled) {
                    enableAutomatic = true
                    permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                } else onChange(latestSettings.copy(autoLocation = false))
            })
        }
        settings.point?.let { Text("${settings.region} · ${"%.4f, %.4f".format(it.latitude, it.longitude)}\n먼지 예보권역: ${settings.forecastArea.ifBlank { "선택 필요" }}", fontSize = 11.sp) }
        Row {
            TextButton(onClick = { enableAutomatic = false; permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) }, enabled = !busy) { Text(if (busy) "위치 확인 중…" else "현재 위치 한 번 확인") }
            TextButton(onClick = { locatedDraft = null; editor = true }, enabled = !busy) { Text("직접 지정") }
        }
        Text(if (settings.autoLocation) "앱을 열거나 새로고침할 때 위치와 가까운 측정소를 갱신합니다. 앱을 닫으면 마지막 위치를 사용합니다." else "고정 지역을 사용합니다. 위 옵션을 켜면 앱 실행·새로고침 때 현재 위치로 바뀝니다.", fontSize = 11.sp)
        if (message != null) Text(message!!, fontSize = 11.sp)
    }
    if (editor) RegionDialog(locatedDraft ?: settings, onDismiss = { editor = false; locatedDraft = null }, onApply = { onChange(it); editor = false; locatedDraft = null })
}

@Composable
private fun RegionDialog(settings: WidgetSettings, onDismiss: () -> Unit, onApply: (WidgetSettings) -> Unit) {
    var name by remember { mutableStateOf(settings.region) }
    var latitude by remember { mutableStateOf(settings.point?.latitude?.toString().orEmpty()) }
    var longitude by remember { mutableStateOf(settings.point?.longitude?.toString().orEmpty()) }
    var area by remember { mutableStateOf(settings.forecastArea) }
    var expanded by remember { mutableStateOf(false) }
    val point = runCatching { Coordinates(latitude.toDouble(), longitude.toDouble()) }.getOrNull()
    val valid = point != null && point.latitude in 32.0..40.0 && point.longitude in 123.0..133.0 && name.isNotBlank() && area.isNotBlank()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("고정 지역") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it.take(20) }, label = { Text("지역 이름") }, singleLine = true)
            OutlinedTextField(latitude, { latitude = it }, label = { Text("위도 · 예: 37.5665") }, singleLine = true)
            OutlinedTextField(longitude, { longitude = it }, label = { Text("경도 · 예: 126.9780") }, singleLine = true)
            Box {
                OutlinedButton(onClick = { expanded = true }) { Text(area.ifBlank { "먼지 예보권역 선택" }) }
                DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                    Regions.airAreas.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { area = item; expanded = false }) }
                }
            }
            Text("좌표와 예보권역을 확인해주세요. 먼지 관측 측정소는 실제 자료에서 별도로 고릅니다.", fontSize = 11.sp)
        }
    }, confirmButton = { TextButton(onClick = { onApply(settings.copy(region = name.trim(), latitude = point!!.latitude, longitude = point.longitude, airArea = area, station = "", autoLocation = false)) }, enabled = valid) { Text("이 지역 적용") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } })
}

private fun timeLabel(time: ZonedDateTime?): String = time?.withZoneSameInstant(SEOUL)?.format(DateTimeFormatter.ofPattern("MM/dd HH:mm")) ?: "—"
