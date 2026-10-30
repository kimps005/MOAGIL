package kr.moagil.vehicle.navigation

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference
import kr.moagil.vehicle.data.Point

fun createNavigationPort(context: Context): NavigationPort = PleosNavigationPort(context)

private class PleosNavigationPort(context: Context) : NavigationPort {
    private val appContext = context.applicationContext
    private val routeLock = Mutex()
    private val pendingResult = AtomicReference<CompletableDeferred<NavigationResult>?>(null)
    private var connection: Connection? = null
    private var connectionFailure: String? = null

    private data class Connection(
        val helper: NaviHelper,
        val listener: NaviHelperEventListener,
    )

    override suspend fun navigateTo(point: Point): NavigationResult {
        val deadlineNanos = System.nanoTime() + ROUTE_TIMEOUT_MS * 1_000_000
        return withTimeoutOrNull(ROUTE_TIMEOUT_MS) {
            routeLock.withLock {
                val connected = connect(deadlineNanos)
                    ?: return@withLock NavigationResult.Rejected(connectionFailure ?: "Pleos NaviHelper 서비스에 연결할 수 없습니다.")
                val result = CompletableDeferred<NavigationResult>()
                pendingResult.set(result)
                try {
                    withContext(Dispatchers.Main.immediate) {
                        connected.helper.requestRoute(
                            RouteInfo(
                                latitude = point.lat,
                                longitude = point.lon,
                                poiName = point.label,
                                poiId = "",
                                address = point.label,
                                poiSubId = "",
                                routeOption = RouteOption.RECOMMENDED,
                            ),
                        )
                    }
                    withTimeoutOrNull(remainingMillis(deadlineNanos)) { result.await() }
                        ?: NavigationResult.Rejected("5초 안에 Pleos 내비 경로 결과 콜백을 받지 못했습니다.")
                } catch (error: SecurityException) {
                    NavigationResult.Rejected("NAVI_ROUTE_SEARCH 권한을 확인해 주세요: ${error.message ?: "권한 오류"}")
                } catch (error: Exception) {
                    NavigationResult.Rejected("NaviHelper 요청 오류: ${error.message ?: error.javaClass.simpleName}")
                } finally {
                    pendingResult.compareAndSet(result, null)
                }
            }
        } ?: NavigationResult.Rejected("Pleos 내비 응답이 5초 제한을 넘었습니다. 잠시 뒤 다시 시도해 주세요.")
    }

    private suspend fun connect(deadlineNanos: Long): Connection? {
        connection?.let { return it }

        val ready = CompletableDeferred<Result<Unit>>()
        val helper = NaviHelper(appContext)
        val listener = object : NaviHelperEventListener {
            override fun onRouteStarted(routeStartInfo: RouteStartInfo) = Unit

            override fun onRoutingCompleted() {
                pendingResult.get()?.complete(NavigationResult.Confirmed)
            }

            override fun onError(errorCode: NaviErrorCode) {
                pendingResult.get()?.complete(NavigationResult.Rejected("내비 오류 $errorCode"))
            }
        }
        helper.initialize(
            onConnected = {
                helper.addListener(listener)
                ready.complete(Result.success(Unit))
            },
            onFailed = { error -> ready.complete(Result.failure(error)) },
        )

        val initialized = withTimeoutOrNull(remainingMillis(deadlineNanos)) { ready.await() }
        if (initialized?.isSuccess == true) {
            connection = Connection(helper, listener)
            connectionFailure = null
            return connection
        }

        connectionFailure = initialized?.exceptionOrNull()?.message
            ?: "5초 안에 Pleos Navigation 서비스 연결을 확인하지 못했습니다."
        withContext(Dispatchers.Main.immediate) { helper.release() }
        return null
    }

    override suspend fun close() = routeLock.withLock {
        pendingResult.getAndSet(null)?.complete(NavigationResult.Unavailable)
        val current = connection ?: return@withLock
        connection = null
        withContext(Dispatchers.Main.immediate) {
            current.helper.removeListener(current.listener)
            current.helper.release()
        }
    }

    private fun remainingMillis(deadlineNanos: Long): Long =
        ((deadlineNanos - System.nanoTime()) / 1_000_000).coerceAtLeast(1)

    private companion object {
        const val ROUTE_TIMEOUT_MS = 5_000L
    }
}
