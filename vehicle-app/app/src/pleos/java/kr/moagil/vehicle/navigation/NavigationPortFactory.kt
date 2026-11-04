package kr.moagil.vehicle.navigation

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import kr.moagil.vehicle.data.Point

fun createNavigationPort(context: Context): NavigationPort = PleosNavigationPort(context)

private class PleosNavigationPort(context: Context) : NavigationPort {
    companion object {
        private const val TAG = "MOAGIL_PLEOS_ROUTE"
        private const val CONNECTION_TIMEOUT_MS = 15_000L
        private const val ROUTE_RESPONSE_TIMEOUT_MS = 30_000L
    }

    private val appContext = context.applicationContext
    private val routeLock = Mutex()
    private val pendingResult = AtomicReference<PendingRoute?>(null)
    private var connection: Connection? = null
    private var connectionFailure: String? = null

    private data class Connection(
        val helper: NaviHelper,
        val listener: NaviHelperEventListener,
    )

    private data class PendingRoute(
        val result: CompletableDeferred<NavigationResult>,
        val startedAtElapsedMs: Long,
        val expectedDestinations: Set<String>,
        val routeStarted: AtomicBoolean = AtomicBoolean(false),
    ) {
        fun elapsedMs(): Long = (SystemClock.elapsedRealtime() - startedAtElapsedMs).coerceAtLeast(0)
    }

    override suspend fun navigateTo(point: Point): NavigationResult = navigateRoute(point, emptyList())

    override suspend fun showNavigationScreen() = routeLock.withLock {
        val connected = connect()
            ?: throw IllegalStateException(connectionFailure ?: "Pleos NaviHelper 서비스에 연결할 수 없습니다.")
        withContext(Dispatchers.Main.immediate) {
            connected.helper.requestNavigationScreen(NavigationScreenRequest.Foreground)
        }
    }

    override suspend fun navigateRoute(destination: Point, waypoints: List<Point>): NavigationResult {
        require(waypoints.size <= 2) { "Pleos 내비 경유지는 한 구간에 최대 2개까지 보낼 수 있습니다." }
        val startedAtElapsedMs = SystemClock.elapsedRealtime()
        return routeLock.withLock {
            val pending = PendingRoute(
                CompletableDeferred(),
                startedAtElapsedMs,
                (waypoints.map(Point::label) + destination.label).toSet(),
            )
            pendingResult.set(pending)
            try {
                val connected = connect()
                    ?: return@withLock NavigationResult.Rejected(
                        connectionFailure ?: "Pleos NaviHelper 서비스에 연결할 수 없습니다.",
                        pending.elapsedMs(),
                    )
                withContext(Dispatchers.Main.immediate) {
                    val destinationInfo = destination.toRouteInfo()
                    if (waypoints.isEmpty()) {
                        connected.helper.requestRoute(destinationInfo)
                    } else {
                        connected.helper.requestRouteWithWaypoints(
                            RouteWithWaypointsRequest(
                                destination = destinationInfo,
                                waypoints = waypoints.mapIndexed { index, point ->
                                    RequestWaypointInfo(
                                        latitude = point.lat,
                                        longitude = point.lon,
                                        poiName = point.label,
                                        waypointIndex = if (index == 0) WaypointIndex.FIRST else WaypointIndex.SECOND,
                                        poiId = "",
                                        address = point.label,
                                        poiSubId = "0",
                                    )
                                },
                            ),
                        )
                    }
                    connected.helper.requestNavigationScreen(NavigationScreenRequest.Foreground)
                }
                withTimeoutOrNull(ROUTE_RESPONSE_TIMEOUT_MS) { pending.result.await() }
                    ?: NavigationResult.Rejected(
                        "Pleos 내비에서 30초 동안 경로 응답이 없습니다. 연결 상태를 확인하고 다시 요청해 주세요.",
                        pending.elapsedMs(),
                    )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: SecurityException) {
                NavigationResult.Rejected(
                    "NAVI_ROUTE_SEARCH 권한을 확인해 주세요: ${error.message ?: "권한 오류"}",
                    pending.elapsedMs(),
                )
            } catch (error: Exception) {
                NavigationResult.Rejected(
                    "NaviHelper 요청 오류: ${error.message ?: error.javaClass.simpleName}",
                    pending.elapsedMs(),
                )
            } finally {
                pendingResult.compareAndSet(pending, null)
            }
        }
    }

