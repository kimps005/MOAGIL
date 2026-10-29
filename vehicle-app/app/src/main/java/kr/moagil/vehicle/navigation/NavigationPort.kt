package kr.moagil.vehicle.navigation

import kotlinx.coroutines.delay
import kr.moagil.vehicle.data.Point

enum class DrivingState { PARKED, UNKNOWN }
sealed interface NavigationResult {
    data object Confirmed : NavigationResult
    data class Rejected(val reason: String) : NavigationResult
    data object Unavailable : NavigationResult
}

interface NavigationPort {
    suspend fun navigateTo(point: Point): NavigationResult
    suspend fun close()
}

interface DrivingStatePort { fun state(simulatedParked: Boolean): DrivingState }

class MockNavigationPort : NavigationPort {
    override suspend fun navigateTo(point: Point): NavigationResult {
        delay(500)
        return NavigationResult.Confirmed
    }
    override suspend fun close() = Unit
}

class MockDrivingStatePort : DrivingStatePort {
    override fun state(simulatedParked: Boolean) = if (simulatedParked) DrivingState.PARKED else DrivingState.UNKNOWN
}
