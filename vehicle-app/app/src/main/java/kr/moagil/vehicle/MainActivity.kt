package kr.moagil.vehicle

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kr.moagil.vehicle.data.DriverSession
import kr.moagil.vehicle.data.Passenger
import kr.moagil.vehicle.data.Point
import kr.moagil.vehicle.ui.DriverUiState
import kr.moagil.vehicle.ui.MoagilViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val Ink = Color(0xFF101820)
private val Panel = Color(0xFF192630)
private val PanelRaised = Color(0xFF22333E)
private val Mint = Color(0xFF75E1CF)
private val Muted = Color(0xFF9AABB4)
private val Alert = Color(0xFFFF9F84)
private val Scheme = darkColorScheme(
    primary = Mint,
    onPrimary = Ink,
    secondary = Color(0xFFB8D4DF),
    background = Ink,
    onBackground = Color(0xFFF3F7F7),
    surface = Panel,
    onSurface = Color(0xFFF3F7F7),
    error = Alert,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(16, 24, 32)
        window.navigationBarColor = android.graphics.Color.rgb(16, 24, 32)
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
    var showDisconnect by remember { mutableStateOf(false) }
    var showFinish by remember { mutableStateOf(false) }
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
    if (showDisconnect) {
        AlertDialog(
            onDismissRequest = { showDisconnect = false },
            title = { Text("이 기기 연결을 해제할까요?") },
            text = { Text("기기에 저장된 차량 토큰을 지웁니다. 서버의 이동 상태는 바뀌지 않습니다.") },
            confirmButton = { TextButton(onClick = { showDisconnect = false; model.disconnectLocal() }) { Text("기기에서 지우기") } },
            dismissButton = { TextButton(onClick = { showDisconnect = false }) { Text("취소") } },
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

    Column(Modifier.fillMaxSize().background(Ink)) {
        Header(state, model, onDisconnect = { showDisconnect = true })
        Divider(color = Color(0xFF2D3B44))
        if (state.session == null) {
            StartPanel(state, model, Modifier.weight(1f))
        } else {
            SessionDashboard(state, model, onDelete = { pendingDelete = it }, onFinish = { showFinish = true }, modifier = Modifier.weight(1f))
        }
        state.errorMessage?.let { MessageBanner(it, Alert, onDismiss = model::clearMessages) }
        state.notice?.let { MessageBanner(it, Mint, onDismiss = model::clearMessages) }
        Footer()
    }
}

@Composable
private fun Header(state: DriverUiState, model: MoagilViewModel, onDisconnect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).background(Mint, CircleShape), contentAlignment = Alignment.Center) {
                Text("길", color = Ink, fontWeight = FontWeight.Black, fontSize = 17.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("모아길", fontWeight = FontWeight.Bold, fontSize = 21.sp)
                Text("차량용 시연 화면", color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.width(18.dp))
            Badge("모의 위치 · 모의 교통 · 모의 내비", Mint)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(if (state.connected == true) "서버 연결" else if (state.connected == false) "연결 끊김" else "연결 확인 중", if (state.connected == true) Mint else Alert)
            state.credentials?.let {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDisconnect) { Text("기기 연결 해제", color = Muted) }
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
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { SafetyCard(state, model) }
            if (wide) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        InviteCard(session, credentials?.joinBaseUrl.orEmpty(), credentials?.inviteToken.orEmpty(), Modifier.weight(0.9f))
                        PassengerCard(session, state, model, onDelete, Modifier.weight(1.5f))
                    }
                }
                item { RouteSettingsCard(session, state, model) }
            } else {
                item { InviteCard(session, credentials?.joinBaseUrl.orEmpty(), credentials?.inviteToken.orEmpty(), Modifier.fillMaxWidth()) }
                item { PassengerCard(session, state, model, onDelete, Modifier.fillMaxWidth()) }
                item { RouteSettingsCard(session, state, model) }
            }
            if (session.route != null && session.state in setOf("PLANNED", "ACTIVE")) {
                item { OrderCard(session, state, model) }
            }
            if (session.state == "ACTIVE") { item { ActiveCard(session, state, model, onFinish) } }
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
    }
}

