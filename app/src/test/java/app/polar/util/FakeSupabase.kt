package app.polar.util

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.minimalSettings
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.client.request.HttpRequestData
import kotlinx.coroutines.Dispatchers

// Test-only fake of the SupabaseClient's HTTP boundary, used by SyncManagerTest and
// AuthViewModelTest (agent-docs/analisis-implementacion-supabase-sync.md, hallazgo 6 / Fase
// 7.1 & 7.3: "tests de integracion de SyncManager con un SupabaseClient/Postgrest fake o mock").
// Rather than mocking the SupabaseClient/Postgrest/Auth DSL objects directly (they lean heavily
// on inline/reified extension functions that mockk can't intercept), this builds a *real*
// SupabaseClient — same createSupabaseClient(...) entry point production code uses — wired to a
// Ktor MockEngine, so Postgrest/Auth's actual request-building logic (query params, Prefer
// headers, upsert bodies) runs for real against canned HTTP responses instead of the network.

// Routes requests by (last path segment, HTTP method) — Postgrest always calls
// "{supabaseUrl}/rest/v1/{table}", so the last segment is the table name. A table/method with no
// registered route defaults to an empty JSON array (200 OK), which is the right no-op default
// for the pull() GETs a given test isn't exercising; push (POST/PATCH) calls a test cares about
// must be registered explicitly so a missing stub can't silently mask a bug in the test itself.
class FakePostgrestRouter {
    private val routes = mutableMapOf<Pair<String, HttpMethod>, Pair<HttpStatusCode, String>>()

    // Auth requests (gotrue) share the table name's last-path-segment shape too ("token",
    // "recover", "logout"), but "token" is used by both sign-in (grant_type=password) and
    // session-refresh (grant_type=refresh_token), so those need the query param disambiguated —
    // hence a separate map instead of overloading [on] for it.
    private val authTokenRoutes = mutableMapOf<String, Pair<HttpStatusCode, String>>()

    val requests = mutableListOf<HttpRequestData>()

    fun on(table: String, method: HttpMethod, body: String, status: HttpStatusCode = HttpStatusCode.OK) {
        routes[table to method] = status to body
    }

    // grantType is the `token?grant_type=...` query value (e.g. "password", "refresh_token").
    fun onAuthToken(grantType: String, body: String, status: HttpStatusCode = HttpStatusCode.OK) {
        authTokenRoutes[grantType] = status to body
    }

    // dispatcher = Unconfined so a viewModelScope.launch { ... } coroutine (Dispatchers.Main,
    // also Unconfined in tests via MainDispatcherRule) runs the whole request/response round trip
    // eagerly on the calling thread instead of hopping onto ktor's real background dispatcher —
    // otherwise a test's assertions would race a genuinely concurrent thread with no synchronous
    // way to wait for it (there is no Job to join: AuthViewModel's public methods aren't suspend).
    fun engine(): HttpClientEngine = MockEngine(MockEngineConfig().apply {
        dispatcher = Dispatchers.Unconfined
        addHandler { request ->
            requests += request
            val lastSegment = request.url.encodedPath.substringAfterLast('/')
            val grantType = request.url.parameters["grant_type"]
            val (status, body) = when {
                lastSegment == "token" && grantType != null -> authTokenRoutes[grantType]
                else -> routes[lastSegment to request.method]
            } ?: (HttpStatusCode.OK to "[]")
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
    })
}

// alwaysAutoRefresh/autoLoadFromStorage/autoSaveToStorage = false + an in-memory session manager
// (minimalSettings(), literally documented upstream for "server side applications, where you
// don't need to store the session") — avoids Auth touching Android SharedPreferences-backed
// storage, which isn't available in a plain JVM unit test.
fun fakeSupabaseClient(engine: HttpClientEngine): SupabaseClient = createSupabaseClient(
    supabaseUrl = "https://fake-test.supabase.co",
    supabaseKey = "fake-anon-key"
) {
    httpEngine = engine
    install(Auth) { minimalSettings() }
    install(Postgrest)
}

// Marks the client as authenticated as [userId] without any network call: importSession(...,
// autoRefresh = false) just assigns the session status directly (see AuthImpl.importSession) —
// exactly what a client that already has a valid, non-expired session does after a real sign-in.
suspend fun SupabaseClient.signInWithFakeSession(userId: String, email: String = "user@example.com") {
    auth.importSession(
        session = UserSession(
            accessToken = "fake-access-token",
            refreshToken = "fake-refresh-token",
            expiresIn = 3600L,
            tokenType = "bearer",
            user = UserInfo(aud = "authenticated", id = userId, email = email)
        ),
        autoRefresh = false
    )
}
