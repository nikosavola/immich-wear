package fi.nikosavola.immichwear.data.api.dto

import kotlinx.serialization.Serializable

// Only the "on this day" fields this client actually reads - id/type/dates etc. are ignored via
// ignoreUnknownKeys (see JsonConfig).
@Serializable data class MemoryDto(val data: MemoryDataDto, val assets: List<AssetDto>)

@Serializable data class MemoryDataDto(val year: Int)
