package fi.nikosavola.immichwear.datalayer

import kotlinx.serialization.Serializable

// Wire contract for the phone (:mobile) -> watch (:wear) login handoff over the Wear OS Data
// Layer. Deliberately duplicated in :mobile's own copy of this file rather than shared through a
// third module - see PhoneLoginListenerService for why. kotlinx.serialization matches fields by
// name, so keep both copies in sync by hand.
const val LOGIN_REQUEST_PATH = "/immich/login/request"
const val LOGIN_RESULT_PATH = "/immich/login/result"
const val LOGIN_CAPABILITY = "immich_wear_login"

@Serializable data class LoginRequest(val serverUrl: String, val apiKey: String)

@Serializable
data class LoginResult(
  val success: Boolean,
  val errorMessage: String? = null,
  val stats: LoginStats? = null,
)

/**
 * Best-effort library counts included with a successful [LoginResult] - null if the follow-up stats
 * fetch failed, which never fails the login itself.
 */
@Serializable data class LoginStats(val total: Int, val images: Int, val videos: Int)
