package kr.moagil.vehicle

import android.graphics.Color as AndroidColor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.PolylineOverlay
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kr.moagil.vehicle.data.DriverSession
import kr.moagil.vehicle.data.Passenger
import kr.moagil.vehicle.data.Point
import kr.moagil.vehicle.ui.DriverUiState
import kr.moagil.vehicle.ui.MoagilViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

private val Ink = Color(0xFFF4F5F9)
private val Panel = Color.White
private val PanelRaised = Color(0xFFF3F5FA)
private val Mint = Color(0xFF5D70E6)
private val AccentSoft = Color(0xFFEEF0FF)
private val Border = Color(0xFFE5E8F0)
private val Muted = Color(0xFF737B8D)
private val Success = Color(0xFF2B806B)
private val Alert = Color(0xFFB95765)
private val Scheme = lightColorScheme(
    primary = Mint,
    onPrimary = Color.White,
    secondary = Color(0xFF6979A5),
    background = Ink,
    onBackground = Color(0xFF202636),
    surface = Panel,
    onSurface = Color(0xFF30374A),
    error = Alert,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(244, 245, 249)
        window.navigationBarColor = android.graphics.Color.rgb(244, 245, 249)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent {
            MaterialTheme(colorScheme = Scheme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val model: MoagilViewModel = viewModel()
                    val state by model.state.collectAsState()
                    DriverScreen(state, model)
                }
            }
        }
    }
}

@Composable
private fun DriverScreen(state: DriverUiState, model: MoagilViewModel) {
    var pendingDelete by remember { mutableStateOf<Passenger?>(null) }
    var showFinish by remember { mutableStateOf(false) }
    var showNewSession by remember { mutableStateOf(false) }
    if (state.cancelConfirmation) {
        AlertDialog(
            onDismissRequest = { model.setCancelConfirmation(false) },
            title = { Text("이동을 취소할까요?") },
            text = { Text("참가 링크가 닫히며 진행 중인 시연을 취소합니다.") },
            confirmButton = { TextButton(onClick = { model.setCancelConfirmation(false); model.cancelTrip() }) { Text("이동 취소") } },
            dismissButton = { TextButton(onClick = { model.setCancelConfirmation(false) }) { Text("계속 진행") } },
        )
    }
    pendingDelete?.let { passenger ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("${passenger.displayName} 참가를 삭제할까요?") },
            text = { Text("모집 중인 참가자만 삭제할 수 있으며, 경로 계산은 다시 해야 합니다.") },
            confirmButton = { TextButton(onClick = { pendingDelete = null; model.removePassenger(passenger) }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("취소") } },
        )
    }
    if (showFinish) {
        AlertDialog(
            onDismissRequest = { showFinish = false },
            title = { Text("이동을 종료할까요?") },
            text = { Text("모든 하차 단계를 마쳤습니다. 시연 이동을 종료하고 참가 링크를 닫습니다.") },
            confirmButton = { TextButton(onClick = { showFinish = false; model.finishTrip() }) { Text("이동 종료") } },
            dismissButton = { TextButton(onClick = { showFinish = false }) { Text("계속 진행") } },
        )
    }
    if (showNewSession) {
        AlertDialog(
            onDismissRequest = { showNewSession = false },
            title = { Text("새 이동을 만들까요?") },
            text = { Text("이전 초대 QR을 닫고 새 이동 QR을 발급합니다.") },
            confirmButton = {
                TextButton(onClick = { showNewSession = false; model.createSession(false) }) { Text("새 이동 만들기") }
            },
            dismissButton = { TextButton(onClick = { showNewSession = false }) { Text("취소") } },
        )
    }

    if (state.session == null) {
        QrFirstScreen(state, model)
    } else {
        Column(Modifier.fillMaxSize().background(Ink)) {
            Header(state, onNewSession = { showNewSession = true })
            Divider(color = Border)
            SessionDashboard(state, model, onDelete = { pendingDelete = it }, onFinish = { showFinish = true }, modifier = Modifier.weight(1f))
            state.errorMessage?.let { MessageBanner(it, Alert, onDismiss = model::clearMessages) }
        }
    }
}

@Composable
private fun QrFirstScreen(state: DriverUiState, model: MoagilViewModel) {
    val session = state.session
    val credentials = state.credentials
    val inviteUrl = remember(session?.sessionId, credentials?.joinBaseUrl, credentials?.inviteToken) {
        if (session == null || credentials == null) "" else "${credentials.joinBaseUrl.trimEnd('/')}/join/${session.sessionId}?invite=${credentials.inviteToken}"
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        val qrSize = if (maxWidth > 900.dp) 440.dp else 280.dp
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("MOAGIL", color = Mint, fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            if (inviteUrl.isNotBlank()) {
                val qr = remember(inviteUrl) { renderQr(inviteUrl) }
                if (qr != null) {
                    Image(
                        qr.asImageBitmap(),
                        contentDescription = "동승자 초대 QR 코드",
                        modifier = Modifier.size(qrSize).background(Color.White, RoundedCornerShape(20.dp)).padding(14.dp),
                    )
                } else {
                    Box(Modifier.size(qrSize), contentAlignment = Alignment.Center) { Text("QR을 만들 수 없습니다.", color = Alert) }
                }
                Text("QR을 스캔해 내릴 곳을 등록하세요", color = Color(0xFF183B32), fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("등록 즉시 추천 순서를 계산합니다 · ${session?.passengers?.size ?: 0} / 4명", color = Muted, fontSize = 14.sp)
            } else {
                CircularProgressIndicator(color = Mint, modifier = Modifier.padding(20.dp))
                Text(if (state.busy) "초대 QR을 준비하고 있어요" else "서버 연결을 확인하고 있어요", color = Color(0xFF183B32), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                state.errorMessage?.let { Text(it, color = Alert, fontSize = 13.sp) }
                if (!state.busy) Button(onClick = { model.createSession(false) }) { Text("다시 연결") }
            }
        }
    }
}

@Composable
private fun Header(state: DriverUiState, onNewSession: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(Brush.linearGradient(listOf(Color(0xFF8192F1), Mint)), CircleShape), contentAlignment = Alignment.Center) {
                Text("길", color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
            }
            Spacer(Modifier.width(11.dp))
            Text("모아길", fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-0.3).sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(
                if (state.connected == true) "연결" else if (state.connected == false) "오프라인" else "연결 중",
                if (state.connected == true) Success else if (state.connected == false) Alert else Muted,
            )
            if (state.session != null) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onNewSession, enabled = !state.busy) { Text("새 QR", color = Muted) }
            }
        }
    }
}

