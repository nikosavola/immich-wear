package fi.nikosavola.immichwear.data.api

import fi.nikosavola.immichwear.data.api.dto.AlbumDto
import fi.nikosavola.immichwear.data.api.dto.MetadataSearchRequest
import fi.nikosavola.immichwear.data.api.dto.SearchMetadataResponse
import fi.nikosavola.immichwear.data.api.dto.ServerPingResponse
import fi.nikosavola.immichwear.data.api.dto.UpdateAssetRequest
import fi.nikosavola.immichwear.data.api.dto.UserDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface ImmichApi {
  @GET("api/server/ping") suspend fun ping(): ServerPingResponse

  @GET("api/users/me") suspend fun getCurrentUser(): UserDto

  @POST("api/search/metadata")
  suspend fun searchMetadata(@Body request: MetadataSearchRequest): SearchMetadataResponse

  @GET("api/albums") suspend fun getAlbums(): List<AlbumDto>

  // Album contents are not returned inline by this endpoint on the current server version;
  // ImmichRepository.albumAssets() fetches them via searchMetadata(albumIds = [id]) instead.
  @GET("api/albums/{id}") suspend fun getAlbum(@Path("id") id: String): AlbumDto

  @PUT("api/assets/{id}")
  suspend fun updateAsset(@Path("id") id: String, @Body request: UpdateAssetRequest)
}
