package kr.moagil.vehicle.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.moagil.vehicle.data.ApiException
import kr.moagil.vehicle.data.DriverCredentials
import kr.moagil.vehicle.data.DriverSession
import kr.moagil.vehicle.data.DriverVault
import kr.moagil.vehicle.data.MoagilApi
import kr.moagil.vehicle.data.Passenger
import kr.moagil.vehicle.data.Point
import kr.moagil.vehicle.navigation.DrivingState
import kr.moagil.vehicle.navigation.createNavigationPort
import kr.moagil.vehicle.navigation.MockDrivingStatePort
import kr.moagil.vehicle.navigation.NavigationResult
import kr.moagil.vehicle.BuildConfig

data class DriverUiState(
    val apiBaseUrl: String = "http://10.0.2.2:8080",
    val credentials: DriverCredentials? = null,
    val session: DriverSession? = null,
    val connected: Boolean? = null,
    val simulatedParked: Boolean = false,
    val busy: Boolean = false,
    val finalEnabled: Boolean = false,
    val finalLat: String = "37.510000",
    val finalLon: String = "126.950000",
    val priorityId: String? = null,
    val navigationMessage: String = "",
    val navigationConfirmed: Boolean = false,
    val errorMessage: String? = null,
    val notice: String? = null,
    val cancelConfirmation: Boolean = false,
)

class MoagilViewModel(application: Application) : AndroidViewModel(application) {
    private val vault = DriverVault(application)
    private val navigation = createNavigationPort(application)
    private val driving = MockDrivingStatePort()
    private val _state = MutableStateFlow(DriverUiState())
    val state: StateFlow<DriverUiState> = _state.asStateFlow()
    private var pollJob: Job? = null
    private var healthJob: Job? = null

    init {
        healthJob = viewModelScope.launch {
            while (isActive) {
                val current = _state.value
                val reachable = runCatching { MoagilApi(current.apiBaseUrl).health() }.getOrDefault(false)
                _state.value = _state.value.copy(connected = reachable)
                delay(15_000)
            }
        }
        viewModelScope.launch {
            vault.restore()?.let { credentials ->
                _state.value = _state.value.copy(apiBaseUrl = credentials.apiBaseUrl, credentials = credentials)
                runCatching { MoagilApi(credentials.apiBaseUrl).snapshot(credentials) }
                    .onSuccess { session -> acceptSession(session, "저장된 이동 정보를 다시 연결했습니다.") }
                    .onFailure { error -> report(error) }
                startPolling(credentials)
            }
        }
    }

    fun setApiBaseUrl(value: String) { _state.value = _state.value.copy(apiBaseUrl = value, errorMessage = null) }
    fun setFinalLat(value: String) { _state.value = _state.value.copy(finalLat = value) }
    fun setFinalLon(value: String) { _state.value = _state.value.copy(finalLon = value) }
    fun clearMessages() { _state.value = _state.value.copy(errorMessage = null, notice = null) }
    fun setCancelConfirmation(show: Boolean) { _state.value = _state.value.copy(cancelConfirmation = show) }

    fun toggleParked() {
        val parked = !_state.value.simulatedParked
        _state.value = _state.value.copy(simulatedParked = parked, notice = if (parked) "정차(시연) 상태입니다. 실제 차량 센서는 연결되지 않았습니다." else "정차 확인을 해제했습니다. 운전자 입력을 잠급니다.")
    }

