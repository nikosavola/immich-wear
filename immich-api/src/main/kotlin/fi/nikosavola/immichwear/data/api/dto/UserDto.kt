package fi.nikosavola.immichwear.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(val id: String, val email: String? = null, val name: String? = null)