@Composable
private fun SafetyCard(state: DriverUiState, model: MoagilViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = if (state.simulatedParked) Color(0xFF153A39) else PanelRaised), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (state.simulatedParked) "정차(시연) 상태" else "정차 상태 확인 필요", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (state.simulatedParked) Mint else Color(0xFFFFD18D))
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
                        Text(passenger.displayName, fontWeight = FontWeight.SemiBold)
                        Text("${passenger.point.label} · ${"%.4f".format(Locale.US, passenger.point.lat)}, ${"%.4f".format(Locale.US, passenger.point.lon)}", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("경로 조건", "출발지와 최종 목적지를 포함해 모의 비교")
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
                    Button(onClick = { model.setFinalDestination(true) }, enabled = enabled, modifier = Modifier.align(Alignment.CenterVertically)) { Text("좌표 저장") }
                }
                Text("오프라인 좌표 입력 · 실제 주소 검색과 도로 지도는 제공하지 않습니다.", color = Muted, fontSize = 12.sp)
            }
            PriorityPicker(session, state, model, enabled)
            Button(
                onClick = { model.plan() },
                enabled = enabled && session.passengers.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Text(if (state.busy) "계산 중…" else "가능한 순서 계산", fontWeight = FontWeight.Bold)
            }
            if (!state.simulatedParked) Text("정차 확인 전에는 설정 변경과 계산을 잠급니다.", color = Color(0xFFFFD18D), fontSize = 12.sp)
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
    val active = session.state == "ACTIVE"
    val canEdit = state.simulatedParked && !state.busy && !active
    val names = session.passengers.associateBy(Passenger::id)
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SectionTitle("선택한 하차 순서", "${route.evaluatedCount}개 비교 · 추천/수동 비용 동일 provider")
            Text("${route.note} · ${formatDuration(route.durationMs)} · ${route.distanceM?.let { "${it}m" } ?: "거리 미제공"}", color = Mint, fontWeight = FontWeight.SemiBold)
            session.selectedOrder.forEachIndexed { index, id ->
                val passenger = names[id]
                Row(Modifier.fillMaxWidth().background(PanelRaised, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", color = Mint, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp))
                    Column(Modifier.weight(1f)) {
                        Text(passenger?.displayName ?: "동승자", fontWeight = FontWeight.SemiBold)
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
                Button(onClick = { model.startRoute() }, enabled = canEdit, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("경로 확정 · 모의 안내", fontWeight = FontWeight.Bold)
                }
                if (!state.simulatedParked) Text("정차 확인 전에는 확정할 수 없습니다.", color = Color(0xFFFFD18D), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ActiveCard(session: DriverSession, state: DriverUiState, model: MoagilViewModel, onFinish: () -> Unit) {
    val passenger = session.selectedOrder.getOrNull(session.activeIndex)?.let { id -> session.passengers.firstOrNull { it.id == id } }
    val finalLeg = passenger == null && session.finalPoint != null
    val readyToFinish = passenger == null && session.finalPoint == null
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF123A3B)), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("모의 내비 안내 · 실제 차량 내비 연결 없음", color = Mint, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(state.navigationMessage.ifBlank { "앱이 다시 연결됐습니다. 현재 목적지를 운전자가 확인해 주세요." }, color = Color(0xFFD9EBE8))
            Divider(color = Color(0xFF315655))
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
            )
            if (!state.navigationConfirmed) {
                Button(onClick = { model.retryNavigation() }, enabled = state.simulatedParked && !state.busy, modifier = Modifier.fillMaxWidth()) { Text("모의 안내 다시 요청") }
            } else {
                OutlinedButton(onClick = { model.simulateNavigationFailure() }, enabled = state.simulatedParked && !state.busy, modifier = Modifier.fillMaxWidth()) { Text("안내 실패·재시도 시연") }
            }
            when {
                passenger != null -> Button(onClick = { model.completeCurrentStop() }, enabled = state.simulatedParked && state.navigationConfirmed && !state.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("정차 후 하차 완료 · 시연") }
                finalLeg -> Button(onClick = onFinish, enabled = state.simulatedParked && state.navigationConfirmed && !state.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("최종 목적지 도착 · 이동 종료") }
                readyToFinish -> Button(onClick = onFinish, enabled = state.simulatedParked && state.navigationConfirmed && !state.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("이동 종료 · 시연") }
            }
            TextButton(onClick = { model.setCancelConfirmation(true) }, enabled = state.simulatedParked && !state.busy) { Text("이동 취소", color = Alert) }
            if (!state.simulatedParked) Text("정차(시연) 확인 전에는 하차 완료와 종료를 잠급니다.", color = Color(0xFFFFD18D), fontSize = 12.sp)
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
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF20313B)), shape = RoundedCornerShape(14.dp)) {
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
        Text("MOAGIL · mockDebug", color = Muted, fontSize = 11.sp)
        Text("시연용 좌표·직선거리 추정 · 실제 운행 금지", color = Muted, fontSize = 11.sp)
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
