package kr.moagil.vehicle

import android.graphics.Bitmap
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.key
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kr.moagil.vehicle.data.DriverSession
import kr.moagil.vehicle.data.Passenger
import kr.moagil.vehicle.data.Point
import kr.moagil.vehicle.ui.DriverUiState
import kr.moagil.vehicle.ui.KakaoVehicleMap
import kr.moagil.vehicle.ui.MoagilViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

private val Ink = Color(0xFFF4F5F9)
private val Panel = Color.White
private val PanelRaised = Color(0xFFF3F5FA)
private val Mint = Color(0xFF5D70E6)
private val AccentSoft = Color(0xFFEEF0FF)
private val Border = Color(0xFFE5E8F0)
private val Muted = Color(0xFF737B8D)
private val Success = Color(0xFF2B806B)
private val Alert = Color(0xFFB95765)
private val PassengerTones = listOf(
    Color(0xFFF3C3C6),
    Color(0xFFC6D5F7),
    Color(0xFFC3E4D4),
    Color(0xFFF3E4AD),
)
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
        val previewQrPreparation = BuildConfig.DEBUG && intent.getBooleanExtra("codex_preview_qr_preparation", false)
        setContent {
            MaterialTheme(colorScheme = Scheme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (previewQrPreparation) {
                        QrFirstScreen(DriverUiState()) {}
                    } else {
                        val model: MoagilViewModel = viewModel()
                        val state by model.state.collectAsState()
                        DriverScreen(state, model)
                    }
                }
            }
        }
    }
}

@Composable
private fun DriverScreen(state: DriverUiState, model: MoagilViewModel) {
    val context = LocalContext.current
    val vehiclePermissions = remember {
        listOf(
            "pleos.car.permission.FUSED_LOCATION",
            "android.car.permission.CAR_SPEED",
            "android.car.permission.CAR_POWERTRAIN",
        )
    }
    LaunchedEffect(model) {
        if (!BuildConfig.PLEOS_NATIVE_NAVIGATION) {
            model.startVehicleContext()
        } else if (vehiclePermissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            model.startVehicleContext()
        } else {
            model.setVehiclePermissionDenied()
        }
    }
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
        QrFirstScreen(state) { model.createSession(false) }
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
private fun QrFirstScreen(state: DriverUiState, onRetry: () -> Unit) {
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color(0xFFF1F3FB)),
        contentAlignment = Alignment.Center,
    ) {
        val compact = maxHeight < 620.dp
        val logoSize = (maxHeight * 0.28f).coerceIn(132.dp, 190.dp)
        AnimatedMoagilLogo(
            Modifier.align(Alignment.Center).size(logoSize),
        )
        Column(
            Modifier.align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = maxHeight / 2 + logoSize / 2 + if (compact) 16.dp else 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("모아길", color = Color(0xFF26345F), fontSize = if (compact) 36.sp else 44.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(if (compact) 24.dp else 36.dp))
            Text("QR 준비 중", color = Color(0xFF515C79), fontSize = if (compact) 20.sp else 24.sp, fontWeight = FontWeight.SemiBold)
            if (state.errorMessage != null) {
                Spacer(Modifier.height(18.dp))
                Text(state.errorMessage, color = Alert, fontSize = 16.sp, lineHeight = 23.sp)
                if (!state.busy) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onRetry,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint),
                    ) {
                        Text("다시 연결", modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedMoagilLogo(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "moagil-logo")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 2_200, easing = LinearEasing)),
        label = "logo-path-progress",
    )
    val (x, y) = moagilLogoPosition(progress)

    BoxWithConstraints(
        modifier.clip(CircleShape).background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val artworkOffsetX = maxWidth * 0.024f
        val artworkOffsetY = -(maxHeight * 0.008f)
        Image(
            painter = painterResource(R.drawable.moagil_logo),
            contentDescription = "모아길 로고",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().offset(x = artworkOffsetX, y = artworkOffsetY),
        )
        val dotSize = maxWidth * 0.11f
        Box(
            Modifier.align(Alignment.TopStart)
                .offset(
                    x = maxWidth * x + artworkOffsetX - dotSize / 2,
                    y = maxHeight * y + artworkOffsetY - dotSize / 2,
                )
                .size(dotSize)
                .background(Color(0xFF536DE6), CircleShape)
                .border(maxWidth * 0.008f, Color.White, CircleShape),
        )
    }
}

