// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.completion

import com.sun.net.httpserver.HttpServer
import helium314.keyboard.latin.context.SenseContextCache
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class SenseCompletionClientTest {
    private lateinit var server: HttpServer
    private lateinit var base: String
    private val serverWorker = Executors.newCachedThreadPool()
    private val calls = AtomicInteger()
    private val postBody = AtomicReference("")
    private val authHeader = AtomicReference<String?>()
    private val status = AtomicInteger(200)
    private val response = AtomicReference("""{"choices":[{"message":{"content":"буду завтра к 11."},"finish_reason":"stop"}]}""")
    private val location = AtomicReference("")
    private var entered: CountDownLatch? = null
    private var release: CountDownLatch? = null

    @Before fun setUp() {
        SenseContextCache.setServiceConnected(true)
        SenseContextCache.update("chat.test", 1, "Сможешь приехать завтра к 11?")
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = serverWorker
        base = "http://127.0.0.1:${server.address.port}"
        server.createContext("/v1/models") { exchange ->
            calls.incrementAndGet()
            val bytes = """{"data":[{"id":"loaded-test-model"}]}""".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.createContext("/v1/chat/completions") { exchange ->
            calls.incrementAndGet()
            postBody.set(exchange.requestBody.bufferedReader().use { it.readText() })
            authHeader.set(exchange.requestHeaders.getFirst("Authorization"))
            entered?.countDown()
            release?.await(5, TimeUnit.SECONDS)
            if (location.get().isNotEmpty()) exchange.responseHeaders.add("Location", location.get())
            val bytes = response.get().toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(status.get(), bytes.size.toLong())
            try { exchange.responseBody.use { it.write(bytes) } } catch (_: java.io.IOException) { }
            exchange.close()
        }
        server.start()
    }

    @After fun tearDown() {
        release?.countDown()
        server.stop(0)
        serverWorker.shutdownNow()
        SenseContextCache.setServiceConnected(false)
    }

    private fun request(draft: String = "Да,") = SenseCompletionRequest(
        1, 7, "chat.test", draft.length, draft, SenseContextCache.getSnapshot())

    private fun complete(model: String = "", cancellation: SenseCompletionClient.Cancellation =
        SenseCompletionClient.Cancellation()) = SenseCompletionClient().complete(base, model, request(), cancellation)

    @Test fun autoDetectsModelAndSendsContextAsDataWithoutToolsOrCredentials() {
        val result = complete()
        assertEquals(SenseCompletionClient.Error.NONE, result.error)
        assertEquals(" буду завтра к 11.", result.suffix)
        assertEquals("loaded-test-model", result.model)
        assertEquals(2, calls.get())
        assertEquals(null, authHeader.get())
        val body = JSONObject(postBody.get())
        assertFalse(body.has("tools"))
        assertFalse(body.getBoolean("stream"))
        assertEquals(64, body.getInt("max_tokens"))
        assertFalse(body.getJSONObject("chat_template_kwargs").getBoolean("enable_thinking"))
        val messages = body.getJSONArray("messages")
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("user", messages.getJSONObject(1).getString("role"))
        val data = JSONObject(messages.getJSONObject(1).getString("content"))
        assertEquals("Да,", data.getString("draft"))
        assertEquals("Сможешь приехать завтра к 11?", data.getString("screen_context"))
    }

    @Test fun explicitModelDoesNotRequireDiscovery() {
        assertEquals("chosen-model", complete("chosen-model").model)
        assertEquals(1, calls.get())
    }

    @Test fun checkingModelNeverPostsConversation() {
        assertEquals("loaded-test-model", SenseCompletionClient().discoverModel(base,
            SenseCompletionClient.Cancellation()))
        assertEquals(1, calls.get())
        assertEquals("", postBody.get())
    }

    @Test fun loopbackAddressesNormalizeButRemoteUrlsAreRejectedBeforeAnyRequest() {
        assertEquals("http://127.0.0.1:8080/v1", SenseCompletionClient.normalizeBaseUrl("http://localhost:8080/v1/"))
        assertEquals("http://[::1]:8080/v1", SenseCompletionClient.normalizeBaseUrl("http://[::1]:8080"))
        for (address in listOf("https://example.com", "http://192.168.1.2:8080",
            "http://127.0.0.1.evil.test:8080", "http://user@127.0.0.1:8080",
            "http://127.0.0.1:8080/proxy", "http://127.0.0.1:8080?remote=1",
            "http://127.0.0.1:0", "http://127.0.0.1:65536")) {
            val result = SenseCompletionClient().complete(address, "", request(), SenseCompletionClient.Cancellation())
            assertEquals(SenseCompletionClient.Error.ADDRESS, result.error, address)
            assertEquals("", result.suffix)
        }
        assertEquals(0, calls.get())
    }

    @Test fun httpRedirectIsNotFollowed() {
        status.set(302)
        location.set("$base/v1/models")
        val result = complete("chosen-model")
        assertEquals(SenseCompletionClient.Error.SERVER, result.error)
        assertEquals("", result.suffix)
        assertEquals(1, calls.get())
    }

    @Test fun serverFailureCannotBecomeInsertedText() {
        status.set(503)
        response.set("private server details")
        val result = complete("chosen-model")
        assertEquals(SenseCompletionClient.Error.SERVER, result.error)
        assertEquals("", result.suffix)
    }

    @Test fun invalidJsonCannotBecomeInsertedText() {
        response.set("not JSON")
        val result = complete("chosen-model")
        assertEquals(SenseCompletionClient.Error.RESPONSE, result.error)
        assertEquals("", result.suffix)
    }

    @Test fun nullContentAndTruncatedGenerationDoNotProduceSuggestions() {
        for (body in listOf("""{"choices":[{"message":{"content":null}}]}""",
            """{"choices":[{"message":{"content":"half a phrase"},"finish_reason":"length"}]}""")) {
            response.set(body)
            val result = complete("chosen-model")
            assertEquals(SenseCompletionClient.Error.EMPTY, result.error)
            assertEquals("", result.suffix)
        }
    }

    @Test fun oversizedResponseIsBounded() {
        response.set("x".repeat(70_000))
        val result = complete("chosen-model")
        assertEquals(SenseCompletionClient.Error.SERVER, result.error)
        assertEquals("", result.suffix)
    }

    @Test fun cancellationBeforeStartMakesNoRequest() {
        val cancellation = SenseCompletionClient.Cancellation()
        cancellation.cancel()
        assertEquals(SenseCompletionClient.Error.CANCELLED, complete(cancellation = cancellation).error)
        assertEquals(0, calls.get())
    }

    @Test fun cancelledInFlightResponseCannotBeInserted() {
        entered = CountDownLatch(1)
        release = CountDownLatch(1)
        val cancellation = SenseCompletionClient.Cancellation()
        val caller = Executors.newSingleThreadExecutor()
        try {
            val future = caller.submit<SenseCompletionClient.Result> { complete("chosen-model", cancellation) }
            assertTrue(entered!!.await(5, TimeUnit.SECONDS))
            cancellation.cancel()
            assertTrue(cancellation.isCancelled)
            release!!.countDown()
            val result = future.get(5, TimeUnit.SECONDS)
            assertEquals(SenseCompletionClient.Error.CANCELLED, result.error)
            assertEquals("", result.suffix)
        } finally { caller.shutdownNow() }
    }

    @Test fun completedReasoningAndRepeatedDraftAreExcluded() {
        assertEquals(" буду к 11.", SenseCompletionClient.cleanSuffix(
            "<think>private reasoning</think>\"Да, буду к 11.\"", "Да,"))
        assertEquals("буду к 11.", SenseCompletionClient.cleanSuffix("буду к 11.", "Да, "))
        assertEquals("", SenseCompletionClient.cleanSuffix("<think>unfinished reasoning", "Да,"))
        assertEquals("", SenseCompletionClient.cleanSuffix("```code```", "Да,"))
        assertEquals("", SenseCompletionClient.cleanSuffix("x".repeat(181), "Да,"))
    }

    @Test fun contextBudgetKeepsRecentTextAndDoesNotSplitSurrogatePair() {
        val long = "Earlier material\n".repeat(400) + "Latest relevant question"
        SenseContextCache.update("chat.test", 1, long)
        val body = SenseCompletionClient.createPayload("chosen-model", request())
        val data = JSONObject(body.getJSONArray("messages").getJSONObject(1).getString("content"))
        assertTrue(data.getString("screen_context").length <= SenseCompletionClient.MAX_CONTEXT_CHARS)
        assertTrue(data.getString("screen_context").endsWith("Latest relevant question"))
        assertEquals("ab", SenseCompletionClient.safeTail("😀ab", 3))
    }
}
