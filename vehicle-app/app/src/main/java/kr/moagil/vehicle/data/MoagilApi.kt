package kr.moagil.vehicle.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(val status: Int, val code: String, override val message: String) : IOException(message)

class MoagilApi(private val baseUrl: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        runCatching { request("GET", "/api/health", null, null); true }.getOrDefault(false)
    }

    suspend fun createSession(demo: Boolean): Pair<DriverCredentials, DriverSession?> {
        val path = if (demo) "/api/sessions/demo" else "/api/sessions"
        val result = request("POST", path, JSONObject(), null)
        val credentials = DriverCredentials(
            apiBaseUrl = baseUrl,
            joinBaseUrl = result.optString("joinBaseUrl", baseUrl),
            sessionId = result.getString("sessionId"),
            driverToken = result.getString("driverToken"),
            inviteToken = result.getString("inviteToken"),
        )
        val session = result.optJSONObject("session")?.let(JsonModels::session)
        return credentials to session
    }

    suspend fun snapshot(credentials: DriverCredentials): DriverSession =
        JsonModels.session(request("GET", "/api/sessions/${credentials.sessionId}", null, credentials.driverToken))

    suspend fun saveSettings(
        credentials: DriverCredentials,
        session: DriverSession,
        finalPoint: Point?,
        priorityId: String?,
        startPoint: Point = session.startPoint,
    ): DriverSession {
        val body = JSONObject().apply {
            put("expectedVersion", session.version)
            put("startPoint", JsonModels.pointJson(startPoint))
            put("finalPoint", finalPoint?.let(JsonModels::pointJson) ?: JSONObject.NULL)
            put("priorityPassengerId", priorityId ?: JSONObject.NULL)
        }
        return mutation(credentials, "/api/sessions/${session.sessionId}/settings", body, "PUT")
    }

    suspend fun plan(credentials: DriverCredentials, session: DriverSession): DriverSession =
        mutation(credentials, "/api/sessions/${session.sessionId}/plan", expectedVersion(session), "POST")

    suspend fun selectOrder(credentials: DriverCredentials, session: DriverSession, order: List<String>): DriverSession =
        mutation(credentials, "/api/sessions/${session.sessionId}/select-order", JSONObject().apply {
            put("expectedVersion", session.version)
            put("order", JSONArray(order))
        }, "POST")

    suspend fun start(credentials: DriverCredentials, session: DriverSession): DriverSession =
        mutation(credentials, "/api/sessions/${session.sessionId}/start", expectedVersion(session), "POST")

    suspend fun completeStop(credentials: DriverCredentials, session: DriverSession, passengerId: String): DriverSession =
        mutation(credentials, "/api/sessions/${session.sessionId}/complete-stop", expectedVersion(session).put("passengerId", passengerId), "POST")

    suspend fun completeTrip(credentials: DriverCredentials, session: DriverSession): DriverSession =
        mutation(credentials, "/api/sessions/${session.sessionId}/complete-trip", expectedVersion(session), "POST")

    suspend fun cancel(credentials: DriverCredentials, session: DriverSession): DriverSession =
        mutation(credentials, "/api/sessions/${session.sessionId}/cancel", expectedVersion(session), "POST")

    suspend fun removePassenger(credentials: DriverCredentials, session: DriverSession, passengerId: String): DriverSession =
        JsonModels.session(request(
            "DELETE",
            "/api/sessions/${session.sessionId}/passengers/$passengerId",
            null,
            credentials.driverToken,
            mapOf("If-Match" to session.version.toString()),
        ))

    private suspend fun mutation(credentials: DriverCredentials, path: String, body: JSONObject, method: String): DriverSession =
        JsonModels.session(request(method, path, body, credentials.driverToken))

    private fun expectedVersion(session: DriverSession) = JSONObject().put("expectedVersion", session.version)

    private suspend fun request(
        method: String,
        path: String,
        body: JSONObject?,
        token: String?,
        headers: Map<String, String> = emptyMap(),
    ): JSONObject = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + path
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        if (token != null) builder.header("Authorization", "Bearer $token")
        headers.forEach { (name, value) -> builder.header(name, value) }
        if (method == "GET") builder.get()
        else if (method == "DELETE") builder.delete()
        else builder.method(method, (body ?: JSONObject()).toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val result = if (text.isBlank()) JSONObject() else runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (!response.isSuccessful) {
                val error = result.optJSONObject("error")
                throw ApiException(response.code, error?.optString("code", "REQUEST_FAILED") ?: "REQUEST_FAILED", error?.optString("message", "요청에 실패했습니다") ?: "요청에 실패했습니다")
            }
            result
        }
    }
}
