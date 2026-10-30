package kr.moagil.vehicle.navigation

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.VehicleGear
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.moagil.vehicle.data.Point
import java.util.concurrent.atomic.AtomicBoolean

fun createVehicleContextPort(context: Context): VehicleContextPort = PleosVehicleContextPort(context)

private class PleosVehicleContextPort(context: Context) : VehicleContextPort {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val closed = AtomicBoolean(false)
    private var onUpdate: (VehicleContext) -> Unit = {}
    private var current = VehicleContext()
    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null
    private var pollJob: Job? = null
    private var fusedLocation: FusedLocation? = null
    private var locationListener: FusedLocationListener? = null

    override fun start(onUpdate: (VehicleContext) -> Unit) {
        this.onUpdate = onUpdate
        publish(current.copy(message = "차량 위치·속도·기어 연결 중"))
        connectVehicleSignals()
        connectVehicleLocation()
    }

    private fun connectVehicleSignals() {
        try {
            car = Car.createCar(
                appContext,
                mainHandler,
                Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT,
                Car.CarServiceLifecycleListener { connectedCar, ready ->
                    if (closed.get()) return@CarServiceLifecycleListener
                    if (!ready) {
                        propertyManager = null
                        publish(current.copy(signalsReady = false, speedMetersPerSecond = null, gearSelection = null, message = "차량 상태 서비스 연결을 기다리는 중"))
                        return@CarServiceLifecycleListener
                    }
                    propertyManager = connectedCar.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                    if (propertyManager == null) {
                        publish(current.copy(signalsReady = false, message = "차량 상태 서비스를 사용할 수 없습니다"))
                    } else if (pollJob?.isActive != true) {
                        pollJob = scope.launch {
                            while (isActive && !closed.get()) {
                                refreshVehicleSignals()
                                delay(1_000)
                            }
                        }
                    }
                },
            )
        } catch (error: Exception) {
            publish(current.copy(signalsReady = false, message = "차량 속도·기어 연결 실패: ${error.message ?: "권한 또는 서비스 확인 필요"}"))
        }
    }

    private suspend fun refreshVehicleSignals() = withContext(Dispatchers.IO) {
        val manager = propertyManager ?: return@withContext
        try {
            val speed = manager.getProperty(Float::class.javaObjectType, VehiclePropertyIds.PERF_VEHICLE_SPEED, 0)?.value
            val gear = manager.getProperty(Int::class.javaObjectType, VehiclePropertyIds.GEAR_SELECTION, 0)?.value
            if (speed == null || gear == null) {
                publish(current.copy(signalsReady = false, speedMetersPerSecond = null, gearSelection = null, message = "차량 속도·기어 정보를 아직 받지 못했습니다"))
                return@withContext
            }
            val speedKmh = kotlin.math.abs(speed * 3.6f)
            val gearLabel = when (gear) {
                VehicleGear.GEAR_PARK -> "P"
                VehicleGear.GEAR_REVERSE -> "R"
                VehicleGear.GEAR_NEUTRAL -> "N"
                VehicleGear.GEAR_DRIVE -> "D"
                else -> "기어 $gear"
            }
            val message = when {
                kotlin.math.abs(speed) >= 0.3f -> "주행 중 · ${"%.0f".format(java.util.Locale.US, speedKmh)} km/h · $gearLabel"
                gear == VehicleGear.GEAR_PARK -> "정차 · P · 0 km/h"
                else -> "정차 감지 · $gearLabel · P에 두면 조작할 수 있습니다"
            }
            publish(current.copy(speedMetersPerSecond = speed, gearSelection = gear, signalsReady = true, message = message))
        } catch (error: SecurityException) {
            publish(current.copy(signalsReady = false, speedMetersPerSecond = null, gearSelection = null, message = "차량 속도·기어 권한이 없습니다"))
        } catch (error: Exception) {
            publish(current.copy(signalsReady = false, speedMetersPerSecond = null, gearSelection = null, message = "차량 상태를 읽지 못했습니다: ${error.message ?: "다시 연결해 주세요"}"))
        }
    }

    private fun connectVehicleLocation() {
        try {
            val location = FusedLocation(appContext)
            if (!location.isSupported()) {
                publish(current.copy(location = null, message = "차량 위치 서비스를 지원하지 않습니다"))
                return
            }
            location.initialize()
            val listener = object : FusedLocationListener {
                override fun onChanged(locationData: LocationData?) {
                    if (closed.get()) return
                    val point = locationData?.let(::toPoint)
                    if (point == null) {
                        publish(current.copy(location = null, locationUpdatedAtElapsedMs = 0L, message = "차량 위치를 확인하고 있습니다"))
                    } else {
                        publish(current.copy(location = point, locationUpdatedAtElapsedMs = SystemClock.elapsedRealtime()))
                    }
                }

                override fun onError(exception: Exception) {
                    if (!closed.get()) publish(current.copy(location = null, locationUpdatedAtElapsedMs = 0L, message = "차량 위치를 읽지 못했습니다: ${exception.message ?: "위치 권한을 확인해 주세요"}"))
                }
            }
            fusedLocation = location
            locationListener = listener
            location.registerFusedLocationCallback(listener)
        } catch (error: SecurityException) {
            publish(current.copy(location = null, message = "차량 위치 권한이 없습니다"))
        } catch (error: Exception) {
            publish(current.copy(location = null, message = "차량 위치 연결 실패: ${error.message ?: "다시 연결해 주세요"}"))
        }
    }

    private fun toPoint(location: LocationData): Point? {
        val latitude = location.latitude
        val longitude = location.longitude
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        if (latitude == 0.0 && longitude == 0.0) return null
        return Point(latitude, longitude, "현재 차량 위치")
    }

    @Synchronized
    private fun publish(updated: VehicleContext) {
        if (closed.get()) return
        current = updated
        onUpdate(updated)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        pollJob?.cancel()
        scope.cancel()
        val location = fusedLocation
        val listener = locationListener
        if (location != null && listener != null) {
            runCatching { location.unregisterFusedLocationCallback(listener) }
            runCatching { location.release() }
        }
        fusedLocation = null
        locationListener = null
        propertyManager = null
        car?.disconnect()
        car = null
    }
}
