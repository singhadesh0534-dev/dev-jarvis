package com.adesh.devjarvis.brain

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupModelsTest {
    private val groq = """{"object":"list","data":[
        {"id":"llama-3.1-8b-instant","active":true},
        {"id":"whisper-large-v3-turbo","active":true},
        {"id":"openai/gpt-oss-120b","active":true},
        {"id":"moonshotai/kimi-k2-instruct-0905","active":true},
        {"id":"meta-llama/llama-guard-4-12b","active":true},
        {"id":"qwen/qwen3-32b","active":true},
        {"id":"groq/compound","active":true},
        {"id":"llama-3.3-70b-versatile","active":false},
        {"id":"some-unknown-model","active":true}
    ]}"""

    @Test fun bestChatModelsFirstNothingElse() {
        assertEquals(listOf("openai/gpt-oss-120b", "moonshotai/kimi-k2-instruct-0905", "qwen/qwen3-32b", "llama-3.1-8b-instant"), BackupModels.pick(groq))
    }

    @Test fun settingsModelStaysFirstWhenAvailable() {
        assertEquals("qwen/qwen3-32b", BackupModels.pick(groq, current = "qwen/qwen3-32b").first())
        assertEquals("openai/gpt-oss-120b", BackupModels.pick(groq, current = "not-there").first())
    }

    @Test fun badBodyGivesNothing() = assertEquals(emptyList<String>(), BackupModels.pick("<html>"))

    @Test fun openRouterFreeModelsWithTools() {
        val body = """{"data":[
            {"id":"vendor/paid-model","pricing":{"prompt":"0.000001","completion":"0.000002"},"supported_parameters":["tools"],"context_length":200000},
            {"id":"qwen/qwen3-32b:free","pricing":{"prompt":"0","completion":"0"},"supported_parameters":["tools","temperature"],"context_length":40000},
            {"id":"deepseek/deepseek-v3:free","pricing":{"prompt":"0","completion":"0"},"supported_parameters":["tools"],"context_length":160000},
            {"id":"tiny/no-tools:free","pricing":{"prompt":"0","completion":"0"},"supported_parameters":["temperature"],"context_length":8000},
            {"id":"other/free-unknown:free","pricing":{"prompt":"0","completion":"0"},"supported_parameters":["tools"],"context_length":128000}
        ]}"""
        assertEquals(listOf("deepseek/deepseek-v3:free", "qwen/qwen3-32b:free", "other/free-unknown:free"), BackupModels.pickOpenRouterFree(body))
    }
}
