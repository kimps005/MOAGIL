package kr.moagil.vehicle.navigation

import android.content.Context

fun createVehicleContextPort(context: Context): VehicleContextPort = MockVehicleContextPort()

private class MockVehicleContextPort : VehicleContextPort {
    override fun start(onUpdate: (VehicleContext) -> Unit) {
        onUpdate(VehicleContext(message = "시연 모드 · 실제 차량 정보 미연결"))
    }

    override fun close() = Unit
}