private fun moagilLogoPosition(progress: Float): Pair<Float, Float> {
    val scaled = progress.coerceIn(0f, 1f) * 4f
    val segment = scaled.toInt().coerceAtMost(3)
    val t = (scaled - segment).coerceIn(0f, 1f)

    fun cubic(p0: Float, p1: Float, p2: Float, p3: Float): Float {
        val inverse = 1f - t
        return inverse * inverse * inverse * p0 +
            3f * inverse * inverse * t * p1 +
            3f * inverse * t * t * p2 +
            t * t * t * p3
    }

    return when (segment) {
        0 -> cubic(.147f, .18f, .23f, .293f) to cubic(.663f, .60f, .39f, .384f)
        1 -> cubic(.293f, .35f, .40f, .449f) to cubic(.384f, .384f, .50f, .509f)
        2 -> cubic(.449f, .51f, .56f, .61f) to cubic(.509f, .52f, .384f, .384f)
        else -> cubic(.61f, .68f, .73f, .795f) to cubic(.384f, .38f, .61f, .629f)
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
            Image(
                painter = painterResource(R.drawable.moagil_logo),
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(CircleShape),
            )
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
                TextButton(onClick = onNewSession, enabled = !state.busy && !state.routeUpdating) { Text("새 QR", color = Muted) }
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
            Text(
                if (BuildConfig.PLEOS_NATIVE_NAVIGATION) "동승자는 휴대폰에서 목적지를 등록하고, 차량은 현재 위치와 상태를 기준으로 내비를 연결합니다."
                else "동승자는 휴대폰 웹에서 하차 지점을 등록하고, 운전자는 시연용 정차 상태에서 순서와 안내를 확인합니다.",
                color = Muted,
                fontSize = 16.sp,
            )
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ServerCard(state, model, Modifier.weight(1f))
                    StartActions(state, model, Modifier.weight(1f))
                }
            } else {
                ServerCard(state, model, Modifier.fillMaxWidth())
                StartActions(state, model, Modifier.fillMaxWidth())
            }
            SafetyNote(
                if (BuildConfig.PLEOS_NATIVE_NAVIGATION) "차량 위치·속도·기어 정보가 확인되고 P에 정차했을 때만 운전자 조작을 허용합니다."
                else "시연용 정차 스위치로 운전자 조작을 제한합니다. 실제 차량 상태는 연결되지 않습니다."
            )
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
private fun VehicleMapWithRouteStatus(
    session: DriverSession,
    state: DriverUiState,
    javascriptKey: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.clip(RoundedCornerShape(18.dp))) {
        KakaoVehicleMap(
            session = session,
            currentLocation = state.vehicleContext.location.takeIf { state.vehicleContext.hasFreshLocation },
            javascriptKey = javascriptKey,
            mapBaseUrl = BuildConfig.KAKAO_MAP_ORIGIN,
            assetBaseUrl = BuildConfig.KAKAO_MAP_ORIGIN,
            modifier = Modifier.fillMaxSize(),
        )
        if (state.routeUpdating || state.autoPlanning) {
            Column(
                Modifier.align(Alignment.Center)
                    .background(Color.White.copy(alpha = .95f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AnimatedMoagilLogo(Modifier.size(68.dp))
                Text("최적 경로를 반영하고 있어요", color = Color(0xFF36457F), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun SessionDashboard(state: DriverUiState, model: MoagilViewModel, onDelete: (Passenger) -> Unit, onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val session = state.session ?: return
    val credentials = state.credentials
    val terminal = session.state in setOf("COMPLETED", "CANCELLED", "EXPIRED")
    if (BuildConfig.PLEOS_NATIVE_NAVIGATION) {
        val contentModifier = modifier.fillMaxSize().padding(14.dp)
        Box(contentModifier) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                VehicleMapWithRouteStatus(
                    session = session,
                    state = state,
                    javascriptKey = state.kakaoMapJavascriptKey,
                    modifier = Modifier.weight(1.7f).fillMaxHeight(),
                )
                if (terminal) {
                    SessionDetailsColumn(
                        session, state, model, credentials?.joinBaseUrl.orEmpty(), credentials?.inviteToken.orEmpty(),
                        onDelete, onFinish, true, Modifier.weight(0.8f).fillMaxHeight(),
                    )
                } else {
                    LiveStopsPanel(
                        session, state, model, credentials?.joinBaseUrl.orEmpty(), credentials?.inviteToken.orEmpty(),
                        onFinish, Modifier.weight(0.8f).fillMaxHeight(),
                    )
                }
            }
        }
    } else {
        BoxWithConstraints(modifier.fillMaxWidth()) {
            if (maxWidth > 900.dp) {
                Row(Modifier.fillMaxSize().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    VehicleMapWithRouteStatus(
                        session = session,
                        state = state,
                        javascriptKey = state.kakaoMapJavascriptKey,
                        modifier = Modifier.weight(1.7f).fillMaxHeight(),
                    )
                    if (terminal) {
                        SessionDetailsColumn(session, state, model, credentials?.joinBaseUrl.orEmpty(), credentials?.inviteToken.orEmpty(), onDelete, onFinish, true, Modifier.weight(0.8f).fillMaxHeight())
                    } else {
                        LiveStopsPanel(session, state, model, credentials?.joinBaseUrl.orEmpty(), credentials?.inviteToken.orEmpty(), onFinish, Modifier.weight(0.8f).fillMaxHeight())
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        VehicleMapWithRouteStatus(
                            session = session,
                            state = state,
                            javascriptKey = state.kakaoMapJavascriptKey,
                            modifier = Modifier.fillMaxWidth().height(360.dp),
                        )
                    }
                    sessionDetailItems(session, state, model, credentials?.joinBaseUrl.orEmpty(), credentials?.inviteToken.orEmpty(), onDelete, onFinish, terminal)
                }
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
            text = { Text("최적 순서대로 다음 구간을 Pleos 내비에 보냅니다. 동승자 창의 앱 라이브러리에서 모아길을 열면 기본 내비와 함께 사용할 수 있습니다.") },
            confirmButton = { TextButton(onClick = { confirmPleosRoute = false; model.startRoute() }) { Text("시작") } },
            dismissButton = { TextButton(onClick = { confirmPleosRoute = false }) { Text("취소") } },
        )
    }

    val peopleById = session.passengers.associateBy(Passenger::id)
    val routeOrder = session.selectedOrder.ifEmpty { session.route?.order.orEmpty() }
    val orderedPeople = (routeOrder.mapNotNull(peopleById::get) + session.passengers.filter { it.id !in routeOrder })
    val route = session.route

    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("하차 순서", color = Color(0xFF252B3E), fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${session.passengers.size} / 4", color = Muted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            val durationLabel = route?.let {
                val minutes = (it.durationMs + 30_000) / 60_000
                when {
                    minutes < 60L -> "${minutes}분"
                    minutes % 60L == 0L -> "${minutes / 60L}시간"
                    else -> "${minutes / 60L}시간 ${minutes % 60L}분"
                }
            }.takeUnless { state.routeUpdating || state.autoPlanning } ?: if (state.autoPlanning || state.routeUpdating) "계산 중" else "—"
            Row(
                Modifier.fillMaxWidth().background(AccentSoft, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("예상 소요 시간", color = Color(0xFF616B85), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(durationLabel, color = Color(0xFF36457F), fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Box(Modifier.width(1.dp).height(56.dp).background(Color(0xFFD8DDF5)))
                Column(Modifier.weight(1.15f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("도로 경로", color = Color(0xFF616B85), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    RoutePreferenceToggle(
                        selected = session.routePreference,
                        enabled = !state.busy && !state.routeUpdating && session.state in setOf("COLLECTING", "PLANNED"),
                        onSelect = model::setRoutePreference,
                    )
                }
            }
            if (route == null && (state.autoPlanning || state.autoPlanError != null || session.passengers.isNotEmpty())) {
                Text(
                    when {
                        state.autoPlanning -> "등록된 목적지를 확인하고 있어요."
                        state.autoPlanError != null -> state.autoPlanError
                        else -> "목적지가 등록되면 가장 효율적인 순서를 추천합니다."
                    },
                    color = if (state.autoPlanError != null) Alert else Muted,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                )
            }

            if (session.passengers.isNotEmpty() && session.state in setOf("COLLECTING", "PLANNED")) {
                Button(
                    onClick = model::optimizeRoute,
                    enabled = state.canOperate && !state.busy && !state.routeUpdating && !state.autoPlanning,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(if (state.routeUpdating || state.autoPlanning) "최단 시간 경로 계산 중…" else "가장 빠른 경로로 정렬", fontWeight = FontWeight.Bold)
                }
            }

            if (orderedPeople.isNotEmpty() && session.state in setOf("COLLECTING", "PLANNED")) {
                Text("위·아래 버튼으로 순서를 바꾸고, 핀으로 원하는 위치를 고정하세요.", color = Muted, fontSize = 14.sp, lineHeight = 19.sp)
            }

            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                items(orderedPeople, key = { it.id }) { passenger ->
                    val passengerIndex = session.passengers.indexOfFirst { it.id == passenger.id }.coerceAtLeast(0)
                    val stopNumber = orderedPeople.indexOf(passenger) + 1
                    val fixedPosition = session.fixedPositions[passenger.id]
                    val fixedIndices = session.fixedPositions.values.map { it - 1 }.toSet()
                    val canReorder = state.canOperate && !state.busy && session.state == "PLANNED"
                    FriendStopRow(
                        passenger = passenger,
                        stopNumber = stopNumber,
                        passengerColor = PassengerTones[passengerIndex % PassengerTones.size],
                        fixedPosition = fixedPosition,
                        canPin = !state.busy && !state.routeUpdating && session.state in setOf("COLLECTING", "PLANNED"),
                        canMoveUp = canReorder && fixedPosition == null && stopNumber > 1 && stopNumber - 2 !in fixedIndices,
                        canMoveDown = canReorder && fixedPosition == null && stopNumber < orderedPeople.size && stopNumber !in fixedIndices,
                        onPin = { model.toggleFixedPosition(passenger.id) },
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
                    enabled = state.canOperate && !state.busy && !state.routeUpdating && !state.autoPlanning,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text(if (state.canOperate) "이 순서로 안내 시작" else "정차 확인 후 안내 시작", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("차량 정차 상태", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                if (BuildConfig.PLEOS_NATIVE_NAVIGATION) {
                    Text(if (state.canOperate) state.vehicleContext.message else "차량 상태 확인 필요", color = if (state.canOperate) Success else Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Switch(checked = state.simulatedParked, onCheckedChange = { model.toggleParked() }, enabled = !state.busy)
                }
            }
        }
    }
}

@Composable
private fun FriendStopRow(
    passenger: Passenger,
    stopNumber: Int,
    passengerColor: Color,
    fixedPosition: Int?,
    canPin: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPin: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val fixed = fixedPosition != null
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(if (fixed) AccentSoft else PanelRaised)
            .border(1.dp, if (fixed) Color(0xFFD8DDFB) else Border, RoundedCornerShape(17.dp))
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            Modifier.size(48.dp)
                .background(passengerColor, CircleShape)
                    .border(if (fixed) 2.dp else 1.dp, if (fixed) Color(0xFF2D52CA) else Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$stopNumber", color = Color(0xFF34425F), fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(passenger.displayName, color = Color(0xFF252B3E), fontWeight = FontWeight.Bold, fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (fixedPosition != null) {
                    Text(
                        "${fixedPosition}번째 고정",
                        color = Color(0xFF36457F),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.background(Color.White, RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Text(passenger.point.label, color = Color(0xFF626B7E), fontSize = 19.sp, lineHeight = 26.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        IconButton(
            onClick = onPin,
            enabled = canPin,
            modifier = Modifier.size(44.dp).background(if (fixed) Color(0x1A000000) else Color.Transparent, RoundedCornerShape(15.dp))
                .semantics { contentDescription = if (fixed) "${fixedPosition}번째 고정 해제" else "현재 ${stopNumber}번째 순서 고정" },
        ) {
            Canvas(Modifier.size(25.dp)) {
                val w = size.width
                val h = size.height
                val pin = Path().apply {
                    moveTo(w * .35f, h * .12f)
                    lineTo(w * .65f, h * .12f)
                    lineTo(w * .62f, h * .34f)
                    lineTo(w * .62f, h * .43f)
                    lineTo(w * .72f, h * .52f)
                    lineTo(w * .82f, h * .62f)
                    lineTo(w * .82f, h * .69f)
                    lineTo(w * .56f, h * .69f)
                    lineTo(w * .53f, h * .93f)
                    lineTo(w * .47f, h * .93f)
                    lineTo(w * .44f, h * .69f)
                    lineTo(w * .18f, h * .69f)
                    lineTo(w * .18f, h * .62f)
                    lineTo(w * .28f, h * .52f)
                    lineTo(w * .38f, h * .43f)
                    lineTo(w * .38f, h * .34f)
                    close()
                }
                val tint = if (fixed) Color.Black else Color(0xFF778096)
                if (fixed) drawPath(pin, tint)
                else drawPath(pin, tint, style = Stroke(width = 1.8.dp.toPx()))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TextButton(onClick = onMoveUp, enabled = canMoveUp, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(width = 34.dp, height = 30.dp)) { Text("↑", fontSize = 19.sp, lineHeight = 19.sp) }
            TextButton(onClick = onMoveDown, enabled = canMoveDown, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(width = 34.dp, height = 30.dp)) { Text("↓", fontSize = 19.sp, lineHeight = 19.sp) }
        }
    }
}

@Composable
private fun RoutePreferenceToggle(selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFFDDE2F5), RoundedCornerShape(10.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf("FREE" to "무료 우선", "TIME" to "시간 우선").forEach { (value, label) ->
            val isSelected = selected == value
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Color(0xFF25283A) else Color.Transparent)
                    .clickable(enabled = enabled && !isSelected) { onSelect(value) }
                    .semantics { contentDescription = if (isSelected) "$label 경로, 선택됨" else "$label 경로 선택" }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (isSelected) Color.White else Color(0xFF59627B),
                    fontSize = 17.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CompactInviteQr(session: DriverSession, joinBaseUrl: String, inviteToken: String) {
    val url = remember(joinBaseUrl, session.sessionId, inviteToken) {
        if (joinBaseUrl.isBlank() || inviteToken.isBlank()) "" else "${joinBaseUrl.trimEnd('/')}/join/${session.sessionId}?invite=$inviteToken"
    }
    val image = remember(url) { url.takeIf(String::isNotBlank)?.let(::renderQr) }
    if (image != null) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Image(
                image.asImageBitmap(),
                contentDescription = "친구 초대 QR",
                modifier = Modifier.size(230.dp).clip(RoundedCornerShape(16.dp)).background(Color.White)
                    .border(1.dp, Border, RoundedCornerShape(16.dp)).padding(9.dp),
            )
        }
    }
}

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
    if (BuildConfig.PLEOS_NATIVE_NAVIGATION) item { PleosSplitScreenHint() }
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
            if (!terminal && session.state != "ACTIVE") OutlinedButton(onClick = { model.setCancelConfirmation(true) }, enabled = state.canOperate && !state.busy) { Text("이동 취소") }
        }
    }
}

@Composable
private fun PleosSplitScreenHint() {
    Card(colors = CardDefaults.cardColors(containerColor = AccentSoft), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Pleos 화면 배치", color = Color(0xFF36457F), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                "기본 내비를 유지하려면 동승자 창의 앱 라이브러리에서 모아길을 여세요. 앱 아이콘은 동승자 GNB 즐겨찾기에 등록할 수 있습니다.",
                color = Color(0xFF535E78),
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }
    }
}

@Composable
private fun RouteCalculationCard(state: DriverUiState, model: MoagilViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (state.autoPlanning) "추천 하차 순서를 계산하고 있어요" else "추천 순서 계산 대기", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(state.autoPlanError ?: "위치가 등록될 때마다 가능한 순서를 자동으로 비교합니다.", color = Muted, fontSize = 13.sp)
            Button(onClick = model::optimizeRoute, enabled = state.canOperate && !state.busy && !state.autoPlanning && !state.routeUpdating) {
                Text("최적 경로 설정")
            }
            if (!state.autoPlanning) OutlinedButton(onClick = model::recalculate) { Text("다시 계산") }
        }
    }
}

@Composable
private fun SafetyCard(state: DriverUiState, model: MoagilViewModel) {
    val context = LocalContext.current
    val vehiclePermissions = listOf(
        "pleos.car.permission.FUSED_LOCATION",
        "android.car.permission.CAR_SPEED",
        "android.car.permission.CAR_POWERTRAIN",
    )
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val granted = vehiclePermissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (granted) model.startVehicleContext() else model.setVehiclePermissionDenied()
    }
    Card(colors = CardDefaults.cardColors(containerColor = if (state.canOperate) Color(0xFFEAF6F1) else PanelRaised), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (BuildConfig.PLEOS_NATIVE_NAVIGATION) {
                    Text(if (state.canOperate) "P 정차 확인" else "차량 상태 확인 필요", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (state.canOperate) Mint else Color(0xFF98631D))
                    Text(state.vehicleContext.message, color = Muted, fontSize = 12.sp)
                    Text(state.vehicleContext.location?.label ?: "출발 위치 확인 중", color = Muted, fontSize = 12.sp)
                } else {
                    Text(if (state.simulatedParked) "정차(시연) 상태" else "정차 상태 확인 필요", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (state.simulatedParked) Mint else Color(0xFF98631D))
                    Text("모의 토글 · 실제 차량 정보 미연결", color = Muted, fontSize = 12.sp)
                }
            }
            if (BuildConfig.PLEOS_NATIVE_NAVIGATION) {
                if (!state.vehiclePermissionGranted) {
                    OutlinedButton(onClick = { permissionLauncher.launch(vehiclePermissions.toTypedArray()) }) { Text("차량 연결") }
                } else {
                    Text(if (state.canOperate) "조작 가능" else "조작 잠김", color = Muted, fontSize = 13.sp)
                }
            } else {
                Text("정차", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Switch(checked = state.simulatedParked, onCheckedChange = { model.toggleParked() })
            }
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
    val canRemove = state.canOperate && !state.busy && !state.routeUpdating && session.state in setOf("COLLECTING", "PLANNED")
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
                        Text(passenger.displayName, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                        Text(passenger.point.label, color = Muted, fontSize = 17.sp, lineHeight = 23.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (canRemove) TextButton(onClick = { onDelete(passenger) }) { Text("삭제", color = Alert) }
                }
            }
        }
    }
}

@Composable
private fun RouteSettingsCard(session: DriverSession, state: DriverUiState, model: MoagilViewModel) {
    val enabled = state.canOperate && !state.busy && !state.routeUpdating && session.state in setOf("COLLECTING", "PLANNED")
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
                if (!state.canOperate) Text(if (BuildConfig.PLEOS_NATIVE_NAVIGATION) "차량이 P 기어로 정차해야 설정할 수 있습니다." else "선택 사항 변경은 정차 확인 후 할 수 있습니다.", color = Color(0xFF98631D), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PriorityPicker(session: DriverSession, state: DriverUiState, model: MoagilViewModel, enabled: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val selected = session.passengers.firstOrNull { it.id == state.priorityId }?.displayName ?: "지정 안 함"
    Column {
        Text("먼저 하차할 승객", color = Muted, fontSize = 12.sp)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled) { Text(selected) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("지정 안 함") }, onClick = { expanded = false; if (state.priorityId != null) model.setPriority(null) })
                session.passengers.forEach { passenger ->
                    DropdownMenuItem(text = { Text(passenger.displayName) }, onClick = { expanded = false; if (passenger.id != state.priorityId) model.setPriority(passenger.id) })
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
    val canEdit = state.canOperate && !state.busy && !active
    val names = session.passengers.associateBy(Passenger::id)
    var localOrder by remember(session.sessionId) { mutableStateOf(session.selectedOrder) }
    var draggingPassengerId by remember(session.sessionId) { mutableStateOf<String?>(null) }
    var dragStartOrder by remember(session.sessionId) { mutableStateOf<List<String>?>(null) }
    var dragOffsetY by remember(session.sessionId) { mutableFloatStateOf(0f) }
    var optimisticOrderVersion by remember(session.sessionId) { mutableStateOf<Int?>(null) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val rowHeight = 96.dp
    val rowSlotHeight = 104.dp
    val rowSlotHeightPx = with(density) { rowSlotHeight.toPx() }

    LaunchedEffect(session.version) {
        if (draggingPassengerId == null) {
            localOrder = session.selectedOrder
            optimisticOrderVersion = null
        }
    }
    LaunchedEffect(state.errorMessage) {
        if (state.errorMessage != null && optimisticOrderVersion != null && session.version == optimisticOrderVersion) {
            localOrder = session.selectedOrder
            draggingPassengerId = null
            dragStartOrder = null
            dragOffsetY = 0f
            optimisticOrderVersion = null
        }
    }

    fun movedOrder(order: List<String>, from: Int, to: Int): List<String> {
        if (from !in order.indices || to !in order.indices || from == to || !canMoveAcrossPinnedPositions(order, from, to, session.fixedPositions)) return order
        return order.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun saveOrder(next: List<String>) {
        if (next == localOrder || !canEdit || draggingPassengerId != null) return
        localOrder = next
        optimisticOrderVersion = session.version
        model.reorderPassengers(next)
    }

    fun finishDrag() {
        if (draggingPassengerId == null) return
        val originalOrder = dragStartOrder
        draggingPassengerId = null
        dragStartOrder = null
        dragOffsetY = 0f
        if (originalOrder != null && localOrder != originalOrder && canEdit) {
            optimisticOrderVersion = session.version
            model.reorderPassengers(localOrder)
        }
    }

    if (confirmPleosRoute) {
        AlertDialog(
            onDismissRequest = { confirmPleosRoute = false },
            title = { Text("Pleos 내비 목적지를 변경할까요?") },
            text = { Text("최적화된 순서대로 목적지를 한 곳씩 Pleos 내비에 전송합니다. 각 목적지에 도착해 모아길에서 하차를 확인하면 다음 목적지를 새로 요청합니다. 최종 목적지가 있으면 승객 하차를 마친 뒤 별도로 요청합니다. 현재 내비 경로는 바뀔 수 있습니다.") },
            confirmButton = { TextButton(onClick = { confirmPleosRoute = false; model.startRoute() }) { Text("Pleos 내비로 전송") } },
            dismissButton = { TextButton(onClick = { confirmPleosRoute = false }) { Text("취소") } },
        )
    }
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SectionTitle("선택한 하차 순서", "${route.evaluatedCount}개 비교 · 추천/수동 비용 동일 provider")
            Text(
                if (state.routeUpdating) "새 순서의 경로를 계산하고 있어요…"
                else "${route.note} · ${formatDuration(route.durationMs)} · ${route.distanceM?.let { "${it}m" } ?: "거리 미제공"}",
                color = Mint,
                fontWeight = FontWeight.SemiBold,
            )
            Text("위·아래 버튼을 누르거나 점 표시를 길게 눌러 드래그하세요.", color = Muted, fontSize = 12.sp)
            Button(
                onClick = model::optimizeRoute,
                enabled = canEdit && !state.routeUpdating && !state.autoPlanning,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("고정 위치를 유지하고 가장 빠른 경로로 정렬") }
            val rowsHeight = if (localOrder.isEmpty()) 0.dp else rowSlotHeight * (localOrder.size - 1) + rowHeight
            Box(Modifier.fillMaxWidth().height(rowsHeight)) {
                localOrder.forEachIndexed { index, id ->
                    key(id) {
                        val passenger = names[id]
                        val fixedPosition = session.fixedPositions[id]
                        val fixed = fixedPosition == index + 1
                        val isDragging = draggingPassengerId == id
                        val animatedTop by animateDpAsState(
                            targetValue = rowSlotHeight * index,
                            animationSpec = tween(durationMillis = 260),
                            label = "passenger-order-position-$id",
                        )
                        val animatedScale by animateFloatAsState(
                            targetValue = if (isDragging) 1.035f else 1f,
                            animationSpec = tween(durationMillis = 150),
                            label = "passenger-order-scale-$id",
                        )
                        Row(
                            Modifier.fillMaxWidth()
                                .height(rowHeight)
                                .offset {
                                    IntOffset(
                                        x = 0,
                                        y = animatedTop.roundToPx() + if (isDragging) dragOffsetY.roundToInt() else 0,
                                    )
                                }
                                .zIndex(if (isDragging) 2f else 0f)
                                .graphicsLayer {
                                    scaleX = animatedScale
                                    scaleY = animatedScale
                                    shadowElevation = if (isDragging) 12.dp.toPx() else 0f
                                }
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDragging) AccentSoft else PanelRaised)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${index + 1}", color = Mint, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
                            Column(Modifier.weight(1f)) {
                                Text(passenger?.displayName ?: "동승자", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(passenger?.point?.label.orEmpty(), color = Muted, fontSize = 14.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (fixed) Text("${fixedPosition}번째 고정", color = Mint, fontSize = 10.sp)
                            Box(
                                Modifier.padding(horizontal = 3.dp)
                                    .size(width = 34.dp, height = 48.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (isDragging) Color.White else Color.Transparent)
                                    .pointerInput(id, canEdit, fixed) {
                                        if (canEdit && !fixed) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    draggingPassengerId = id
                                                    dragStartOrder = localOrder
                                                    dragOffsetY = 0f
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                                onDragEnd = { finishDrag() },
                                                onDragCancel = {
                                                    dragStartOrder?.let { localOrder = it }
                                                    draggingPassengerId = null
                                                    dragStartOrder = null
                                                    dragOffsetY = 0f
                                                },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    if (draggingPassengerId == id) {
                                                        var nextOffset = dragOffsetY + amount.y
                                                        var index = localOrder.indexOf(id)
                                                         while (nextOffset >= rowSlotHeightPx / 2 && index < localOrder.lastIndex &&
                                                             canMoveAcrossPinnedPositions(localOrder, index, index + 1, session.fixedPositions)
                                                         ) {
                                                            localOrder = movedOrder(localOrder, index, index + 1)
                                                            index += 1
                                                            nextOffset -= rowSlotHeightPx
                                                        }
                                                         while (nextOffset <= -rowSlotHeightPx / 2 && index > 0 &&
                                                             canMoveAcrossPinnedPositions(localOrder, index, index - 1, session.fixedPositions)
                                                         ) {
                                                            localOrder = movedOrder(localOrder, index, index - 1)
                                                            index -= 1
                                                            nextOffset += rowSlotHeightPx
                                                        }
                                                        if (index == localOrder.lastIndex) nextOffset = nextOffset.coerceAtMost(rowSlotHeightPx / 2)
                                                         if (index == 0) nextOffset = nextOffset.coerceAtLeast(-rowSlotHeightPx / 2)
                                                        dragOffsetY = nextOffset
                                                    }
                                                },
                                            )
                                        }
                                    }
                                    .semantics {
                                         contentDescription = if (fixed) "${index + 1}번째 순서 고정" else "길게 눌러 드래그하여 하차 순서 변경"
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Canvas(Modifier.size(16.dp)) {
                                    val radius = 1.6.dp.toPx()
                                    for (row in 0..2) for (column in 0..1) {
                                        drawCircle(
                                            color = if (canEdit && !fixed) Muted else Border,
                                            radius = radius,
                                            center = Offset(x = size.width * (.3f + column * .4f), y = size.height * (.25f + row * .25f)),
                                        )
                                    }
                                }
                            }
                            TextButton(
                                onClick = { saveOrder(movedOrder(localOrder, index, index - 1)) },
                                enabled = canEdit && draggingPassengerId == null && index > 0 && canMoveAcrossPinnedPositions(localOrder, index, index - 1, session.fixedPositions),
                            ) { Text("위") }
                            TextButton(
                                onClick = { saveOrder(movedOrder(localOrder, index, index + 1)) },
                                enabled = canEdit && draggingPassengerId == null && index < localOrder.lastIndex && canMoveAcrossPinnedPositions(localOrder, index, index + 1, session.fixedPositions),
                            ) { Text("아래") }
                        }
                    }
                }
            }
            Text("조회 ${route.fetchedAt} · 출처 ${route.dataSource} · 승객별 ETA는 제공하지 않음", color = Muted, fontSize = 11.sp)
            if (session.state == "PLANNED") {
                Button(
                    onClick = { if (BuildConfig.PLEOS_NATIVE_NAVIGATION) confirmPleosRoute = true else model.startRoute() },
                    enabled = canEdit && !state.routeUpdating && !state.autoPlanning,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(if (BuildConfig.PLEOS_NATIVE_NAVIGATION) "최적 순서 확정 · Pleos 내비 전송" else "경로 확정 · 모의 안내", fontWeight = FontWeight.Bold)
                }
                if (!state.canOperate) Text(if (BuildConfig.PLEOS_NATIVE_NAVIGATION) "차량이 P 기어로 정차해야 내비로 전송할 수 있습니다." else "정차 확인 후 내비로 전송할 수 있습니다.", color = Color(0xFF98631D), fontSize = 12.sp)
            }
        }
    }
}

private fun canMoveAcrossPinnedPositions(order: List<String>, from: Int, to: Int, fixedPositions: Map<String, Int>): Boolean {
    if (from !in order.indices || to !in order.indices || from == to) return false
    if (fixedPositions.containsKey(order[from])) return false
    val fixedIndices = fixedPositions.values.map { it - 1 }.toSet()
    val crossed = if (from < to) (from + 1..to) else (to until from)
    return crossed.none { it in fixedIndices }
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
                    passenger != null -> "다음 하차 · ${passenger.displayName}"
                    finalLeg -> "운전자 최종 목적지 · ${session.finalPoint?.label}"
                    else -> "승객 하차 완료"
                },
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                (passenger?.point ?: session.finalPoint)?.let { "${it.label} · ${it.lat}, ${it.lon}" } ?: "정차 확인 후 이동을 마무리할 수 있습니다.",
                color = Muted,
                fontSize = 18.sp,
                lineHeight = 24.sp,
            )
            if (!state.navigationConfirmed) {
                Button(onClick = { model.retryNavigation() }, enabled = state.canOperate && !state.busy, modifier = Modifier.fillMaxWidth()) { Text(if (BuildConfig.PLEOS_NATIVE_NAVIGATION) "Pleos 내비 경로 다시 요청" else "모의 안내 다시 요청") }
            } else if (!BuildConfig.PLEOS_NATIVE_NAVIGATION) {
                OutlinedButton(onClick = { model.simulateNavigationFailure() }, enabled = state.canOperate && !state.busy, modifier = Modifier.fillMaxWidth()) { Text("안내 실패·재시도 시연") }
            }
            when {
                passenger != null -> Button(onClick = { model.completeCurrentStop() }, enabled = state.canOperate && state.navigationConfirmed && !state.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(if (BuildConfig.PLEOS_NATIVE_NAVIGATION) "정차 확인 · 다음 내비 목적지 전송" else "정차 후 하차 완료 · 시연") }
                finalLeg -> Button(onClick = onFinish, enabled = state.canOperate && state.navigationConfirmed && !state.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("최종 목적지 도착 · 이동 종료") }
                readyToFinish -> Button(onClick = onFinish, enabled = state.canOperate && state.navigationConfirmed && !state.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("이동 종료 · 시연") }
            }
            TextButton(onClick = { model.setCancelConfirmation(true) }, enabled = state.canOperate && !state.busy) { Text("이동 취소", color = Alert) }
            if (!state.canOperate) Text(if (BuildConfig.PLEOS_NATIVE_NAVIGATION) "P 기어와 정차가 확인되면 조작할 수 있습니다." else "정차(시연) 확인 전에는 하차 완료와 종료를 잠급니다.", color = Color(0xFF98631D), fontSize = 12.sp)
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
    Row(Modifier.background(color.copy(alpha = .12f), RoundedCornerShape(40.dp)).padding(horizontal = 13.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
