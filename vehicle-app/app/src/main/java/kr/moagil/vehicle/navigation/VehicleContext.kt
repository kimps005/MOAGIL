package kr.moagil.vehicle.navigation

import android.content.Context
import android.os.SystemClock
import kr.moagil.vehicle.data.Point

data class VehicleContext(
    val location: Point? = null,
    val locationUpdatedAtElapsedMs: Long = 0L,
    val speedMetersPerSecond: Float? = null,
    val gearSelection: Int? = null,
    val signalsReady: Boolean = false,
    val message: String = "차량 상태 연결 중",
) {
    val isParked: Boolean
        get() = signalsReady && speedMetersPerSecond != null &&
            kotlin.math.abs(speedMetersPerSecond) < 0.3f && gearSelection == 4

    val isMoving: Boolean
        get() = signalsReady && speedMetersPerSecond != null && kotlin.math.abs(speedMetersPerSecond) >= 0.3f

    val hasFreshLocation: Boolean
        get() = location != null && locationUpdatedAtElapsedMs > 0L && SystemClock.elapsedRealtime() - locationUpdatedAtElapsedMs <= 300_000L
}

interface VehicleContextPort {
    fun start(onUpdate: (VehicleContext) -> Unit)
    fun close()
}

fun createVehicleContextPort(context: Context): VehicleContextPort
