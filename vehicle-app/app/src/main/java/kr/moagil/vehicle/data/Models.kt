package kr.moagil.vehicle.data

import org.json.JSONArray
import org.json.JSONObject

data class Point(val lat: Double, val lon: Double, val label: String)
data class Passenger(val id: String, val displayName: String, val point: Point)
data class RouteSnapshot(
    val order: List<String>,
    val durationMs: Long,
    val distanceM: Long?,
    val evaluatedCount: Int,
    val dataSource: String,
    val fetchedAt: String,
    val note: String,
)
data class DriverSession(
    val sessionId: String,
    val state: String,
    val version: Int,
    val expiresAt: String,
    val startPoint: Point,
    val finalPoint: Point?,
    val priorityPassengerId: String?,
    val passengers: List<Passenger>,
    val selectedOrder: List<String>,
    val activeIndex: Int,
    val route: RouteSnapshot?,
)
data class DriverCredentials(
    val apiBaseUrl: String,
    val joinBaseUrl: String,
    val sessionId: String,
    val driverToken: String,
    val inviteToken: String,
)

object JsonModels {
    fun point(json: JSONObject): Point = Point(
        lat = json.getDouble("lat"),
        lon = json.getDouble("lon"),
        label = json.optString("label", "시연 지점"),
    )

    fun session(json: JSONObject): DriverSession {
        val people = json.optJSONArray("passengers") ?: JSONArray()
        val passengers = (0 until people.length()).mapNotNull { index ->
            people.optJSONObject(index)?.let {
                Passenger(it.getString("id"), it.getString("displayName"), point(it.getJSONObject("point")))
            }
        }
        val selected = json.optJSONArray("selectedOrder") ?: JSONArray()
        val routeJson = json.optJSONObject("routeSnapshot")
        val route = routeJson?.let {
            RouteSnapshot(
                order = strings(it.optJSONArray("order") ?: JSONArray()),
                durationMs = it.optLong("durationMs"),
                distanceM = if (it.isNull("distanceM") || !it.has("distanceM")) null else it.optLong("distanceM"),
                evaluatedCount = it.optInt("evaluatedCount", 0),
                dataSource = it.optString("dataSource", "unknown"),
                fetchedAt = it.optString("fetchedAt", ""),
                note = it.optString("note", "모의 경로"),
            )
        }
        return DriverSession(
            sessionId = json.getString("sessionId"),
            state = json.getString("state"),
            version = json.getInt("version"),
            expiresAt = json.optString("expiresAt", ""),
            startPoint = point(json.getJSONObject("startPoint")),
            finalPoint = json.optJSONObject("finalPoint")?.let(::point),
            priorityPassengerId = json.optString("priorityPassengerId").takeIf { it.isNotBlank() && it != "null" },
            passengers = passengers,
            selectedOrder = strings(selected),
            activeIndex = json.optInt("activeIndex", 0),
            route = route,
        )
    }

    fun strings(json: JSONArray): List<String> = (0 until json.length()).mapNotNull { json.optString(it).takeIf(String::isNotBlank) }

    fun pointJson(point: Point) = JSONObject().apply {
        put("lat", point.lat)
        put("lon", point.lon)
        put("label", point.label)
    }
}
