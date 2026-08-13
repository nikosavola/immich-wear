package fi.nikosavola.immichwear.data

/** Result of a repository operation: never thrown for the expected API/network failure modes. */
sealed interface ImmichResult<out T> {
  data class Success<out T>(val value: T) : ImmichResult<T>

  data class Failure(val error: ImmichError) : ImmichResult<Nothing>
}

/** Failure reasons a repository call can return, mapped from the underlying HTTP/IO exception. */
sealed interface ImmichError {
  /** HTTP 401: the stored API key is invalid or was revoked. The UI should route to Settings. */
  data object Unauthorized : ImmichError

  /**
   * No network reachable, the server address doesn't resolve, or the request otherwise failed at
   * the transport layer.
   */
  data object Offline : ImmichError

  /** Any other non-2xx response, carrying the status code for diagnostics. */
  data class Http(val code: Int) : ImmichError

  /** The response body could not be decoded as the expected DTO shape. */
  data object ParseError : ImmichError

  /** The server URL typed in Settings doesn't parse as an http(s) address. */
  data object InvalidServerUrl : ImmichError

  /** The operation needs a configured server + API key but none is persisted yet. */
  data object NotConfigured : ImmichError
}
