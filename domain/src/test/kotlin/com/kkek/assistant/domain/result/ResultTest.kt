package com.kkek.assistant.domain.result

import org.junit.Assert.assertTrue
import org.junit.Test

class ResultTest {

    @Test
    fun `success holds data`() {
        val data = listOf(1, 2, 3)
        val result: Result<List<Int>> = Result.Success(data)
        assertTrue(result is Result.Success)
        if (result is Result.Success) {
            assertTrue(result.data == data)
        }
    }

    @Test
    fun `error holds throwable`() {
        val ex = RuntimeException("boom")
        val result: Result<Nothing> = Result.Error(ex)
        assertTrue(result is Result.Error)
        if (result is Result.Error) {
            assertTrue(result.exception === ex)
        }
    }

    @Test
    fun loading_is_loading() {
        val result: Result<Nothing> = Result.Loading
        assertTrue(result === Result.Loading)
    }
}

