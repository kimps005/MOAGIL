package kr.moagil.vehicle.navigation

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.DrivingInfo
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
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import kr.moagil.vehicle.data.Point

fun createNavigationPort(context: Context): NavigationPort = PleosNavigationPort(context)

private class PleosNavigationPort(context: Context) : NavigationPort {
    companion object {
        private const val TAG = "MOAGIL_PLEOS_ROUTE"
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
        val expectedDestination: String,
        val routeStarted: AtomicBoolean = AtomicBoolean(false),
    ) {
        fun elapsedMs(): Long = (SystemClock.elapsedRealtime() - startedAtElapsedMs).coerceAtLeast(0)
    }

    override suspend fun navigateTo(point: Point): NavigationResult {
        val startedAtElapsedMs = SystemClock.elapsedRealtime()
        return routeLock.withLock {
            val pending = PendingRoute(CompletableDeferred(), startedAtElapsedMs, point.label)
            pendingResult.set(pending)
            try {
                val connected = connect()
                    ?: return@withLock NavigationResult.Rejected(
                        connectionFailure ?: "Pleos NaviHelper 서비스에 연결할 수 없습니다.",
                        pending.elapsedMs(),
                    )
                withContext(Dispatchers.Main.immediate) {
                    connected.helper.requestRoute(
                        RouteInfo(
                            latitude = point.lat,
                            longitude = point.lon,
                            poiName = point.label,
                            poiId = "",
                            address = point.label,
                            poiSubId = "0",
                            routeOption = RouteOption.RECOMMENDED,
                        ),
                    )
                }
                pending.result.await()
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
                    pending.result.complete(NavigationResult.Completed(pending.elapsedMs()))
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
                if (pending.routeStarted.get() && destination.name.equals(pending.expectedDestination, ignoreCase = true)) {
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

        val initialized = ready.await()
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
