package app.polar.ui.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.work.WorkManager
import app.polar.R
import app.polar.data.sync.SyncManager
import app.polar.data.sync.SyncPrefs
import app.polar.util.FakePostgrestRouter
import app.polar.util.MainDispatcherRule
import app.polar.util.fakeSupabaseClient
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.ktor.http.HttpMethod
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

// Tests AuthViewModel (agent-docs/analisis-implementacion-supabase-sync.md, hallazgo 6 / Fase
// 7.3). Sign-up is intentionally NOT covered: Fase 2 (see the roadmap doc, table row 1.1)
// deliberately decided against exposing account creation from Polar — AuthViewModel only ever
// exposes signIn()/signOut()/resetPassword(), so there is no signUp() to test.
//
// Two different SupabaseClient setups are used depending on what each group of tests needs:
//  - signIn/signOut/resetPassword: a *real* SupabaseClient wired to a Ktor MockEngine (see
//    FakeSupabase.kt) so Auth's actual request-building (grant_type, body) is exercised.
//  - sessionStatus reactivity (Fase 2.3 / hallazgo 4.11): a mocked SupabaseClient/Auth exposing a
//    plain MutableStateFlow<SessionStatus> the test can push arbitrary transitions (in particular
//    RefreshFailure) into directly — driving that state through Auth's *real* internal retry/
//    refresh machinery would mean real multi-second wall-clock delays outside the test's virtual
//    time scheduler (Auth's own background scope, not the test's), which is exactly the kind of
//    flaky/slow test this fase's own instructions (see roadmap section 11, point 9) want avoided.
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val userId = "9c1e2b0d-0000-4a1a-9a1a-000000000009"
    private val application = mockk<Application>(relaxed = true)
    private val syncManager = mockk<SyncManager>()
    private val syncPrefs = mockk<SyncPrefs>(relaxed = true)

    @Before
    fun stubWorkManager() {
        // AuthViewModel.signIn()/confirmMergeUpload() call SyncWorker.triggerImmediateSync(),
        // which reaches the real WorkManager.getInstance() — unavailable in a plain JVM unit test
        // (same stub TaskViewModelTest already needs).
        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns mockk(relaxed = true)
    }

    @After
    fun unstubWorkManager() {
        unmockkStatic(WorkManager::class)
    }

    // --- signIn / signOut / resetPassword: real Auth against a fake HTTP boundary ---

    private fun sessionJson(id: String = userId) = """
        {"access_token":"fake-token","refresh_token":"fake-refresh","expires_in":3600,"token_type":"bearer",
         "user":{"aud":"authenticated","id":"$id","email":"user@example.com"}}
    """.trimIndent()

    @Test
    fun `signIn with a blank password sets a validation error without contacting Supabase`() = runTest {
        val router = FakePostgrestRouter()
        val client = fakeSupabaseClient(router.engine())
        val viewModel = AuthViewModel(application, client, syncManager, syncPrefs)

        viewModel.signIn("user@example.com", "")

        assertNotNull(viewModel.errorMessage.value)
        assertTrue(router.requests.isEmpty())
    }

    @Test
    fun `signIn success with no local data triggers a normal sync, not the merge dialog`() = runTest {
        val router = FakePostgrestRouter()
        router.onAuthToken("password", sessionJson())
        val client = fakeSupabaseClient(router.engine())
        coEvery { syncPrefs.lastSyncAt } returns 0L
        coEvery { syncManager.hasLocalData() } returns false
        val viewModel = AuthViewModel(application, client, syncManager, syncPrefs)

        viewModel.signIn("user@example.com", "hunter2")

        assertFalse(viewModel.pendingMergeDecision.value)
        assertNotNull(viewModel.infoMessage.value)
        assertEquals(userId, client.auth.currentUserOrNull()?.id)
    }

    @Test
    fun `signIn success with existing local data and a first-ever sync asks to merge or discard`() = runTest {
        val router = FakePostgrestRouter()
        router.onAuthToken("password", sessionJson())
        val client = fakeSupabaseClient(router.engine())
        coEvery { syncPrefs.lastSyncAt } returns 0L
        coEvery { syncManager.hasLocalData() } returns true
        val viewModel = AuthViewModel(application, client, syncManager, syncPrefs)

        viewModel.signIn("user@example.com", "hunter2")

        assertTrue(viewModel.pendingMergeDecision.value)
    }

    @Test
    fun `signIn success when this device already synced before never asks to merge, even with local data`() = runTest {
        val router = FakePostgrestRouter()
        router.onAuthToken("password", sessionJson())
        val client = fakeSupabaseClient(router.engine())
        coEvery { syncPrefs.lastSyncAt } returns 123456789L
        val viewModel = AuthViewModel(application, client, syncManager, syncPrefs)

        viewModel.signIn("user@example.com", "hunter2")

        assertFalse(viewModel.pendingMergeDecision.value)
    }

    @Test
    fun `confirmDiscardLocalUseCloud delegates to SyncManager and reports success`() = runTest {
        val router = FakePostgrestRouter()
        val client = fakeSupabaseClient(router.engine())
        coEvery { syncManager.discardLocalAndPullFromCloud() } returns Result.success(Unit)
        val viewModel = AuthViewModel(application, client, syncManager, syncPrefs)

        viewModel.confirmDiscardLocalUseCloud()

        assertFalse(viewModel.pendingMergeDecision.value)
        assertNotNull(viewModel.infoMessage.value)
        assertNull(viewModel.errorMessage.value)
    }

    @Test
    fun `signOut clears the current session`() = runTest {
        val router = FakePostgrestRouter()
        router.onAuthToken("password", sessionJson())
        router.on("logout", HttpMethod.Post, "{}")
        val client = fakeSupabaseClient(router.engine())
        client.auth.signInWith(io.github.jan.supabase.auth.providers.builtin.Email) {
            email = "user@example.com"
            password = "hunter2"
        }
        val viewModel = AuthViewModel(application, client, syncManager, syncPrefs)
        assertNotNull(viewModel.currentUserEmail)

        viewModel.signOut()

        assertNull(client.auth.currentUserOrNull())
    }

    @Test
    fun `resetPassword with a blank email sets a validation error without contacting Supabase`() = runTest {
        val router = FakePostgrestRouter()
        val client = fakeSupabaseClient(router.engine())
        val viewModel = AuthViewModel(application, client, syncManager, syncPrefs)

        viewModel.resetPassword("")

        assertNotNull(viewModel.errorMessage.value)
        assertTrue(router.requests.isEmpty())
    }

    @Test
    fun `resetPassword with a real email posts to Supabase and confirms it was sent`() = runTest {
        val router = FakePostgrestRouter()
        router.on("recover", HttpMethod.Post, "{}")
        val client = fakeSupabaseClient(router.engine())
        val viewModel = AuthViewModel(application, client, syncManager, syncPrefs)

        viewModel.resetPassword("user@example.com")

        assertNotNull(viewModel.infoMessage.value)
        assertTrue(router.requests.any { it.method == HttpMethod.Post && it.url.encodedPath.endsWith("/recover") })
    }

    // --- sessionStatus reactivity (Fase 2.3 / hallazgo 4.11) ---

    @Test
    fun `signedIn reflects an externally-driven session change, not just signIn()-and-signOut()`() = runTest {
        mockkStatic("io.github.jan.supabase.auth.AuthKt")
        try {
            val fakeSessionStatus = MutableStateFlow<SessionStatus>(SessionStatus.NotAuthenticated(false))
            val supabaseClient = mockk<SupabaseClient>()
            val auth = mockk<Auth>()
            every { supabaseClient.auth } returns auth
            every { auth.sessionStatus } returns fakeSessionStatus
            every { auth.currentUserOrNull() } returns null
            val viewModel = AuthViewModel(application, supabaseClient, syncManager, syncPrefs)
            val collected = mutableListOf<Boolean>()
            val job = launch { viewModel.signedIn.collect { collected += it } }
            // signedIn is a stateIn(..., WhileSubscribed(5000)) flow: its sharing coroutine only
            // starts once something collects it, and that collector above was launched on this
            // test's own (Standard, queued) TestDispatcher rather than Main/Unconfined — it needs
            // an explicit pump before its first value is actually collected.
            runCurrent()

            val session = UserSession(
                accessToken = "t", refreshToken = "r", expiresIn = 3600, tokenType = "bearer",
                user = UserInfo(aud = "authenticated", id = userId)
            )
            fakeSessionStatus.value = SessionStatus.Authenticated(session, SessionSource.SignIn(mockk(relaxed = true)))
            runCurrent()

            assertEquals(true, viewModel.signedIn.value)
            job.cancel()
        } finally {
            unmockkStatic("io.github.jan.supabase.auth.AuthKt")
        }
    }

    @Test
    fun `a RefreshFailure observed outside of signIn or signOut surfaces the session-expired message`() = runTest {
        mockkStatic("io.github.jan.supabase.auth.AuthKt")
        try {
            every { application.getString(R.string.auth_session_expired) } returns "session expired"
            val fakeSessionStatus = MutableStateFlow<SessionStatus>(
                SessionStatus.Authenticated(
                    UserSession(accessToken = "t", refreshToken = "r", expiresIn = 3600, tokenType = "bearer", user = UserInfo(aud = "authenticated", id = userId)),
                    SessionSource.Storage
                )
            )
            val supabaseClient = mockk<SupabaseClient>()
            val auth = mockk<Auth>()
            every { supabaseClient.auth } returns auth
            every { auth.sessionStatus } returns fakeSessionStatus
            every { auth.currentUserOrNull() } returns UserInfo(aud = "authenticated", id = userId)
            val viewModel = AuthViewModel(application, supabaseClient, syncManager, syncPrefs)
            assertNull(viewModel.errorMessage.value)

            fakeSessionStatus.value = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(Exception("offline")))

            // The init{} block's own collector runs regardless of whether anything subscribes to
            // signedIn, unlike the StateFlow in the previous test.
            assertEquals("session expired", viewModel.errorMessage.value)
        } finally {
            unmockkStatic("io.github.jan.supabase.auth.AuthKt")
        }
    }
}
