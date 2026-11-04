package kr.moagil.vehicle.ui

import android.app.Application
import android.net.Uri
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
import kr.moagil.vehicle.passengerName
import kr.moagil.vehicle.data.ApiException
import kr.moagil.vehicle.data.DriverCredentials
import kr.moagil.vehicle.data.DriverSession
import kr.moagil.vehicle.data.DriverVault
import kr.moagil.vehicle.data.MoagilApi
import kr.moagil.vehicle.data.Passenger
import kr.moagil.vehicle.data.Point
import kr.moagil.vehicle.navigation.createNavigationPort
import kr.moagil.vehicle.navigation.VehicleContext
import kr.moagil.vehicle.navigation.createVehicleContextPort
import kr.moagil.vehicle.navigation.NavigationResult
import kr.moagil.vehicle.BuildConfig

data class DriverUiState(
    val apiBaseUrl: String = PRODUCTION_SERVICE_URL,
    val kakaoMapJavascriptKey: String = "",
    val credentials: DriverCredentials? = null,
    val session: DriverSession? = null,
    val connected: Boolean? = null,
    val simulatedParked: Boolean = false,
    val vehicleContext: VehicleContext = VehicleContext(),
    val vehiclePermissionGranted: Boolean = false,
    val canOperate: Boolean = false,
    val busy: Boolean = false,
    val autoPlanning: Boolean = false,
    val autoPlanError: String? = null,
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

private const val PRODUCTION_SERVICE_URL = BuildConfig.DEFAULT_API_BASE_URL

private fun isLocalServiceUrl(value: String): Boolean {
    val host = Uri.parse(value.trim()).host?.lowercase() ?: return true
    if (host == "localhost" || host.endsWith(".localhost") || host == "10.0.2.2") return true
    val octets = host.split('.').mapNotNull { it.toIntOrNull() }
    if (octets.size != 4 || octets.any { it !in 0..255 }) return false
    val (first, second) = octets
    return first == 10 || first == 127 || (first == 172 && second in 16..31) || (first == 192 && second == 168)
}

class MoagilViewModel(application: Application) : AndroidViewModel(application) {
    private val vault = DriverVault(application)
    private val navigation = createNavigationPort(application)
    private val vehicleContextPort = createVehicleContextPort(application)
    private val _state = MutableStateFlow(DriverUiState())
    val state: StateFlow<DriverUiState> = _state.asStateFlow()
    private var pollJob: Job? = null
    private var healthJob: Job? = null
    private var autoPlanJob: Job? = null
    private var originSyncJob: Job? = null
    private var autoPlanAttemptKey: String? = null
    private var vehicleContextStarted = false
    private var pleosCoveredPassengerUntil = -1
    private var pleosSegmentIncludesFinalDestination = false

    init {
        if (!BuildConfig.PLEOS_NATIVE_NAVIGATION) startVehicleContext()
        healthJob = viewModelScope.launch {
            while (isActive) {
                val current = _state.value
                val reachable = runCatching { MoagilApi(current.apiBaseUrl).health() }.getOrDefault(false)
                _state.value = _state.value.copy(connected = reachable)
                delay(15_000)
            }
        }
        viewModelScope.launch {
            val previousCredentials = vault.restore()
            if (previousCredentials != null && !isLocalServiceUrl(previousCredentials.apiBaseUrl)) {
                _state.value = _state.value.copy(apiBaseUrl = previousCredentials.apiBaseUrl)
            }
            createSession(false, previousCredentials)
        }
    }

    fun setApiBaseUrl(value: String) { _state.value = _state.value.copy(apiBaseUrl = value, errorMessage = null) }
    fun setFinalLat(value: String) { _state.value = _state.value.copy(finalLat = value) }
    fun setFinalLon(value: String) { _state.value = _state.value.copy(finalLon = value) }
    fun clearMessages() { _state.value = _state.value.copy(errorMessage = null, notice = null) }
    fun setCancelConfirmation(show: Boolean) { _state.value = _state.value.copy(cancelConfirmation = show) }

    fun startVehicleContext() {
        if (vehicleContextStarted) return
        vehicleContextStarted = true
        _state.value = _state.value.copy(vehiclePermissionGranted = true)
        vehicleContextPort.start { context ->
            _state.value = _state.value.copy(
                vehicleContext = context,
                canOperate = if (BuildConfig.PLEOS_NATIVE_NAVIGATION) context.isParked else _state.value.simulatedParked,
            )
            context.location?.let(::syncOriginWhenUseful)
        }
    }

    fun setVehiclePermissionDenied() {
        _state.value = _state.value.copy(
            vehiclePermissionGranted = false,
            canOperate = false,
            vehicleContext = _state.value.vehicleContext.copy(
                signalsReady = false,
                speedMetersPerSecond = null,
                gearSelection = null,
                message = "차량 위치·속도 권한을 허용해 주세요",
            ),
        )
    }

    fun toggleParked() {
        if (BuildConfig.PLEOS_NATIVE_NAVIGATION) return
        val parked = !_state.value.simulatedParked
        _state.value = _state.value.copy(simulatedParked = parked, canOperate = parked, notice = if (parked) "시연용 정차 상태입니다." else "정차 확인을 해제했습니다. 운전자 입력을 잠급니다.")
    }

    fun createSession(demo: Boolean, previousCredentials: DriverCredentials? = null) = driverAction {
        autoPlanJob?.cancel()
        autoPlanAttemptKey = null
        val previous = previousCredentials ?: _state.value.credentials
        if (previous != null) {
            runCatching {
                val api = MoagilApi(previous.apiBaseUrl)
                val oldSession = api.snapshot(previous)
                if (oldSession.state !in setOf("COMPLETED", "CANCELLED", "EXPIRED")) {
                    api.cancel(previous, oldSession)
                }
            }
        }
        val baseUrl = _state.value.apiBaseUrl.trim().trimEnd('/')
        require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) { "서버 주소는 http:// 또는 https://로 시작해야 합니다." }
        val api = MoagilApi(baseUrl)
        val (credentials, demoSession) = api.createSession(demo)
        val kakaoMapJavascriptKey = runCatching { api.kakaoMapJavascriptKey() }
            .getOrNull()
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: BuildConfig.KAKAO_MAP_JAVASCRIPT_KEY
        vault.save(credentials)
        _state.value = _state.value.copy(
            apiBaseUrl = baseUrl,
            kakaoMapJavascriptKey = kakaoMapJavascriptKey,
            credentials = credentials,
            session = demoSession,
            connected = true,
            simulatedParked = false,
            canOperate = BuildConfig.PLEOS_NATIVE_NAVIGATION && _state.value.vehicleContext.isParked,
            priorityId = demoSession?.priorityPassengerId,
            finalEnabled = demoSession?.finalPoint != null,
            errorMessage = null,
            notice = if (demo) "시연용 위치 4개를 불러왔습니다. 순서를 계산하면 서버가 실제 도로 경로를 비교합니다." else "새 이동을 만들었습니다. 초대 QR에서 참가를 기다립니다.",
            navigationMessage = "",
        )
        var session = demoSession
        if (session == null) {
            refreshNow(credentials)
            session = _state.value.session
        }
        if (BuildConfig.PLEOS_NATIVE_NAVIGATION && session != null &&
            _state.value.vehicleContext.signalsReady && _state.value.vehicleContext.hasFreshLocation
        ) {
            syncCurrentVehicleOrigin(session)
        }
        startPolling(credentials)
    }

    fun setPriority(passengerId: String?) = driverAction {
        val session = requireSession()
        check(session.state in setOf("COLLECTING", "PLANNED")) { "이동 시작 전까지만 우선 하차 위치를 지정할 수 있습니다." }
        autoPlanJob?.cancel()
        autoPlanAttemptKey = null
        val reusableSingleStopRoute = session.passengers.size == 1 && session.route != null
        val final = finalPointOrNull()
        val updated = MoagilApi(requireCredentials().apiBaseUrl).saveSettings(
            requireCredentials(), session, final, passengerId, routePreference = session.routePreference,
        )
        val reused = reusableSingleStopRoute && updated.route != null
        acceptSession(updated, if (reused) "우선 하차 설정을 저장했습니다." else "우선 하차 설정을 저장하고 경로를 다시 계산합니다.")
    }

    fun setRoutePreference(preference: String) = driverAction {
        check(preference in setOf("FREE", "TIME")) { "무료 도로 또는 최단 시간 중에서 선택해 주세요." }
        val session = requireSession()
        check(session.state in setOf("COLLECTING", "PLANNED")) { "이동 시작 전까지만 도로 경로를 바꿀 수 있습니다." }
        if (session.routePreference == preference) return@driverAction
        autoPlanJob?.cancel()
        autoPlanAttemptKey = null
        val updated = MoagilApi(requireCredentials().apiBaseUrl).saveSettings(
            requireCredentials(), session, session.finalPoint, session.priorityPassengerId,
            routePreference = preference,
        )
        acceptSession(updated, if (preference == "FREE") "무료 도로로 경로를 다시 계산합니다." else "최단 시간으로 경로를 다시 계산합니다.")
    }

    fun setFinalDestination(enabled: Boolean) = driverAction {
        ensureParked()
        val session = requireSession()
        val final = if (enabled) finalPointOrNull(required = true) else null
        val updated = MoagilApi(requireCredentials().apiBaseUrl).saveSettings(
            requireCredentials(), session, final, _state.value.priorityId, routePreference = session.routePreference,
        )
        _state.value = _state.value.copy(finalEnabled = enabled)
        acceptSession(updated, "운전자 최종 목적지를 저장했습니다. 경로를 다시 계산해 주세요.")
    }

    fun plan() = driverAction {
        ensureParked()
        val session = syncCurrentVehicleOrigin(requireSession())
        check(session.passengers.isNotEmpty()) { "동승자가 한 명 이상 등록돼야 경로를 계산할 수 있습니다." }
        val updated = MoagilApi(requireCredentials().apiBaseUrl).plan(requireCredentials(), session)
        val routeNote = updated.route?.note ?: "경로 결과를 확인하지 못했습니다."
        acceptSession(updated, "${updated.route?.evaluatedCount ?: 0}개 순서를 비교했습니다. $routeNote")
    }

    fun recalculate() {
        val session = _state.value.session ?: return
        autoPlanAttemptKey = null
        autoPlanJob?.cancel()
        scheduleAutoPlan(session, force = true)
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
        var session = syncCurrentVehicleOrigin(requireSession())
        if (session.route == null) {
            session = MoagilApi(credentials.apiBaseUrl).plan(credentials, session)
            acceptSession(session, "현재 차량 위치 기준으로 순서를 다시 계산했습니다.")
        }
        val updated = MoagilApi(credentials.apiBaseUrl).start(credentials, session)
        pleosCoveredPassengerUntil = -1
        pleosSegmentIncludesFinalDestination = false
        acceptSession(updated, if (BuildConfig.PLEOS_NATIVE_NAVIGATION) "최적 순서를 확정했습니다. Pleos 내비에 다음 정차 구간을 요청합니다." else "경로를 확정했습니다. 실제 내비 앱은 연결되지 않았습니다.")
        requestCurrentNavigation(force = true)
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
        acceptSession(updated, "${passengerName(passenger.displayName)} 참가를 삭제했습니다.")
    }

    fun disconnectLocal() {
        pollJob?.cancel()
        vault.clear()
        pleosCoveredPassengerUntil = -1
        pleosSegmentIncludesFinalDestination = false
        viewModelScope.launch { navigation.close() }
        _state.value = _state.value.copy(credentials = null, session = null, simulatedParked = false, canOperate = BuildConfig.PLEOS_NATIVE_NAVIGATION && _state.value.vehicleContext.isParked, priorityId = null, finalEnabled = false, navigationMessage = "", notice = "이 기기에서 차량 토큰을 지웠습니다. 서버 이동은 취소되지 않았습니다.")
    }

    fun simulateNavigationFailure() = driverAction {
        ensureParked()
        check(requireSession().state == "ACTIVE") { "먼저 모의 안내를 시작해 주세요." }
        check(navigation.failNextRequest()) { "안내 실패 시연은 mock 빌드에서만 사용할 수 있습니다." }
        requestCurrentNavigation(force = true)
    }

    fun retryNavigation() = driverAction {
        ensureParked()
        check(requireSession().state == "ACTIVE") { "현재 모의 안내가 없습니다." }
        requestCurrentNavigation(force = true)
    }

    private suspend fun requestCurrentNavigation(force: Boolean = false) {
        val session = _state.value.session ?: return
        val remainingPassengerStops = if (session.state == "ACTIVE") {
            session.selectedOrder.drop(session.activeIndex)
                .mapNotNull { id -> session.passengers.firstOrNull { it.id == id }?.point }
        } else {
            emptyList()
        }
        val remainingStops = remainingPassengerStops + listOfNotNull(session.finalPoint)
        if (remainingStops.isEmpty()) {
            _state.value = _state.value.copy(navigationMessage = "모든 하차를 마쳤습니다. 이동 종료를 확인해 주세요.", navigationConfirmed = true)
            return
        }
        if (BuildConfig.PLEOS_NATIVE_NAVIGATION && !force) {
            when {
                session.activeIndex < pleosCoveredPassengerUntil -> {
                    _state.value = _state.value.copy(
                        navigationMessage = "Pleos 내비 구간 안내 중 · 다음 하차 ${remainingPassengerStops.firstOrNull()?.label ?: "최종 목적지"}",
                        navigationConfirmed = true,
                    )
                    return
                }
                session.activeIndex >= session.selectedOrder.size && pleosSegmentIncludesFinalDestination -> {
                    _state.value = _state.value.copy(
                        navigationMessage = "Pleos 내비 최종 목적지 안내 중 · ${session.finalPoint?.label}",
                        navigationConfirmed = true,
                    )
                    return
                }
            }
        }
        val nextStop = remainingStops.first()
        val routeSegment = remainingStops.take(3)
        val destination = routeSegment.last()
        val waypoints = routeSegment.dropLast(1)
        val segmentPassengerCount = minOf(remainingPassengerStops.size, routeSegment.size)
        val includesFinalDestination = routeSegment.size > segmentPassengerCount
        val segmentLabel = if (routeSegment.size == 1) nextStop.label else "${nextStop.label}부터 ${destination.label}까지 ${routeSegment.size}곳"
        val startedAt = SystemClock.elapsedRealtime()
        _state.value = _state.value.copy(
            navigationMessage = if (BuildConfig.PLEOS_NATIVE_NAVIGATION) {
                "Pleos 내비 다음 구간 요청 중 · $segmentLabel · 0.0초 경과"
            } else {
                "모의 내비 안내 요청 중… · $segmentLabel"
            },
            navigationConfirmed = false,
        )
        val elapsedTicker = if (BuildConfig.PLEOS_NATIVE_NAVIGATION) viewModelScope.launch {
            while (isActive) {
                delay(200)
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                _state.value = _state.value.copy(
                    navigationMessage = "Pleos 내비 다음 구간 요청 중 · $segmentLabel · ${formatElapsed(elapsed)}초 경과",
                )
            }
        } else null
        val result = try {
            navigation.navigateRoute(destination, waypoints)
        } finally {
            elapsedTicker?.cancel()
        }
        if (BuildConfig.PLEOS_NATIVE_NAVIGATION && result !is NavigationResult.Rejected && result != NavigationResult.Unavailable) {
            pleosCoveredPassengerUntil = session.activeIndex + segmentPassengerCount
            pleosSegmentIncludesFinalDestination = includesFinalDestination
        }
        _state.value = when (result) {
            NavigationResult.Confirmed -> _state.value.copy(navigationMessage = "모의 안내 확인 · $segmentLabel · 실제 차량 내비는 연결되지 않았습니다.", navigationConfirmed = true)
            is NavigationResult.Completed -> {
                _state.value.copy(
                    navigationMessage = "Pleos 내비 경로 탐색 완료 · 다음 하차 ${nextStop.label} · 구간 ${routeSegment.size}곳 · ${formatElapsed(result.elapsedMs)}초",
                    navigationConfirmed = true,
                )
            }
            is NavigationResult.DestinationConfirmed -> _state.value.copy(
                navigationMessage = "Pleos 내비 경로 정보 확인 · 다음 하차 ${nextStop.label}" +
                    " · ${formatElapsed(result.elapsedMs)}초 · ${result.distanceM}m / ${result.durationSec / 60}분",
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
            var delayMs = 500L
            while (isActive && _state.value.credentials?.sessionId == credentials.sessionId && _state.value.session?.state != "COMPLETED" && _state.value.session?.state != "CANCELLED" && _state.value.session?.state != "EXPIRED") {
                try {
                    val snapshot = MoagilApi(credentials.apiBaseUrl).snapshot(credentials)
                    _state.value = _state.value.copy(connected = true)
                    acceptSession(snapshot, null)
                    delayMs = 500L
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
        if (session.state in setOf("COLLECTING", "PLANNED") && session.passengers.isNotEmpty() && session.route == null) {
            scheduleAutoPlan(session)
        } else if (session.route != null) {
            _state.value = _state.value.copy(autoPlanError = null)
        }
    }

    private fun scheduleAutoPlan(session: DriverSession, force: Boolean = false) {
        if (_state.value.busy) return
        val credentials = _state.value.credentials ?: return
        if (originSyncJob?.isActive == true) return
        if (BuildConfig.PLEOS_NATIVE_NAVIGATION && (!_state.value.vehicleContext.hasFreshLocation || !_state.value.vehicleContext.signalsReady)) {
            _state.value = _state.value.copy(autoPlanError = "차량 위치·속도·기어 정보를 확인한 뒤 순서를 계산합니다.")
            return
        }
        val attemptKey = "${session.sessionId}:${session.version}"
        if (autoPlanJob?.isActive == true) return
        if (!force && autoPlanAttemptKey == attemptKey) return
        autoPlanAttemptKey = attemptKey
        autoPlanJob = viewModelScope.launch {
            _state.value = _state.value.copy(autoPlanning = true, autoPlanError = null)
            try {
                val currentSession = _state.value.session?.takeIf { it.sessionId == session.sessionId } ?: session
                val withCurrentOrigin = syncCurrentVehicleOrigin(currentSession)
                val updated = MoagilApi(credentials.apiBaseUrl).plan(credentials, withCurrentOrigin)
                val latest = _state.value.session
                if (_state.value.credentials?.sessionId == session.sessionId && (latest == null || updated.version >= latest.version)) {
                    acceptSession(updated, null)
                }
            } catch (error: Exception) {
                val latest = _state.value.session
                if (latest?.sessionId == session.sessionId && latest.version == session.version) {
                    _state.value = _state.value.copy(autoPlanError = error.message ?: "추천 경로 계산에 실패했습니다.")
                }
            } finally {
                _state.value = _state.value.copy(autoPlanning = false)
            }
        }
    }

    private fun syncOriginWhenUseful(point: Point) {
        if (!BuildConfig.PLEOS_NATIVE_NAVIGATION || _state.value.busy || _state.value.autoPlanning) return
        val session = _state.value.session ?: return
        if (session.state !in setOf("COLLECTING", "PLANNED")) return
        if (!_state.value.vehicleContext.signalsReady || !_state.value.vehicleContext.hasFreshLocation) return
        if (session.startPoint.label == point.label && distanceMeters(session.startPoint, point) < 100.0) {
            if (session.passengers.isNotEmpty() && session.route == null) scheduleAutoPlan(session)
            return
        }
        if (originSyncJob?.isActive == true) return
        originSyncJob = viewModelScope.launch {
            val result = runCatching { syncCurrentVehicleOrigin(session) }
            originSyncJob = null
            result.onFailure { report(it) }
                .onSuccess { updated -> if (updated.passengers.isNotEmpty() && updated.route == null) scheduleAutoPlan(updated) }
        }
    }

    private suspend fun syncCurrentVehicleOrigin(session: DriverSession): DriverSession {
        if (!BuildConfig.PLEOS_NATIVE_NAVIGATION) return session
        val context = _state.value.vehicleContext
        check(context.signalsReady) { "차량 속도와 기어 상태를 확인할 수 없습니다. 연결과 권한을 확인해 주세요." }
        check(context.hasFreshLocation) { "현재 차량 위치가 오래됐습니다. 위치 업데이트를 기다려 주세요." }
        val point = checkNotNull(context.location) { "현재 차량 위치를 받지 못했습니다. 차량 위치 권한과 위치 서비스를 확인해 주세요." }
        if (session.startPoint.label == point.label && distanceMeters(session.startPoint, point) < 100.0) return session
        val credentials = requireCredentials()
        val updated = MoagilApi(credentials.apiBaseUrl).saveSettings(
            credentials = credentials,
            session = session,
            finalPoint = session.finalPoint,
            priorityId = session.priorityPassengerId,
            startPoint = point,
            routePreference = session.routePreference,
        )
        acceptSession(updated, null)
        return updated
    }

    private fun distanceMeters(a: Point, b: Point): Double {
        val earthRadius = 6_371_000.0
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val deltaLat = lat2 - lat1
        val deltaLon = Math.toRadians(b.lon - a.lon)
        val latTerm = kotlin.math.sin(deltaLat / 2).let { it * it }
        val lonTerm = kotlin.math.sin(deltaLon / 2).let { it * it }
        val h = latTerm + kotlin.math.cos(lat1) * kotlin.math.cos(lat2) * lonTerm
        return 2 * earthRadius * kotlin.math.asin(kotlin.math.sqrt(h.coerceIn(0.0, 1.0)))
    }

    private suspend fun launchBusy(action: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, errorMessage = null)
        try { action() } catch (error: Exception) { report(error) }
        finally {
            _state.value = _state.value.copy(busy = false)
            val session = _state.value.session
            if (BuildConfig.PLEOS_NATIVE_NAVIGATION) _state.value.vehicleContext.location?.let(::syncOriginWhenUseful)
            if (session != null && session.state in setOf("COLLECTING", "PLANNED") && session.passengers.isNotEmpty() && session.route == null) {
                scheduleAutoPlan(session)
            }
        }
    }

    private fun driverAction(action: suspend () -> Unit) = viewModelScope.launch { launchBusy(action) }

    override fun onCleared() {
        pollJob?.cancel()
        healthJob?.cancel()
        vehicleContextPort.close()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { navigation.close() }
        super.onCleared()
    }

    private fun requireSession() = checkNotNull(_state.value.session) { "이동 정보를 먼저 불러와 주세요." }
    private fun requireCredentials() = checkNotNull(_state.value.credentials) { "차량 인증 정보가 없습니다." }
    private fun ensureParked() {
        if (BuildConfig.PLEOS_NATIVE_NAVIGATION) {
            check(_state.value.vehicleContext.isParked) { "실제 차량의 정차와 P 기어가 확인돼야 조작할 수 있습니다." }
        } else {
            check(_state.value.simulatedParked) { "시연용 정차 상태를 먼저 확인해 주세요." }
        }
    }

    private fun report(error: Throwable) {
        val message = when (error) {
            is ApiException -> error.message
            is IllegalArgumentException, is IllegalStateException -> error.message ?: "입력을 확인해 주세요."
            else -> if (error is java.net.UnknownHostException || error is java.net.ConnectException) "서버에 연결할 수 없습니다. 주소와 같은 Wi-Fi 연결을 확인해 주세요." else "요청에 실패했습니다. 연결을 확인하고 다시 시도해 주세요."
        }
        _state.value = _state.value.copy(errorMessage = message)
    }
}
