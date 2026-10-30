package com.perfumerlab.scentstudio.acceptance.harness

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 가짜 사용자 데이터 서버. 앱의 OkHttp에 인터셉터로 끼워, 실제 네트워크 없이 대답한다.
 * 경로와 JSON 모양은 docs/design.md 11절 API를 따른다 (4단계 Spring 서버도 같은 모양).
 *
 * - offline = true  : 인터넷이 끊긴 것처럼 모든 요청이 IOException
 * - slowMillis > 0  : 서버가 잠들어 있다 깨는 것처럼 대답이 늦음
 */
class FakeUserServer : Interceptor {

    companion object {
        const val TEST_USER_ID = "user-1"
        const val TEST_TOKEN = "test-token"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    @Volatile var offline = false
    @Volatile var slowMillis = 0L

    // 서버에 저장된 것 (테스트가 미리 넣거나 확인한다)
    @Volatile var dislikedNoteKeys: List<String> = emptyList()
    @Volatile var preferredFamilies: List<String> = emptyList()
    val blends = CopyOnWriteArrayList<JsonObject>()
    val favorites = CopyOnWriteArrayList<String>()
    val feedback = CopyOnWriteArrayList<JsonObject>()
    /** 받은 사용 기록 이름 (도착 순서) */
    val receivedEvents = CopyOnWriteArrayList<String>()
    @Volatile var accountDeleted = false
    /** 받은 요청 "METHOD /경로" (도착 순서) */
    val requests = CopyOnWriteArrayList<String>()

    private val json = Json { ignoreUnknownKeys = true }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (offline) throw IOException("테스트: 인터넷 끊김")
        if (slowMillis > 0) Thread.sleep(slowMillis)

        val method = request.method
        val path = request.url.encodedPath.substringAfter("/api/v1")
        requests += "$method $path"
        val body = request.body?.let { Buffer().also(it::writeTo).readUtf8() }
        val authorized = request.header("Authorization") == "Bearer $TEST_TOKEN"

        fun respond(code: Int, payload: JsonElement? = null): Response =
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code < 400) "OK" else "Error")
                .body((payload?.toString() ?: "").toResponseBody(JSON))
                .build()

        fun error(code: Int, errorCode: String) = respond(code, buildJsonObject {
            put("error", buildJsonObject { put("code", errorCode); put("message", errorCode) })
        })

        // 로그인 없이 되는 것
        when {
            method == "GET" && path == "/catalog/latest" ->
                return error(404, "NO_NEWER_CATALOG") // 앱에 넣은 카탈로그를 그대로 쓰게 함
            method == "POST" && path == "/events" -> {
                val events = json.parseToJsonElement(body!!).jsonObject["events"]!!.jsonArray
                events.forEach { receivedEvents += it.jsonObject["name"]!!.jsonPrimitive.content }
                return respond(202)
            }
        }

        if (!authorized) return error(401, "UNAUTHORIZED")

        return when {
            method == "GET" && path == "/me" ->
                respond(200, buildJsonObject { put("userId", TEST_USER_ID) })
            method == "DELETE" && path == "/me" -> {
                accountDeleted = true
                blends.clear(); favorites.clear(); feedback.clear()
                dislikedNoteKeys = emptyList(); preferredFamilies = emptyList()
                respond(204)
            }
            method == "GET" && path == "/me/preferences" -> respond(200, preferencesJson())
            method == "PUT" && path == "/me/preferences" -> {
                val obj = json.parseToJsonElement(body!!).jsonObject
                dislikedNoteKeys = obj["dislikedNoteKeys"]!!.jsonArray.map { it.jsonPrimitive.content }
                preferredFamilies = obj["preferredFamilies"]!!.jsonArray.map { it.jsonPrimitive.content }
                respond(200, preferencesJson())
            }
            method == "GET" && path == "/blends" -> respond(200, JsonArray(blends.toList()))
            method == "POST" && path == "/blends" -> {
                val obj = json.parseToJsonElement(body!!).jsonObject
                val saved = JsonObject(obj + ("id" to JsonPrimitive(UUID.randomUUID().toString())))
                blends += saved
                respond(201, saved)
            }
            method == "DELETE" && path.startsWith("/blends/") -> {
                val id = path.removePrefix("/blends/")
                blends.removeIf { it["id"]!!.jsonPrimitive.content == id }
                respond(204)
            }
            method == "GET" && path == "/favorites" -> respond(200, JsonArray(favorites.map {
                buildJsonObject { put("perfumeKey", it) }
            }))
            method == "PUT" && path.startsWith("/favorites/") -> {
                val key = path.removePrefix("/favorites/")
                if (key !in favorites) favorites += key
                respond(204)
            }
            method == "DELETE" && path.startsWith("/favorites/") -> {
                favorites.remove(path.removePrefix("/favorites/"))
                respond(204)
            }
            method == "GET" && path == "/feedback" -> respond(200, JsonArray(feedback.toList()))
            method == "POST" && path == "/feedback" -> {
                val obj = json.parseToJsonElement(body!!).jsonObject
                val saved = JsonObject(obj + ("id" to JsonPrimitive(UUID.randomUUID().toString())))
                feedback += saved
                respond(201, saved)
            }
            else -> error(404, "NOT_FOUND")
        }
    }

    private fun preferencesJson() = buildJsonObject {
        put("dislikedNoteKeys", JsonArray(dislikedNoteKeys.map(::JsonPrimitive)))
        put("preferredFamilies", JsonArray(preferredFamilies.map(::JsonPrimitive)))
    }
}
