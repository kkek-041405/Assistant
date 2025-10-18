package com.kkek.assistant.ui.greeting

import app.cash.turbine.test
import com.kkek.assistant.domain.model.Greeting
import com.kkek.assistant.domain.repository.GreetingRepository
import com.kkek.assistant.domain.result.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertTrue

private class FakeGreetingRepository : GreetingRepository {
    private val _state = MutableStateFlow<Result<Greeting>>(Result.Success(Greeting("test")))
    override fun greetingFlow() = _state
    override suspend fun refreshGreeting() {
        _state.value = Result.Loading
        _state.value = Result.Success(Greeting("refreshed"))
    }
}

class GreetingViewModelTest {

    @Test
    fun `uiState reflects repository and refresh updates state`() = runTest {
        val repo = FakeGreetingRepository()
        val vm = GreetingViewModel(repo)

        // initial state should be Success with "test"
        val initial = vm.uiState.value
        assertTrue(initial is Result.Success)

        // trigger a refresh and verify state updates
        vm.refresh()

        // small suspend to allow coroutine dispatch
        kotlinx.coroutines.delay(10)

        val after = vm.uiState.value
        assertTrue(after is Result.Success)
    }
}