    private fun Point.toRouteInfo() = RouteInfo(
        latitude = lat,
        longitude = lon,
        poiName = label,
        poiId = "",
        address = label,
        poiSubId = "0",
        routeOption = RouteOption.RECOMMENDED,
    )

    private suspend fun connect(): Connection? {
        connection?.let { return it }

        val ready = CompletableDeferred<Result<Unit>>()
        val helper = NaviHelper(appContext)
        val listener = object : NaviHelperEventListener {
            override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
                Log.i(TAG, "Pleos started route search: $routeStartInfo")
                pendingResult.get()?.routeStarted?.set(true)
            }

            override fun onRoutingCompleted() {
                Log.i(TAG, "Pleos completed route search")
                pendingResult.get()?.let { pending ->
                    pending.result.complete(
                        NavigationResult.Completed(
                            pending.elapsedMs(),
                        ),
                    )
                }
            }

            override fun onError(errorCode: NaviErrorCode) {
                Log.e(TAG, "Pleos route search error: $errorCode")
                pendingResult.get()?.let { pending ->
                    pending.result.complete(NavigationResult.Rejected("내비 오류 $errorCode", pending.elapsedMs()))
                }
            }

            override fun onRouteCancelled() {
                val pending = pendingResult.get() ?: return
                Log.w(TAG, "Pleos route cancelled after start=${pending.routeStarted.get()}")
                if (pending.routeStarted.get()) {
                    pending.result.complete(NavigationResult.Rejected("Pleos 내비가 경로 탐색을 취소했습니다.", pending.elapsedMs()))
                }
            }

            override fun onDrivingInfo(drivingInfo: DrivingInfo) {
                val pending = pendingResult.get() ?: return
                val destination = drivingInfo.destination
                val matchedDestination = pending.expectedDestinations.any { it.equals(destination.name, ignoreCase = true) }
                if (pending.routeStarted.get() && matchedDestination) {
                    Log.i(TAG, "Pleos confirmed requested destination via onDrivingInfo: ${destination.name}, ${destination.distance}m, ${destination.duration}s")
                    pending.result.complete(
                        NavigationResult.DestinationConfirmed(
                            pending.elapsedMs(),
                            destination.distance,
                            destination.duration,
                        ),
                    )
                }
            }
        }
        helper.initialize(
            onConnected = {
                helper.addListener(listener)
                ready.complete(Result.success(Unit))
            },
            onFailed = { error -> ready.complete(Result.failure(error)) },
        )

        val initialized = withTimeoutOrNull(CONNECTION_TIMEOUT_MS) { ready.await() }
        if (initialized == null) {
            connectionFailure = "Pleos 내비 서비스가 15초 안에 연결 응답을 보내지 않았습니다. 내비 연결 상태를 확인해 주세요."
            withContext(Dispatchers.Main.immediate) { helper.release() }
            return null
        }
        if (initialized.isSuccess) {
            connection = Connection(helper, listener)
            connectionFailure = null
            return connection
        }

        connectionFailure = initialized.exceptionOrNull()?.message ?: "Pleos Navigation 서비스 연결에 실패했습니다."
        withContext(Dispatchers.Main.immediate) { helper.release() }
        return null
    }

    override suspend fun close() = routeLock.withLock {
        pendingResult.getAndSet(null)?.result?.complete(NavigationResult.Unavailable)
        val current = connection ?: return@withLock
        connection = null
        withContext(Dispatchers.Main.immediate) {
            current.helper.removeListener(current.listener)
            current.helper.release()
        }
    }

}