    fun createSession(demo: Boolean) = driverAction {
        val baseUrl = _state.value.apiBaseUrl.trim().trimEnd('/')
        require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) { "서버 주소는 http:// 또는 https://로 시작해야 합니다." }
        val (credentials, demoSession) = MoagilApi(baseUrl).createSession(demo)
        vault.save(credentials)
        _state.value = _state.value.copy(
            apiBaseUrl = baseUrl,
            credentials = credentials,
            session = demoSession,
            connected = true,
            simulatedParked = false,
            priorityId = demoSession?.priorityPassengerId,
            finalEnabled = demoSession?.finalPoint != null,
            errorMessage = null,
            notice = if (demo) "시연용 위치 4개를 불러왔습니다. 순서를 계산하면 서버가 실제 도로 경로를 비교합니다." else "새 이동을 만들었습니다. 초대 QR에서 참가를 기다립니다.",
            navigationMessage = "",
        )
        if (demoSession == null) refreshNow(credentials)
        startPolling(credentials)
    }

    fun setPriority(passengerId: String?) = driverAction {
        ensureParked()
        val session = requireSession()
        val final = finalPointOrNull()
        val updated = MoagilApi(requireCredentials().apiBaseUrl).saveSettings(requireCredentials(), session, final, passengerId)
        acceptSession(updated, "우선 하차 설정을 저장했습니다. 경로를 다시 계산해 주세요.")
    }

    fun setFinalDestination(enabled: Boolean) = driverAction {
        ensureParked()
        val session = requireSession()
        val final = if (enabled) finalPointOrNull(required = true) else null
        val updated = MoagilApi(requireCredentials().apiBaseUrl).saveSettings(requireCredentials(), session, final, _state.value.priorityId)
        _state.value = _state.value.copy(finalEnabled = enabled)
        acceptSession(updated, "운전자 최종 목적지를 저장했습니다. 경로를 다시 계산해 주세요.")
    }

    fun plan() = driverAction {
        ensureParked()
        val session = requireSession()
        check(session.passengers.isNotEmpty()) { "동승자가 한 명 이상 등록돼야 경로를 계산할 수 있습니다." }
        val updated = MoagilApi(requireCredentials().apiBaseUrl).plan(requireCredentials(), session)
        val routeNote = updated.route?.note ?: "경로 결과를 확인하지 못했습니다."
        acceptSession(updated, "${updated.route?.evaluatedCount ?: 0}개 순서를 비교했습니다. $routeNote")
    }

    fun movePassenger(passengerId: String, delta: Int) = driverAction {
        ensureParked()
        val session = requireSession()
        check(session.state == "PLANNED") { "먼저 가능한 순서를 계산해 주세요." }
        val order = session.selectedOrder.toMutableList()
        val from = order.indexOf(passengerId)
        val to = (from + delta).coerceIn(0, order.lastIndex)
        check(from >= 0 && from != to) { "이 동승자는 더 이동할 수 없습니다." }
        check(session.priorityPassengerId == null || (passengerId != session.priorityPassengerId && to != 0)) { "우선 승객은 첫 순서에 고정됩니다." }
        order.removeAt(from)
        order.add(to, passengerId)
        val updated = MoagilApi(requireCredentials().apiBaseUrl).selectOrder(requireCredentials(), session, order)
        acceptSession(updated, "수동 순서를 저장하고 선택한 순서의 모의 비용을 다시 계산했습니다.")
    }

    fun startRoute() = driverAction {
        ensureParked()
        val credentials = requireCredentials()
        val updated = MoagilApi(credentials.apiBaseUrl).start(credentials, requireSession())
        acceptSession(updated, if (BuildConfig.PLEOS_NATIVE_NAVIGATION) "최적 순서를 확정했습니다. Pleos 내비에 첫 하차 목적지를 요청합니다." else "경로를 확정했습니다. 실제 내비 앱은 연결되지 않았습니다.")
        requestCurrentNavigation()
    }

    fun completeCurrentStop() = driverAction {
        ensureParked()
        val credentials = requireCredentials()
        val session = requireSession()
        check(session.state == "ACTIVE" && session.activeIndex < session.selectedOrder.size) { "현재 하차 안내가 없습니다." }
        check(_state.value.navigationConfirmed) { "현재 목적지 안내를 확인한 뒤 하차를 완료해 주세요." }
        val passengerId = session.selectedOrder[session.activeIndex]
        val updated = MoagilApi(credentials.apiBaseUrl).completeStop(credentials, session, passengerId)
        acceptSession(updated, "하차 확인을 저장했습니다. 다음 목적지를 다시 확인해 주세요.")
        requestCurrentNavigation()
    }

    fun finishTrip() = driverAction {
        ensureParked()
        val session = requireSession()
        check(session.state == "ACTIVE" && session.activeIndex >= session.selectedOrder.size) { "모든 승객 하차를 마친 뒤 이동을 종료해 주세요." }
        check(_state.value.navigationConfirmed) { "현재 목적지 안내를 확인한 뒤 종료해 주세요." }
        val credentials = requireCredentials()
        val updated = MoagilApi(credentials.apiBaseUrl).completeTrip(credentials, session)
        acceptSession(updated, "시연 이동을 종료했습니다. 초대 링크가 닫혔습니다.")
        navigation.close()
    }

    fun cancelTrip() = driverAction {
        ensureParked()
        val credentials = requireCredentials()
        val updated = MoagilApi(credentials.apiBaseUrl).cancel(credentials, requireSession())
        acceptSession(updated, "이동을 취소했습니다. 초대 링크가 닫혔습니다.")
        navigation.close()
    }

    fun removePassenger(passenger: Passenger) = driverAction {
        ensureParked()
        val credentials = requireCredentials()
        val updated = MoagilApi(credentials.apiBaseUrl).removePassenger(credentials, requireSession(), passenger.id)
        acceptSession(updated, "${passenger.displayName} 참가를 삭제했습니다.")
    }

    fun disconnectLocal() {
        pollJob?.cancel()
        vault.clear()
        viewModelScope.launch { navigation.close() }
        _state.value = _state.value.copy(credentials = null, session = null, simulatedParked = false, priorityId = null, finalEnabled = false, navigationMessage = "", notice = "이 기기에서 차량 토큰을 지웠습니다. 서버 이동은 취소되지 않았습니다.")
    }

    fun simulateNavigationFailure() = driverAction {
        ensureParked()
        check(requireSession().state == "ACTIVE") { "먼저 모의 안내를 시작해 주세요." }
        check(navigation.failNextRequest()) { "안내 실패 시연은 mock 빌드에서만 사용할 수 있습니다." }
        requestCurrentNavigation()
    }

    fun retryNavigation() = driverAction {
        ensureParked()
        check(requireSession().state == "ACTIVE") { "현재 모의 안내가 없습니다." }
        requestCurrentNavigation()
    }

    private suspend fun requestCurrentNavigation() {
        val session = _state.value.session ?: return
        val order = session.selectedOrder
        val point = when {
            session.state != "ACTIVE" -> null
            session.activeIndex < order.size -> session.passengers.firstOrNull { it.id == order[session.activeIndex] }?.point
            session.finalPoint != null -> session.finalPoint
            else -> null
        }
        if (point == null) {
            _state.value = _state.value.copy(navigationMessage = "모든 하차를 마쳤습니다. 이동 종료를 확인해 주세요.", navigationConfirmed = true)
            return
        }
        val startedAt = SystemClock.elapsedRealtime()
        _state.value = _state.value.copy(
            navigationMessage = if (BuildConfig.PLEOS_NATIVE_NAVIGATION) {
                "Pleos NaviHelper 경로 요청 중 · ${point.label} · 0.0초 경과"
            } else {
                "모의 내비 안내 요청 중… · ${point.label}"
            },
            navigationConfirmed = false,
        )
        val elapsedTicker = if (BuildConfig.PLEOS_NATIVE_NAVIGATION) viewModelScope.launch {
            while (isActive) {
                delay(200)
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                _state.value = _state.value.copy(
                    navigationMessage = "Pleos NaviHelper 경로 요청 중 · ${point.label} · ${formatElapsed(elapsed)}초 경과",
                )
            }
        } else null
        val result = try {
            navigation.navigateTo(point)
        } finally {
            elapsedTicker?.cancel()
        }
        _state.value = when (result) {
            NavigationResult.Confirmed -> _state.value.copy(navigationMessage = "모의 안내 확인 · ${point.label} · 실제 차량 내비는 연결되지 않았습니다.", navigationConfirmed = true)
            is NavigationResult.Completed -> _state.value.copy(
                navigationMessage = "Pleos 내비 경로 탐색 완료 콜백 확인 · ${point.label} · ${formatElapsed(result.elapsedMs)}초",
                navigationConfirmed = true,
            )
            is NavigationResult.DestinationConfirmed -> _state.value.copy(
                navigationMessage = "Pleos 내비 목적지 수신 확인 · ${point.label} · ${formatElapsed(result.elapsedMs)}초 · ${result.distanceM}m / ${result.durationSec / 60}분",
                navigationConfirmed = true,
            )
            is NavigationResult.Rejected -> {
                val duration = result.elapsedMs?.let { " · ${formatElapsed(it)}초" }.orEmpty()
                _state.value.copy(
                    navigationMessage = if (BuildConfig.PLEOS_NATIVE_NAVIGATION) "Pleos 내비 요청 실패 · ${result.reason}$duration" else "모의 안내 거부 · ${result.reason}",
                    navigationConfirmed = false,
                )
            }
            NavigationResult.Unavailable -> _state.value.copy(navigationMessage = if (BuildConfig.PLEOS_NATIVE_NAVIGATION) "Pleos 내비 서비스를 사용할 수 없습니다. 연결 상태와 권한을 확인하세요." else "모의 안내를 사용할 수 없습니다. 운전자가 목적지를 확인하세요.", navigationConfirmed = false)
        }
    }

    private fun formatElapsed(elapsedMs: Long): String = "${elapsedMs / 1_000}.${(elapsedMs % 1_000) / 100}"

    private suspend fun refreshNow(credentials: DriverCredentials) {
        runCatching { MoagilApi(credentials.apiBaseUrl).snapshot(credentials) }
            .onSuccess { acceptSession(it, null) }
            .onFailure { report(it) }
    }

    private fun startPolling(credentials: DriverCredentials) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            var delayMs = 2_000L
            while (isActive && _state.value.credentials?.sessionId == credentials.sessionId && _state.value.session?.state != "COMPLETED" && _state.value.session?.state != "CANCELLED" && _state.value.session?.state != "EXPIRED") {
                try {
                    val snapshot = MoagilApi(credentials.apiBaseUrl).snapshot(credentials)
                    _state.value = _state.value.copy(connected = true)
                    acceptSession(snapshot, null)
                    delayMs = 2_000L
                    delay(delayMs)
                } catch (error: Exception) {
                    _state.value = _state.value.copy(connected = false)
                    delay(delayMs)
                    delayMs = (delayMs * 2).coerceAtMost(15_000L)
                }
            }
        }
    }

    private fun finalPointOrNull(required: Boolean = false): Point? {
        if (!_state.value.finalEnabled && !required) return null
        val lat = _state.value.finalLat.toDoubleOrNull()
        val lon = _state.value.finalLon.toDoubleOrNull()
        require(lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) { "운전자 목적지 위도·경도를 확인해 주세요." }
        return Point(lat, lon, "운전자 최종 목적지 · 시연")
    }

    private fun acceptSession(session: DriverSession, notice: String?) {
        _state.value = _state.value.copy(
            session = session,
            connected = true,
            priorityId = session.priorityPassengerId,
            finalEnabled = session.finalPoint != null,
            finalLat = session.finalPoint?.lat?.toString() ?: _state.value.finalLat,
            finalLon = session.finalPoint?.lon?.toString() ?: _state.value.finalLon,
            errorMessage = null,
            notice = notice ?: _state.value.notice,
        )
    }

    private suspend fun launchBusy(action: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, errorMessage = null)
        try { action() } catch (error: Exception) { report(error) }
        finally { _state.value = _state.value.copy(busy = false) }
    }

    private fun driverAction(action: suspend () -> Unit) = viewModelScope.launch { launchBusy(action) }

    override fun onCleared() {
        pollJob?.cancel()
        healthJob?.cancel()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { navigation.close() }
        super.onCleared()
    }

    private fun requireSession() = checkNotNull(_state.value.session) { "이동 정보를 먼저 불러와 주세요." }
    private fun requireCredentials() = checkNotNull(_state.value.credentials) { "차량 인증 정보가 없습니다." }
    private fun ensureParked() = check(driving.state(_state.value.simulatedParked) == DrivingState.PARKED) { "정차 상태(시연)를 먼저 확인해 주세요." }

    private fun report(error: Throwable) {
        val message = when (error) {
            is ApiException -> error.message
            is IllegalArgumentException, is IllegalStateException -> error.message ?: "입력을 확인해 주세요."
            else -> if (error is java.net.UnknownHostException || error is java.net.ConnectException) "서버에 연결할 수 없습니다. 주소와 같은 Wi-Fi 연결을 확인해 주세요." else "요청에 실패했습니다. 연결을 확인하고 다시 시도해 주세요."
        }
        _state.value = _state.value.copy(errorMessage = message)
    }
}
