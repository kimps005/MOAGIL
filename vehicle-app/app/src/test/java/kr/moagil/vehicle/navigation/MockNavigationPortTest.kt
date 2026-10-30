package kr.moagil.vehicle.navigation

import kotlinx.coroutines.runBlocking
import kr.moagil.vehicle.data.Point
import org.junit.Assert.assertEquals
import org.junit.Test

class MockNavigationPortTest {
    @Test
    fun rejectedRequestCanBeRetriedSuccessfully() = runBlocking {
        val navigation = MockNavigationPort()
        val destination = Point(37.545, 126.97, "시연 지점 A")

        navigation.failNextRequest()
        assertEquals(
            NavigationResult.Rejected("시연용으로 안내 요청을 한 번 거부했습니다"),
            navigation.navigateTo(destination),
        )
        assertEquals(NavigationResult.Confirmed, navigation.navigateTo(destination))
    }
}
