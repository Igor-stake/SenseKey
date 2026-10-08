// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.completion

import helium314.keyboard.latin.context.SenseContextCache
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
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
    private lateinit var server: LoopbackHttpFixture
    private lateinit var base: String
    private val calls = AtomicInteger()
    private val postBody = AtomicReference("")
    private val authHeader = AtomicReference<String?>()
    private val status = AtomicInteger(200)
    private val response = AtomicReference("""{"choices":[{"message":{"content":"{\"text\":\"Да, буду завтра к 11.\"}"},"finish_reason":"stop"}]}""")
    private val location = AtomicReference("")
    private var entered: CountDownLatch? = null
    private var release: CountDownLatch? = null

    @Before fun setUp() {
        SenseContextCache.setServiceConnected(true)
        SenseContextCache.update("chat.test", 1, "Сможешь приехать завтра к 11?")
        server = LoopbackHttpFixture()
        base = "http://127.0.0.1:${server.port}"
        server.start { request ->
            calls.incrementAndGet()
            if (request.path == "/v1/models") {
                LoopbackHttpFixture.Response(200, """{"data":[{"id":"loaded-test-model"}]}""", "")
            } else {
                postBody.set(request.body)
                authHeader.set(request.headers["authorization"])
                entered?.countDown()
                release?.await(5, TimeUnit.SECONDS)
                LoopbackHttpFixture.Response(status.get(), response.get(), location.get())
            }
        }
    }

    @After fun tearDown() {
        release?.countDown()
        server.close()
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
        assertEquals(256, body.getInt("max_tokens"))
        assertEquals("json_object", body.getJSONObject("response_format").getString("type"))
        assertTrue(body.getBoolean("cache_prompt"))
        assertFalse(body.getJSONObject("chat_template_kwargs").getBoolean("enable_thinking"))
        val messages = body.getJSONArray("messages")
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("user", messages.getJSONObject(1).getString("role"))
        val data = JSONObject(messages.getJSONObject(messages.length() - 1).getString("content"))
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
        val messages = body.getJSONArray("messages")
        val data = JSONObject(messages.getJSONObject(messages.length() - 1).getString("content"))
        assertTrue(data.getString("screen_context").length <= SenseCompletionClient.MAX_CONTEXT_CHARS)
        assertTrue(data.getString("screen_context").endsWith("Latest relevant question"))
        assertEquals("ab", SenseCompletionClient.safeTail("😀ab", 3))
    }

    @Test fun boundedContextReservesSpaceForCurrentScreenEvenAfterOlderFragments() {
        val history = "Older captured message\n".repeat(500) + "Old congratulations"
        val visible = "Can you help check the estimate?"
        val packed = SenseCompletionClient.packContext(history, visible)
        assertTrue(packed.length <= SenseCompletionClient.MAX_CONTEXT_CHARS)
        assertTrue(packed.endsWith("CURRENTLY_VISIBLE:\n$visible"))
        assertTrue(packed.contains("fragment order may be unknown"))
        assertEquals(visible, SenseCompletionClient.packContext(visible, visible))
    }

    @Test fun requestStagesAndDisplayedContextCountAgreeWithActualHttpPayload() {
        response.set("""{"choices":[{"message":{"content":"{\"text\":\"Да, готов обсудить.\"}"},"finish_reason":"stop"}]}""")
        SenseContextCache.update("chat.test", 1, "Old material\n".repeat(500) + "Recent question")
        val request = request()
        val stages = mutableListOf<SenseCompletionClient.Stage>()
        val result = SenseCompletionClient().complete(base, "chosen-model", request,
            SenseCompletionClient.Cancellation()) { stages.add(it) }
        assertEquals(SenseCompletionClient.Error.NONE, result.error)
        assertEquals(listOf(SenseCompletionClient.Stage.CONNECTING,
            SenseCompletionClient.Stage.WAITING_FOR_MODEL), stages)
        val messages = JSONObject(postBody.get()).getJSONArray("messages")
        val data = JSONObject(messages.getJSONObject(messages.length() - 1).getString("content"))
        assertEquals(request.payloadContext, data.getString("screen_context"))
        assertTrue(request.payloadContext.length <= 4_500)
        assertTrue(request.payloadContext.length < request.context.length)
    }

    @Test fun emptyDraftGetsAReplyInTheKeyboardLanguageWithoutLeadingSpace() {
        response.set("""{"choices":[{"message":{"content":"{\"text\":\"Скоро отвечу.\"}"},"finish_reason":"stop"}]}""")
        val request = SenseCompletionRequest(1, 7, "chat.test", 0, "",
            SenseContextCache.getSnapshot(), "ru-RU")
        val result = SenseCompletionClient().complete(base, "chosen-model", request,
            SenseCompletionClient.Cancellation())
        assertEquals("Скоро отвечу.", result.suffix)
        val messages = JSONObject(postBody.get()).getJSONArray("messages")
        val data = JSONObject(messages.getJSONObject(messages.length() - 1).getString("content"))
        assertEquals("", data.getString("draft"))
        assertEquals("ru-RU", data.getString("reply_language"))
    }

    @Test fun punctuationOrCaseVariantOfDraftIsNotShownAsAContinuation() {
        for (raw in listOf("Да.", "да!", "ДА")) {
            assertEquals("", SenseCompletionClient.cleanSuffix(raw, "Да,"), raw)
        }
        assertEquals("", SenseCompletionClient.cleanSuffix("Я думаю.", "Я думаю, "))
        assertEquals(" стоит попробовать.", SenseCompletionClient.cleanSuffix("Я думаю, стоит попробовать.", "я думаю,"))
        assertEquals(" Давайте обсудим.", SenseCompletionClient.cleanSuffix("Давайте обсудим.", "Да"))
        assertEquals(" стоит попробовать.", SenseCompletionClient.cleanSuffix("стоит попробовать.", "Я думаю,"))
    }

    @Test fun aCollageOfOldMessagesIsReportedAsEchoAndCannotBeInserted() {
        SenseContextCache.update("chat.test", 1, "Чай готов?\nУже давно готов!")
        response.set("""{"choices":[{"message":{"content":"{\"text\":\"Я думаю, что чай готов уже давно готов.\"}"},"finish_reason":"stop"}]}""")
        val result = SenseCompletionClient().complete(base, "chosen-model", request("Я думаю,"),
            SenseCompletionClient.Cancellation())
        assertEquals(SenseCompletionClient.Error.ECHO, result.error)
        assertEquals("", result.suffix)
        assertEquals(1, calls.get()) // No invisible retry that adds latency or fabricated fallback.
    }

    @Test fun serverTimingsAreOptionalBoundedAndSeparateFromClientDuration() {
        response.set("""{"choices":[{"message":{"content":"{\"text\":\"Да, стоит обсудить.\"}"},"finish_reason":"stop"}],
            "timings":{"prompt_ms":123.4,"predicted_ms":456.7},"usage":{"completion_tokens":9}}""")
        val result = complete("chosen-model")
        assertEquals(123L, result.promptMillis)
        assertEquals(457L, result.generationMillis)
        assertEquals(9, result.generatedTokens)
        assertTrue(result.elapsedMillis >= 0)
        response.set("""{"choices":[{"message":{"content":"{\"text\":\"Да, стоит обсудить.\"}"}}],
            "timings":{"prompt_ms":-1,"predicted_ms":1e99},"usage":{"completion_tokens":-4}}""")
        val malformed = complete("chosen-model")
        assertEquals(-1L, malformed.promptMillis)
        assertEquals(-1L, malformed.generationMillis)
        assertEquals(-1, malformed.generatedTokens)
    }

    @Test fun structuredPredictionCannotChangeTypedPrefixOrBecomeInsertedJson() {
        for (raw in listOf("{\"text\":\"Нет, не смогу.\"}",
            "{\"text\":\"да, приеду.\"}", "{\"text\":\"Да,\"}",
            "{\"text\":\"\"}")) {
            assertEquals("", SenseCompletionClient.parseCompletion(raw, "Да,"))
        }
        assertEquals(" приеду.", SenseCompletionClient.parseCompletion(
            "{\"text\":\"Да, приеду.\"}", "Да,"))
        assertEquals("приеду.", SenseCompletionClient.parseCompletion(
            "{\"text\":\"Да, приеду.\"}", "Да, "))
        for (raw in listOf("обычный текст", "{\"text\":3}",
            "{\"text\":null}", "{\"text\":\"Да, приеду.\",\"extra\":true}")) {
            response.set(JSONObject().put("choices", org.json.JSONArray().put(
                JSONObject().put("message", JSONObject().put("content", raw)))).toString())
            val result = complete("chosen-model")
            assertEquals(SenseCompletionClient.Error.RESPONSE, result.error, raw)
            assertEquals("", result.suffix)
        }
    }

    @Test fun inventedDateIsReportedAndCannotBecomeInsertedText() {
        response.set("""{"choices":[{"message":{"content":"{\"text\":\"Да, приеду послезавтра.\"}"},"finish_reason":"stop"}]}""")
        val result = complete("chosen-model")
        assertEquals(SenseCompletionClient.Error.UNSUPPORTED, result.error)
        assertEquals("", result.suffix)
        assertEquals(1, calls.get())
    }

    @Test fun noContextCannotProduceAFabricatedReplyOrNetworkRequest() {
        SenseContextCache.update("chat.test", 1, "")
        val result = complete("chosen-model")
        assertEquals(SenseCompletionClient.Error.EMPTY, result.error)
        assertEquals(0, calls.get())
    }

    @Test fun unexpectedPredictionFailureDoesNotEscapeAndNextRequestCanSucceed() {
        val result = SenseCompletionClient().complete(base, "chosen-model", request("Я думаю,"),
            SenseCompletionClient.Cancellation()) { throw IllegalStateException("private details") }
        assertEquals(SenseCompletionClient.Error.INTERNAL, result.error)
        assertEquals("", result.suffix)
        assertEquals(0, calls.get())
        assertEquals(SenseCompletionClient.Error.NONE, complete("chosen-model").error)
    }

    @Test fun androidStyleClassInitializationFailureDoesNotTerminatePredictionWorker() {
        val caller = Executors.newSingleThreadExecutor()
        try {
            val result = caller.submit<SenseCompletionClient.Result> {
                SenseCompletionClient().complete(base, "chosen-model", request("Я думаю,"),
                    SenseCompletionClient.Cancellation()) {
                    throw ExceptionInInitializerError(IllegalArgumentException("private details"))
                }
            }.get(5, TimeUnit.SECONDS)
            assertEquals(SenseCompletionClient.Error.INTERNAL, result.error)
            assertEquals("", result.suffix)
            assertEquals(0, calls.get())
            assertEquals(SenseCompletionClient.Error.NONE,
                caller.submit<SenseCompletionClient.Result> { complete("chosen-model") }
                    .get(5, TimeUnit.SECONDS).error)
        } finally { caller.shutdownNow() }
    }
}