@Composable
private fun StartPanel(state: DriverUiState, model: MoagilViewModel, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val wide = maxWidth > 900.dp
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("여러 목적지, 순서는 한 번에.", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("동승자는 휴대폰 웹에서 하차 지점을 등록하고, 운전자는 정차 상태에서 순서와 모의 안내를 확인합니다.", color = Muted, fontSize = 16.sp)
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ServerCard(state, model, Modifier.weight(1f))
                    StartActions(state, model, Modifier.weight(1f))
                }
            } else {
                ServerCard(state, model, Modifier.fillMaxWidth())
                StartActions(state, model, Modifier.fillMaxWidth())
            }
            SafetyNote("정차 전 운전자 변경은 잠깁니다. 이 앱은 차량 센서와 실제 내비를 사용하지 않습니다.")
        }
    }
}

@Composable
private fun ServerCard(state: DriverUiState, model: MoagilViewModel, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("서버 연결", "로컬 또는 같은 Wi-Fi의 서버 주소")
            OutlinedTextField(
                value = state.apiBaseUrl,
                onValueChange = model::setApiBaseUrl,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("서버 주소") },
                placeholder = { Text("http://192.168.0.10:8080") },
                singleLine = true,
            )
            Text("에뮬레이터 기본값은 10.0.2.2입니다. 실제 차량 기기에서는 PC의 사설 LAN 주소를 입력하고, QR 공개 주소는 서버의 MOAGIL_PUBLIC_BASE_URL로 지정하세요.", color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun StartActions(state: DriverUiState, model: MoagilViewModel, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("시연 시작", "빈 이동 또는 명시된 시연 참가자")
            Button(onClick = { model.createSession(false) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Text(if (state.busy) "연결 중…" else "새 이동 만들기", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = { model.createSession(true) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Text("시연 데이터로 시작")
            }
            Text("참가 최대 4명 · 이동 세션 2시간 · 로그인 불필요", color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SessionDashboard(state: DriverUiState, model: MoagilViewModel, onDelete: (Passenger) -> Unit, onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val session = state.session ?: return
    val credentials = state.credentials
    val terminal = session.state in setOf("COMPLETED", "CANCELLED", "EXPIRED")
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val wide = maxWidth > 900.dp
        if (wide) {
            Row(Modifier.fillMaxSize().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                RealRoadMapCard(
                    session,
                    state.credentials?.apiBaseUrl ?: state.apiBaseUrl,
                    state.credentials?.driverToken.orEmpty(),
                    Modifier.weight(1.5f).fillMaxHeight(),
                )
                if (terminal) {
                    SessionDetailsColumn(session, state, model, credentials?.joinBaseUrl.orEmpty(), credentials?.inviteToken.orEmpty(), onDelete, onFinish, true, Modifier.weight(1f).fillMaxHeight())
                } else {
                    LiveStopsPanel(session, state, model, credentials?.joinBaseUrl.orEmpty(), credentials?.inviteToken.orEmpty(), onFinish, Modifier.weight(1f).fillMaxHeight())
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    RealRoadMapCard(
                        session,
                        state.credentials?.apiBaseUrl ?: state.apiBaseUrl,
                        state.credentials?.driverToken.orEmpty(),
                        Modifier.fillMaxWidth().height(360.dp),
                    )
                }
                sessionDetailItems(session, state, model, credentials?.joinBaseUrl.orEmpty(), credentials?.inviteToken.orEmpty(), onDelete, onFinish, terminal)
            }
        }
    }
}

@Composable
private fun LiveStopsPanel(
    session: DriverSession,
    state: DriverUiState,
    model: MoagilViewModel,
    joinBaseUrl: String,
    inviteToken: String,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmPleosRoute by remember { mutableStateOf(false) }
    if (confirmPleosRoute) {
        AlertDialog(
            onDismissRequest = { confirmPleosRoute = false },
            title = { Text("안내 시작") },
            text = { Text("첫 목적지를 Pleos 내비에 보냅니다.") },
            confirmButton = { TextButton(onClick = { confirmPleosRoute = false; model.startRoute() }) { Text("시작") } },
            dismissButton = { TextButton(onClick = { confirmPleosRoute = false }) { Text("취소") } },
        )
    }

    val peopleById = session.passengers.associateBy(Passenger::id)
    val registrationIndex = session.passengers.mapIndexed { index, person -> person.id to index }.toMap()
    val routeOrder = session.selectedOrder.ifEmpty { session.route?.order.orEmpty() }
    val orderedPeople = (routeOrder.mapNotNull(peopleById::get) + session.passengers.filter { it.id !in routeOrder })
    val route = session.route

    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${session.passengers.size}/4", color = Color(0xFF535E78), fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = .2.sp)
                }
                Badge(
                    label = when {
                        route != null -> "AI 최적 경로"
                        state.autoPlanning -> "AI 계산 중"
                        state.autoPlanError != null -> "AI 오류"
                        else -> "AI 대기"
                    },
                    color = if (route != null || state.autoPlanning) Mint else Muted,
                )
            }

            route?.let {
                val minutes = (it.durationMs + 30_000) / 60_000
                val kilometers = it.distanceM?.let { meters -> String.format(Locale.US, "%.1fkm", meters / 1000.0) }.orEmpty()
                Row(
                    Modifier.fillMaxWidth().background(AccentSoft, RoundedCornerShape(14.dp)).padding(horizontal = 13.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("${minutes}분", color = Color(0xFF36457F), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Box(Modifier.size(4.dp).background(Color(0xFF8995D7), CircleShape))
                    Text(kilometers, color = Color(0xFF616B85), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            LazyColumn(
                Modifier.widthIn(max = 480.dp).fillMaxWidth().weight(1f).align(Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                itemsIndexed(orderedPeople, key = { _, person -> person.id }) { index, passenger ->
                    val friendIndex = registrationIndex[passenger.id] ?: 0
                    val canReorder = state.simulatedParked && !state.busy && session.state == "PLANNED"
                    FriendStopRow(
                        passenger = passenger,
                        friendLabel = friendLabel(friendIndex),
                        priority = session.priorityPassengerId == passenger.id,
                        canPin = state.simulatedParked && !state.busy && session.state in setOf("COLLECTING", "PLANNED"),
                        canMoveUp = canReorder && index > 0 && (session.priorityPassengerId == null || index > 1),
                        canMoveDown = canReorder && index < orderedPeople.lastIndex && passenger.id != session.priorityPassengerId,
                        showReorder = session.state == "PLANNED",
                        onPin = { model.setPriority(if (session.priorityPassengerId == passenger.id) null else passenger.id) },
                        onMoveUp = { model.movePassenger(passenger.id, -1) },
                        onMoveDown = { model.movePassenger(passenger.id, 1) },
                    )
                }
                if (session.state == "ACTIVE") {
                    item { ActiveCard(session, state, model, onFinish) }
                }
            }

            if (session.state in setOf("COLLECTING", "PLANNED")) {
                CompactInviteQr(session, joinBaseUrl, inviteToken)
            }
            if (session.state == "PLANNED" && route != null) {
                Button(
                    onClick = { if (BuildConfig.PLEOS_NATIVE_NAVIGATION) confirmPleosRoute = true else model.startRoute() },
                    enabled = state.simulatedParked && !state.busy,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text("안내 시작", fontWeight = FontWeight.Bold)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("정차", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Switch(checked = state.simulatedParked, onCheckedChange = { model.toggleParked() }, enabled = !state.busy)
            }
        }
    }
}

@Composable
private fun FriendStopRow(
    passenger: Passenger,
    friendLabel: String,
    priority: Boolean,
    canPin: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    showReorder: Boolean,
    onPin: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(if (priority) AccentSoft else Color(0xFFF7F8FC))
            .border(1.dp, if (priority) Color(0xFFD8DDFB) else Border, RoundedCornerShape(17.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        val emoji = passengerEmoji(passenger.displayName)
        Box(Modifier.size(44.dp).background(if (priority) Mint else Color(0xFFE7EAFF), CircleShape), contentAlignment = Alignment.Center) {
            Text(emoji ?: friendLabel.takeLast(1), color = if (emoji != null || priority) Color.White else Mint, fontWeight = FontWeight.Bold, fontSize = if (emoji != null) 23.sp else 18.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(passengerName(passenger.displayName), color = Color(0xFF252B3E), fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(passenger.point.label, color = Muted, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (showReorder) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(if (canMoveUp) Color.White else Color.Transparent)
                        .border(1.dp, if (canMoveUp) Border else Color.Transparent, CircleShape)
                        .semantics { contentDescription = "위로 이동" },
                ) {
                    Text("↑", color = if (canMoveUp) Mint else Muted.copy(alpha = .55f), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(if (canMoveDown) Color.White else Color.Transparent)
                        .border(1.dp, if (canMoveDown) Border else Color.Transparent, CircleShape)
                        .semantics { contentDescription = "아래로 이동" },
                ) {
                    Text("↓", color = if (canMoveDown) Mint else Muted.copy(alpha = .55f), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        IconButton(
            onClick = onPin,
            enabled = canPin,
            modifier = Modifier.size(40.dp).background(if (priority) Color(0xFFE3E7FF) else Color.Transparent, RoundedCornerShape(13.dp))
                .semantics { contentDescription = if (priority) "우선 하차 고정 해제" else "먼저 하차하도록 고정" },
        ) {
            Text("📌", fontSize = 17.sp)
        }
    }
}

@Composable
private fun CompactInviteQr(session: DriverSession, joinBaseUrl: String, inviteToken: String) {
    val url = remember(joinBaseUrl, session.sessionId, inviteToken) {
        if (joinBaseUrl.isBlank() || inviteToken.isBlank()) "" else "${joinBaseUrl.trimEnd('/')}/join/${session.sessionId}?invite=$inviteToken"
    }
    val image = remember(url) { url.takeIf(String::isNotBlank)?.let(::renderQr) }
    Box(
        Modifier.fillMaxWidth().background(PanelRaised, RoundedCornerShape(18.dp)).padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(
                image.asImageBitmap(),
                contentDescription = "친구 초대 QR",
                modifier = Modifier.size(220.dp).clip(RoundedCornerShape(16.dp)).background(Color.White)
                    .border(1.dp, Border, RoundedCornerShape(16.dp)).padding(10.dp),
            )
        } else {
            Box(Modifier.size(140.dp).background(Color.White, RoundedCornerShape(10.dp)))
        }
    }
}

private fun friendLabel(index: Int): String = "친구 ${(index.coerceIn(0, 25) + 'A'.code).toChar()}"

private val passengerAvatarEmojis = setOf("🚗", "🚕", "🚙", "🚌", "🚲", "🐱", "🐶", "🐼", "🐰", "🦊", "🌸", "⭐")

private fun passengerEmoji(displayName: String): String? = passengerAvatarEmojis.firstOrNull { displayName.startsWith("$it ") }

private fun passengerName(displayName: String): String =
    passengerEmoji(displayName)?.let { displayName.removePrefix("$it ").trim() } ?: displayName

@Composable
private fun SessionDetailsColumn(
    session: DriverSession,
    state: DriverUiState,
    model: MoagilViewModel,
    joinBaseUrl: String,
    inviteToken: String,
    onDelete: (Passenger) -> Unit,
    onFinish: () -> Unit,
    terminal: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        sessionDetailItems(session, state, model, joinBaseUrl, inviteToken, onDelete, onFinish, terminal)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.sessionDetailItems(
    session: DriverSession,
    state: DriverUiState,
    model: MoagilViewModel,
    joinBaseUrl: String,
    inviteToken: String,
    onDelete: (Passenger) -> Unit,
    onFinish: () -> Unit,
    terminal: Boolean,
) {
    item { InviteCard(session, joinBaseUrl, inviteToken) }
    item { PassengerCard(session, state, model, onDelete, Modifier.fillMaxWidth()) }
    if (session.route != null && session.state in setOf("PLANNED", "ACTIVE")) {
        item { OrderCard(session, state, model) }
    } else if (session.passengers.isNotEmpty() && session.state in setOf("COLLECTING", "PLANNED")) {
        item { RouteCalculationCard(state, model) }
    }
    item { SafetyCard(state, model) }
    if (session.state in setOf("COLLECTING", "PLANNED")) item { RouteSettingsCard(session, state, model) }
    if (session.state == "ACTIVE") item { ActiveCard(session, state, model, onFinish) }
    if (terminal) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (session.state == "COMPLETED") "이동 완료" else "이동 종료", fontWeight = FontWeight.Bold, fontSize = 21.sp)
                    Text("참가 링크가 닫혔습니다. 새 시연은 별도 이동으로 시작합니다.", color = Muted)
                    Button(onClick = { model.createSession(false) }, enabled = !state.busy) { Text("새 이동 만들기") }
                }
            }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("세션 ${session.sessionId.take(8)} · 버전 ${session.version} · 만료 ${formatTime(session.expiresAt)}", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!terminal && session.state != "ACTIVE") OutlinedButton(onClick = { model.setCancelConfirmation(true) }, enabled = state.simulatedParked && !state.busy) { Text("이동 취소") }
        }
    }
}

@Composable
private fun RouteCalculationCard(state: DriverUiState, model: MoagilViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (state.autoPlanning) "추천 하차 순서를 계산하고 있어요" else "추천 순서 계산 대기", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(state.autoPlanError ?: "위치가 등록될 때마다 가능한 순서를 자동으로 비교합니다.", color = Muted, fontSize = 13.sp)
            if (!state.autoPlanning) OutlinedButton(onClick = model::recalculate) { Text("다시 계산") }
        }
    }
}

@Composable
private fun RealRoadMapCard(session: DriverSession, apiBaseUrl: String, driverToken: String, modifier: Modifier = Modifier) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (BuildConfig.NAVER_MAP_CLIENT_ID.isBlank()) {
                OsmStreetMapView(session, apiBaseUrl, driverToken, Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)))
            } else {
                NaverRoadMapView(session, Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)))
            }
            Text(
                if (BuildConfig.NAVER_MAP_CLIENT_ID.isBlank()) "© OpenStreetMap" else "© Naver Maps",
                color = Muted,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun NaverRoadMapView(session: DriverSession, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var naverMap by remember { mutableStateOf<NaverMap?>(null) }
    val markers = remember { mutableStateListOf<Marker>() }
    var routeLine by remember { mutableStateOf<PolylineOverlay?>(null) }

    AndroidView(
        factory = { viewContext ->
            MapView(viewContext).also { view ->
                mapView = view
                view.onCreate(null)
                view.getMapAsync { map -> naverMap = map }
            }
        },
        modifier = modifier,
    )

    DisposableEffect(lifecycleOwner, mapView) {
        val view = mapView
        var destroyed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> view?.onStart()
                Lifecycle.Event.ON_RESUME -> view?.onResume()
                Lifecycle.Event.ON_PAUSE -> view?.onPause()
                Lifecycle.Event.ON_STOP -> view?.onStop()
                Lifecycle.Event.ON_DESTROY -> if (!destroyed) { view?.onDestroy(); destroyed = true }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (!destroyed) {
                view?.onPause()
                view?.onStop()
                view?.onDestroy()
            }
        }
    }

    LaunchedEffect(naverMap, session.sessionId, session.version) {
        val map = naverMap ?: return@LaunchedEffect
        markers.forEach { it.map = null }
        markers.clear()
        routeLine?.map = null
        routeLine = null

        val bounds = LatLngBounds.Builder()
        val boundPoints = mutableListOf<LatLng>()
        fun addMarker(point: Point, caption: String, color: Int) {
            val position = LatLng(point.lat, point.lon)
            markers += Marker().apply {
                this.position = position
                captionText = caption
                iconTintColor = color
                this.map = map
            }
            boundPoints += position
        }

        addMarker(session.startPoint, "출발", AndroidColor.rgb(82, 91, 116))
        session.passengers.forEachIndexed { index, passenger ->
            val color = if (session.priorityPassengerId == passenger.id) AndroidColor.rgb(239, 151, 75) else AndroidColor.rgb(101, 121, 238)
            addMarker(passenger.point, passengerEmoji(passenger.displayName) ?: friendLabel(index).takeLast(1), color)
        }
        session.finalPoint?.let { addMarker(it, "도착", AndroidColor.rgb(82, 91, 116)) }

        val geometry = session.route?.geometry.orEmpty()
        if (geometry.size > 1) {
            val coordinates = geometry.map { LatLng(it.lat, it.lon) }
            routeLine = PolylineOverlay().apply {
                coords = coordinates
                color = AndroidColor.rgb(101, 121, 238)
                width = 12
                this.map = map
            }
            boundPoints += coordinates
        }
        boundPoints.forEach(bounds::include)
        if (boundPoints.size > 1) {
            map.moveCamera(CameraUpdate.fitBounds(bounds.build(), 48))
        } else {
            map.moveCamera(CameraUpdate.scrollTo(LatLng(session.startPoint.lat, session.startPoint.lon)))
        }
    }
}

@Composable
private fun OsmStreetMapView(session: DriverSession, apiBaseUrl: String, driverToken: String, modifier: Modifier = Modifier) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val markers = remember(session.sessionId, session.version) {
        buildList {
            add(MapMarker(session.startPoint, "S", isStart = true))
            session.passengers.forEachIndexed { index, passenger ->
                val emoji = passengerEmoji(passenger.displayName)
                add(MapMarker(passenger.point, emoji ?: friendLabel(index).takeLast(1), isPriority = session.priorityPassengerId == passenger.id, isEmoji = emoji != null))
            }
            session.finalPoint?.let { add(MapMarker(it, "F", isFinal = true)) }
        }
    }
    val routePoints = remember(session.sessionId, session.version) { session.route?.geometry.orEmpty() }
    val allPoints = remember(markers, routePoints) { markers.map(MapMarker::point) + routePoints }
    var camera by remember(session.sessionId) { mutableStateOf<MapCamera?>(null) }
    var manuallyMoved by remember(session.sessionId) { mutableStateOf(false) }
    BoxWithConstraints(modifier.background(Color(0xFFEEF1F8)), contentAlignment = Alignment.Center) {
        val widthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val heightPx = with(density) { maxHeight.roundToPx().coerceAtLeast(1) }
        val fitCamera = remember(allPoints, widthPx, heightPx) {
            val zoom = fittingZoom(allPoints, widthPx, heightPx)
            val worldPoints = allPoints.map { worldPoint(it, zoom) }
            MapCamera(
                centerX = (worldPoints.minOfOrNull(WorldPoint::x) ?: 0.0) / 2.0 + (worldPoints.maxOfOrNull(WorldPoint::x) ?: 0.0) / 2.0,
                centerY = (worldPoints.minOfOrNull(WorldPoint::y) ?: 0.0) / 2.0 + (worldPoints.maxOfOrNull(WorldPoint::y) ?: 0.0) / 2.0,
                zoom = zoom,
            )
        }
        LaunchedEffect(fitCamera) {
            if (!manuallyMoved) camera = fitCamera
        }
        val visibleCamera = camera ?: fitCamera
        val zoom = visibleCamera.zoom
        val centerX = visibleCamera.centerX
        val centerY = visibleCamera.centerY
        val originX = centerX - widthPx / 2.0
        val originY = centerY - heightPx / 2.0
        val latestCamera = androidx.compose.runtime.rememberUpdatedState(visibleCamera)
        val tileRequests = remember(zoom, widthPx, heightPx, centerX, centerY) {
            mapTileRequests(zoom, originX, originY, widthPx, heightPx)
        }
        val loadedTiles by produceState<List<OsmTile>?>(null, tileRequests, apiBaseUrl, session.sessionId, driverToken) {
            value = null
            val firstPass = OsmTileLoader.load(tileRequests, apiBaseUrl, session.sessionId, driverToken)
            value = firstPass
            val loadedPositions = firstPass.mapTo(mutableSetOf()) { it.left to it.top }
            val missingRequests = tileRequests.filter { (it.left to it.top) !in loadedPositions }
            if (missingRequests.isNotEmpty()) {
                delay(250)
                val retriedTiles = OsmTileLoader.load(missingRequests, apiBaseUrl, session.sessionId, driverToken)
                value = (firstPass + retriedTiles).distinctBy { it.left to it.top }
            }
        }
        Canvas(
            Modifier.fillMaxSize().pointerInput(session.sessionId, widthPx, heightPx) {
                var accumulatedZoom = 1f
                detectTransformGestures { centroid, pan, zoomChange, _ ->
                    accumulatedZoom *= zoomChange
                    var zoomSteps = 0
                    while (accumulatedZoom >= 1.35f && latestCamera.value.zoom + zoomSteps < 18) {
                        accumulatedZoom /= 1.35f
                        zoomSteps++
                    }
                    while (accumulatedZoom <= 0.74f && latestCamera.value.zoom + zoomSteps > 6) {
                        accumulatedZoom /= 0.74f
                        zoomSteps--
                    }
                    if (latestCamera.value.zoom + zoomSteps !in 6..18) accumulatedZoom = 1f
                    if (pan != Offset.Zero || zoomSteps != 0) {
                        manuallyMoved = true
                        camera = transformMapCamera(latestCamera.value, pan, zoomSteps, centroid, size.width.toFloat(), size.height.toFloat())
                    }
                }
            },
        ) {
            loadedTiles.orEmpty().forEach { tile ->
                drawImage(tile.image, dstOffset = IntOffset(tile.left, tile.top), dstSize = IntSize(256, 256))
            }
            if (routePoints.size > 1) {
                val route = routePoints.map { worldPoint(it, zoom) }
                val step = (route.size / 400).coerceAtLeast(1)
                val simplified = route.filterIndexed { index, _ -> index % step == 0 || index == route.lastIndex }
                simplified.zipWithNext().forEach { (a, b) ->
                    drawLine(Mint, Offset((a.x - originX).toFloat(), (a.y - originY).toFloat()), Offset((b.x - originX).toFloat(), (b.y - originY).toFloat()), strokeWidth = 7.dp.toPx())
                }
            }
            markers.forEach { marker ->
                val point = worldPoint(marker.point, zoom)
                val center = Offset((point.x - originX).toFloat(), (point.y - originY).toFloat())
                val fill = when {
                    marker.isStart || marker.isFinal -> Color(0xFF525B74)
                    marker.isPriority -> Color(0xFFEF974B)
                    else -> Mint
                }
                val radius = when {
                    marker.isStart || marker.isFinal -> 12.dp.toPx()
                    marker.isEmoji -> 16.dp.toPx()
                    else -> 11.dp.toPx()
                }
                drawCircle(fill, radius = radius, center = center)
                drawCircle(Color.White, radius = radius, center = center, style = Stroke(width = 2.dp.toPx()))
                drawContext.canvas.nativeCanvas.drawText(
                    marker.label,
                    center.x,
                    center.y + 4.dp.toPx(),
                    android.graphics.Paint().apply {
                        color = AndroidColor.WHITE
                    textSize = if (marker.isEmoji) 17.sp.toPx() else 10.sp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                        isAntiAlias = true
                    },
                )
            }
        }
        when {
            loadedTiles == null -> CircularProgressIndicator(color = Mint)
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            color = Color.White.copy(alpha = .94f),
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 4.dp,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = {
                        manuallyMoved = true
                        camera = transformMapCamera(visibleCamera, Offset.Zero, 1, Offset(widthPx / 2f, heightPx / 2f), widthPx.toFloat(), heightPx.toFloat())
                    },
                    modifier = Modifier.size(40.dp),
                ) { Text("+", fontSize = 21.sp, fontWeight = FontWeight.Bold) }
                Divider(Modifier.width(24.dp))
                IconButton(
                    onClick = {
                        manuallyMoved = true
                        camera = transformMapCamera(visibleCamera, Offset.Zero, -1, Offset(widthPx / 2f, heightPx / 2f), widthPx.toFloat(), heightPx.toFloat())
                    },
                    modifier = Modifier.size(40.dp),
                ) { Text("−", fontSize = 21.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

private data class MapMarker(
    val point: Point,
    val label: String,
    val isStart: Boolean = false,
    val isPriority: Boolean = false,
    val isFinal: Boolean = false,
    val isEmoji: Boolean = false,
)
private data class WorldPoint(val x: Double, val y: Double)
private data class MapCamera(val centerX: Double, val centerY: Double, val zoom: Int)
private data class TileRequest(val zoom: Int, val x: Int, val y: Int, val left: Int, val top: Int)
private data class OsmTile(val left: Int, val top: Int, val image: androidx.compose.ui.graphics.ImageBitmap)

private fun transformMapCamera(
    camera: MapCamera,
    pan: Offset,
    zoomSteps: Int,
    focalPoint: Offset,
    width: Float,
    height: Float,
): MapCamera {
    val pannedX = camera.centerX - pan.x
    val pannedY = camera.centerY - pan.y
    val zoom = (camera.zoom + zoomSteps).coerceIn(6, 18)
    val scale = 2.0.pow((zoom - camera.zoom).toDouble())
    val focalX = focalPoint.x - width / 2.0
    val focalY = focalPoint.y - height / 2.0
    return MapCamera(
        centerX = (pannedX + focalX) * scale - focalX,
        centerY = (pannedY + focalY) * scale - focalY,
        zoom = zoom,
    )
}

private fun worldPoint(point: Point, zoom: Int): WorldPoint {
    val scale = 256.0 * (1 shl zoom)
    val latitude = point.lat.coerceIn(-85.0511, 85.0511) * PI / 180.0
    val x = (point.lon + 180.0) / 360.0 * scale
    val y = (0.5 - ln((1.0 + sin(latitude)) / (1.0 - sin(latitude))) / (4.0 * PI)) * scale
    return WorldPoint(x, y)
}

private fun fittingZoom(points: List<Point>, width: Int, height: Int): Int {
    if (points.size < 2) return 12
    for (zoom in 16 downTo 6) {
        val projected = points.map { worldPoint(it, zoom) }
        val spanX = (projected.maxOf(WorldPoint::x) - projected.minOf(WorldPoint::x)) * .5
        val spanY = (projected.maxOf(WorldPoint::y) - projected.minOf(WorldPoint::y)) * .5
        if (spanX <= width * .38 && spanY <= height * .38) return zoom
    }
    return 6
}

private fun mapTileRequests(zoom: Int, originX: Double, originY: Double, width: Int, height: Int): List<TileRequest> {
    val tileCount = 1 shl zoom
    val firstX = floor(originX / 256.0).toInt()
    val firstY = floor(originY / 256.0).toInt()
    val lastX = floor((originX + width) / 256.0).toInt()
    val lastY = floor((originY + height) / 256.0).toInt()
    return buildList {
        for (tileY in firstY..lastY) {
            if (tileY !in 0 until tileCount) continue
            for (tileX in firstX..lastX) {
                val wrappedX = ((tileX % tileCount) + tileCount) % tileCount
                add(TileRequest(zoom, wrappedX, tileY, (tileX * 256 - originX).toInt(), (tileY * 256 - originY).toInt()))
            }
        }
    }
}

private object OsmTileLoader {
    private val client = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).build()
    private val cache = ConcurrentHashMap<String, androidx.compose.ui.graphics.ImageBitmap>()

    suspend fun load(requests: List<TileRequest>, apiBaseUrl: String, sessionId: String, driverToken: String): List<OsmTile> = coroutineScope {
        requests.chunked(4).flatMap { batch ->
            batch.map { request -> async(Dispatchers.IO) { loadOne(request, apiBaseUrl, sessionId, driverToken) } }.awaitAll().filterNotNull()
        }
    }

    private fun loadOne(request: TileRequest, apiBaseUrl: String, sessionId: String, driverToken: String): OsmTile? = runCatching {
        val key = "${request.zoom}/${request.x}/${request.y}"
        val image = cache[key] ?: run {
            val httpRequest = Request.Builder()
                .url("${apiBaseUrl.trimEnd('/')}/api/sessions/$sessionId/map-tiles/${request.zoom}/${request.x}/${request.y}.png")
                .header("Authorization", "Bearer $driverToken")
                .build()
            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
                bitmap.asImageBitmap().also { cache[key] = it }
            }
        }
        OsmTile(request.left, request.top, image)
    }.getOrNull()
}

@Composable
private fun SafetyCard(state: DriverUiState, model: MoagilViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = if (state.simulatedParked) Color(0xFFEAF6F1) else PanelRaised), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (state.simulatedParked) "정차(시연) 상태" else "정차 상태 확인 필요", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (state.simulatedParked) Mint else Color(0xFF98631D))
                Text("모의 토글 · 차량 센서 판정이 아닙니다", color = Muted, fontSize = 12.sp)
            }
            Text("운전자 입력", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            Switch(checked = state.simulatedParked, onCheckedChange = { model.toggleParked() })
        }
    }
}

@Composable
private fun InviteCard(session: DriverSession, joinBaseUrl: String, inviteToken: String, modifier: Modifier = Modifier) {
    val url = remember(joinBaseUrl, session.sessionId, inviteToken) {
        if (joinBaseUrl.isBlank() || inviteToken.isBlank()) "" else "${joinBaseUrl.trimEnd('/')}/join/${session.sessionId}?invite=$inviteToken"
    }
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (url.isNotBlank()) {
                val image = remember(url) { renderQr(url) }
                image?.let { Image(it.asImageBitmap(), contentDescription = "동승자 모바일 초대 QR", modifier = Modifier.size(148.dp).background(Color.White, RoundedCornerShape(12.dp)).padding(8.dp)) }
            } else {
                Box(Modifier.size(148.dp).background(PanelRaised, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Text("QR 준비 중", color = Muted) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                SectionTitle("동승자 초대", "스마트폰 카메라로 스캔")
                Text("${session.passengers.size} / 4명", color = Mint, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text("초대 QR은 이 이동 참가 권한입니다. 세션 종료 후 사용할 수 없습니다.", color = Muted, fontSize = 12.sp)
                Text("${session.startPoint.label}에서 시작", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PassengerCard(session: DriverSession, state: DriverUiState, model: MoagilViewModel, onDelete: (Passenger) -> Unit, modifier: Modifier = Modifier) {
    val canRemove = state.simulatedParked && session.state in setOf("COLLECTING", "PLANNED")
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("동승자", "등록된 위치만 차량 화면에 표시")
            if (session.passengers.isEmpty()) Text("아직 참가자가 없습니다. QR에서 이름과 핀을 등록해 주세요.", color = Muted, modifier = Modifier.padding(vertical = 8.dp))
            session.passengers.forEachIndexed { index, passenger ->
                Row(Modifier.fillMaxWidth().background(PanelRaised, RoundedCornerShape(12.dp)).padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp).background(Color(0xFF315064), CircleShape), contentAlignment = Alignment.Center) {
                        Text("${index + 1}", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(passengerName(passenger.displayName), fontWeight = FontWeight.SemiBold)
                        Text(passenger.point.label, color = Muted, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (canRemove) TextButton(onClick = { onDelete(passenger) }, enabled = !state.busy) { Text("삭제", color = Alert) }
                }
            }
        }
    }
}

@Composable
private fun RouteSettingsCard(session: DriverSession, state: DriverUiState, model: MoagilViewModel) {
    val enabled = state.simulatedParked && !state.busy && session.state in setOf("COLLECTING", "PLANNED")
    var expanded by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionTitle("경로 설정", "선택 사항 · 현재 위치 등록만으로 자동 계산")
                }
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "접기" else "열기") }
            }
            if (expanded) {
                Text("출발지 · ${session.startPoint.label} · ${"%.4f".format(Locale.US, session.startPoint.lat)}, ${"%.4f".format(Locale.US, session.startPoint.lon)}", color = Muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("운전자 최종 목적지", fontWeight = FontWeight.SemiBold)
                        Text("미선택이면 마지막 승객 하차 후 종료", color = Muted, fontSize = 12.sp)
                    }
                    Switch(checked = state.finalEnabled, onCheckedChange = { model.setFinalDestination(it) }, enabled = enabled)
                }
                if (state.finalEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(state.finalLat, model::setFinalLat, Modifier.weight(1f), label = { Text("위도") }, singleLine = true)
                        OutlinedTextField(state.finalLon, model::setFinalLon, Modifier.weight(1f), label = { Text("경도") }, singleLine = true)
                        Button(onClick = { model.setFinalDestination(true) }, enabled = enabled, modifier = Modifier.align(Alignment.CenterVertically)) { Text("저장") }
                    }
                    Text("좌표로 지정하는 시연용 목적지입니다.", color = Muted, fontSize = 12.sp)
                }
                PriorityPicker(session, state, model, enabled)
                if (!state.simulatedParked) Text("선택 사항 변경은 정차 확인 후 할 수 있습니다.", color = Color(0xFF98631D), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PriorityPicker(session: DriverSession, state: DriverUiState, model: MoagilViewModel, enabled: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val selected = session.passengers.firstOrNull { it.id == state.priorityId }?.displayName?.let(::passengerName) ?: "지정 안 함"
    Column {
        Text("먼저 하차할 승객", color = Muted, fontSize = 12.sp)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled) { Text(selected) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("지정 안 함") }, onClick = { expanded = false; if (state.priorityId != null) model.setPriority(null) })
                session.passengers.forEach { passenger ->
                    DropdownMenuItem(text = { Text(passengerName(passenger.displayName)) }, onClick = { expanded = false; if (passenger.id != state.priorityId) model.setPriority(passenger.id) })
                }
            }
        }
    }
}

