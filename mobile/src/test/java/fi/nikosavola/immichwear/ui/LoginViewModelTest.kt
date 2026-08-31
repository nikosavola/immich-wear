package fi.nikosavola.immichwear.ui

import fi.nikosavola.immichwear.datalayer.LoginOutcome
import fi.nikosavola.immichwear.datalayer.LoginStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `starts idle`() {
    val viewModel = LoginViewModel(sendToWatch = { _, _ -> LoginOutcome.Success(stats = null) })

    assertEquals(LoginUiState.Idle, viewModel.uiState.value)
  }

  @Test
  fun `send surfaces a successful outcome with stats`() = runTest {
    val viewModel =
      LoginViewModel(sendToWatch = { _, _ -> LoginOutcome.Success(LoginStats(7, 5, 2)) })

    viewModel.send("https://immich.example.com", "key").join()

    assertEquals(
      LoginUiState.Result(LoginOutcome.Success(LoginStats(7, 5, 2))),
      viewModel.uiState.value,
    )
  }

  @Test
  fun `send passes the typed server url and api key through unchanged`() = runTest {
    var received: Pair<String, String>? = null
    val viewModel =
      LoginViewModel(
        sendToWatch = { serverUrl, apiKey ->
          received = serverUrl to apiKey
          LoginOutcome.Success(stats = null)
        }
      )

    viewModel.send("https://immich.example.com", "secret-key").join()

    assertEquals("https://immich.example.com" to "secret-key", received)
  }

  @Test
  fun `send surfaces a failure outcome`() = runTest {
    val viewModel =
      LoginViewModel(sendToWatch = { _, _ -> LoginOutcome.Failure("Invalid API key") })

    viewModel.send("https://immich.example.com", "wrong-key").join()

    assertEquals(
      LoginUiState.Result(LoginOutcome.Failure("Invalid API key")),
      viewModel.uiState.value,
    )
  }

  @Test
  fun `send surfaces NoWatchFound and SendFailed outcomes unchanged`() = runTest {
    val noWatch = LoginViewModel(sendToWatch = { _, _ -> LoginOutcome.NoWatchFound })
    val sendFailed = LoginViewModel(sendToWatch = { _, _ -> LoginOutcome.SendFailed })

    noWatch.send("https://immich.example.com", "key").join()
    sendFailed.send("https://immich.example.com", "key").join()

    assertEquals(LoginUiState.Result(LoginOutcome.NoWatchFound), noWatch.uiState.value)
    assertEquals(LoginUiState.Result(LoginOutcome.SendFailed), sendFailed.uiState.value)
  }

  @Test
  fun `reset returns to Idle after a result`() = runTest {
    val viewModel = LoginViewModel(sendToWatch = { _, _ -> LoginOutcome.NoWatchFound })
    viewModel.send("https://immich.example.com", "key").join()

    viewModel.reset()

    assertEquals(LoginUiState.Idle, viewModel.uiState.value)
  }
}