@Composable
private fun OrderCard(session: DriverSession, state: DriverUiState, model: MoagilViewModel) {
    val route = session.route ?: return
    var confirmPleosRoute by remember { mutableStateOf(false) }
    val active = session.state == "ACTIVE"
    val canEdit = state.simulatedParked && !state.busy && !active
    val names = session.passengers.associateBy(Passenger::id)
    if (confirmPleosRoute) {
        AlertDialog(
            onDismissRequest = { confirmPleosRoute = false },
            title = { Text("Pleos 내비 목적지를 변경할까요?") },
            text = { Text("최적화된 첫 하차 지점을 차량 내비에 전송합니다. 이미 안내 중인 경로가 있다면 바뀔 수 있습니다. 다음 목적지는 하차 완료를 누를 때마다 전송됩니다.") },
            confirmButton = { TextButton(onClick = { confirmPleosRoute = false; model.startRoute() }) { Text("Pleos 내비로 전송") } },
            dismissButton = { TextButton(onClick = { confirmPleosRoute = false }) { Text("취소") } },
        )
    }
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SectionTitle("선택한 하차 순서", "${route.evaluatedCount}개 비교 · 추천/수동 비용 동일 provider")
            Text("${route.note} · ${formatDuration(route.durationMs)} · ${route.distanceM?.let { "${it}m" } ?: "거리 미제공"}", color = Mint, fontWeight = FontWeight.SemiBold)
            session.selectedOrder.forEachIndexed { index, id ->
                val passenger = names[id]
                Row(Modifier.fillMaxWidth().background(PanelRaised, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", color = Mint, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp))
                    Column(Modifier.weight(1f)) {
                        Text(passenger?.displayName?.let(::passengerName) ?: "동승자", fontWeight = FontWeight.SemiBold)
                        Text(passenger?.point?.label.orEmpty(), color = Muted, fontSize = 12.sp)
                    }
                    val fixed = session.priorityPassengerId == id
                    if (fixed) Text("우선 고정", color = Mint, fontSize = 11.sp)
                    TextButton(onClick = { model.movePassenger(id, -1) }, enabled = canEdit && index > 0 && !fixed) { Text("위") }
                    TextButton(onClick = { model.movePassenger(id, 1) }, enabled = canEdit && index < session.selectedOrder.lastIndex && !fixed) { Text("아래") }
                }
            }
            Text("조회 ${route.fetchedAt} · 출처 ${route.dataSource} · 승객별 ETA는 제공하지 않음", color = Muted, fontSize = 11.sp)
            if (session.state == "PLANNED") {
                Button(
                    onClick = { if (BuildConfig.PLEOS_NATIVE_NAVIGATION) confirmPleosRoute = true else model.startRoute() },
                    enabled = canEdit,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(if (BuildConfig.PLEOS_NATIVE_NAVIGATION) "최적 순서 확정 · Pleos 내비 전송" else "경로 확정 · 모의 안내", fontWeight = FontWeight.Bold)
                }
                if (!state.simulatedParked) Text("정차 확인 후 내비로 전송할 수 있습니다.", color = Color(0xFF98631D), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ActiveCard(session: DriverSession, state: DriverUiState, model: MoagilViewModel, onFinish: () -> Unit) {
    val passenger = session.selectedOrder.getOrNull(session.activeIndex)?.let { id -> session.passengers.firstOrNull { it.id == id } }
    val finalLeg = passenger == null && session.finalPoint != null
    val readyToFinish = passenger == null && session.finalPoint == null
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF6F1)), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (BuildConfig.PLEOS_NATIVE_NAVIGATION) "Pleos NaviHelper 내비 안내" else "모의 내비 안내 · 실제 차량 내비 연결 없음", color = Mint, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(state.navigationMessage.ifBlank { "앱이 다시 연결됐습니다. 현재 목적지를 운전자가 확인해 주세요." }, color = Color(0xFF42564D))
            Divider(color = Color(0xFFD2E8DE))
            Text("${session.selectedOrder.size - session.activeIndex}명 남음", color = Muted, fontSize = 12.sp)
            Text(
                when {
                    passenger != null -> "다음 하차 · ${passengerName(passenger.displayName)}"
                    finalLeg -> "운전자 최종 목적지 · ${session.finalPoint?.label}"
                    else -> "승객 하차 완료"
                },
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                (passenger?.point ?: session.finalPoint)?.let { "${it.label} · ${it.lat}, ${it.lon}" } ?: "정차 확인 후 이동을 마무리할 수 있습니다.",
                color = Muted,
            )
            if (!state.navigationConfirmed) {
                Button(onClick = { model.retryNavigation() }, enabled = state.simulatedParked && !state.busy, modifier = Modifier.fillMaxWidth()) { Text(if (BuildConfig.PLEOS_NATIVE_NAVIGATION) "Pleos 내비 경로 다시 요청" else "모의 안내 다시 요청") }
            } else if (!BuildConfig.PLEOS_NATIVE_NAVIGATION) {
                OutlinedButton(onClick = { model.simulateNavigationFailure() }, enabled = state.simulatedParked && !state.busy, modifier = Modifier.fillMaxWidth()) { Text("안내 실패·재시도 시연") }
            }
            when {
                passenger != null -> Button(onClick = { model.completeCurrentStop() }, enabled = state.simulatedParked && state.navigationConfirmed && !state.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(if (BuildConfig.PLEOS_NATIVE_NAVIGATION) "정차 확인 · 다음 내비 목적지 전송" else "정차 후 하차 완료 · 시연") }
                finalLeg -> Button(onClick = onFinish, enabled = state.simulatedParked && state.navigationConfirmed && !state.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("최종 목적지 도착 · 이동 종료") }
                readyToFinish -> Button(onClick = onFinish, enabled = state.simulatedParked && state.navigationConfirmed && !state.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("이동 종료 · 시연") }
            }
            TextButton(onClick = { model.setCancelConfirmation(true) }, enabled = state.simulatedParked && !state.busy) { Text("이동 취소", color = Alert) }
            if (!state.simulatedParked) Text("정차(시연) 확인 전에는 하차 완료와 종료를 잠급니다.", color = Color(0xFF98631D), fontSize = 12.sp)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(subtitle, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun Badge(label: String, color: Color) {
    Row(Modifier.background(color.copy(alpha = .12f), RoundedCornerShape(40.dp)).padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(7.dp))
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SafetyNote(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = PanelRaised), shape = RoundedCornerShape(14.dp)) {
        Text(text, Modifier.fillMaxWidth().padding(15.dp), color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun MessageBanner(text: String, color: Color, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(color.copy(alpha = .12f)).padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), color = color, fontSize = 13.sp)
        TextButton(onClick = onDismiss) { Text("닫기", color = color) }
    }
}

@Composable
private fun Footer() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("MOAGIL · ${BuildConfig.FLAVOR}${BuildConfig.BUILD_TYPE.replaceFirstChar(Char::uppercase)}", color = Muted, fontSize = 11.sp)
        Text("차량 센서·정차 상태는 시연값 · 실제 주행 금지", color = Muted, fontSize = 11.sp)
    }
}

private fun renderQr(value: String): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 400, 400, mapOf(EncodeHintType.MARGIN to 1))
    Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
}.getOrNull()

private fun formatDuration(milliseconds: Long): String {
    val minutes = milliseconds / 60_000
    val seconds = (milliseconds % 60_000) / 1_000
    return "약 ${minutes}분 ${seconds}초 · mock"
}

private fun formatTime(iso: String): String = runCatching {
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    val date: Date = parser.parse(iso.take(20)) ?: return@runCatching "정보 없음"
    SimpleDateFormat("HH:mm", Locale.KOREA).format(date)
}.getOrDefault("정보 없음")
