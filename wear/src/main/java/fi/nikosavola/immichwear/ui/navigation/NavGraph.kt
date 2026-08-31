package fi.nikosavola.immichwear.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import fi.nikosavola.immichwear.data.ImmichRepository
import fi.nikosavola.immichwear.data.ImmichResult
import fi.nikosavola.immichwear.data.TimelinePage
import fi.nikosavola.immichwear.di.AppContainer
import fi.nikosavola.immichwear.ui.albums.AlbumDetailScreen
import fi.nikosavola.immichwear.ui.albums.AlbumDetailViewModel
import fi.nikosavola.immichwear.ui.albums.AlbumsScreen
import fi.nikosavola.immichwear.ui.albums.AlbumsViewModel
import fi.nikosavola.immichwear.ui.detail.AssetDetailScreen
import fi.nikosavola.immichwear.ui.detail.AssetDetailViewModel
import fi.nikosavola.immichwear.ui.favorites.FavoritesScreen
import fi.nikosavola.immichwear.ui.home.HomeScreen
import fi.nikosavola.immichwear.ui.home.HomeViewModel
import fi.nikosavola.immichwear.ui.settings.SettingsScreen
import fi.nikosavola.immichwear.ui.settings.SettingsViewModel
import fi.nikosavola.immichwear.ui.timeline.PagedAssetsViewModel
import fi.nikosavola.immichwear.ui.timeline.TimelineScreen

private const val SOURCE_ARG = "source"
private const val ASSET_ID_ARG = "assetId"
private const val ALBUM_ID_ARG = "albumId"

@Composable
fun ImmichNavHost(
  appContainer: AppContainer,
  navController: NavHostController = rememberSwipeDismissableNavController(),
  startDestination: String = ImmichRoutes.HOME,
) {
  AppScaffold {
    SwipeDismissableNavHost(navController = navController, startDestination = startDestination) {
      composable(ImmichRoutes.HOME) { HomeDestination(appContainer, navController) }
      composable(ImmichRoutes.SETTINGS) { SettingsDestination(appContainer) }
      composable(ImmichRoutes.TIMELINE) { TimelineDestination(appContainer, navController) }
      composable(ImmichRoutes.FAVORITES) { FavoritesDestination(appContainer, navController) }
      composable(
        ImmichRoutes.ASSET_DETAIL_PATTERN,
        arguments =
          listOf(
            navArgument(SOURCE_ARG) { type = NavType.StringType },
            navArgument(ASSET_ID_ARG) { type = NavType.StringType },
          ),
      ) { backStackEntry ->
        val source = backStackEntry.arguments?.getString(SOURCE_ARG).orEmpty()
        val assetId = backStackEntry.arguments?.getString(ASSET_ID_ARG).orEmpty()
        AssetDetailDestination(appContainer, navController, source, assetId)
      }
      composable(ImmichRoutes.ALBUMS) { AlbumsDestination(appContainer, navController) }
      composable(
        ImmichRoutes.ALBUM_DETAIL_PATTERN,
        arguments = listOf(navArgument(ALBUM_ID_ARG) { type = NavType.StringType }),
      ) { backStackEntry ->
        val albumId = backStackEntry.arguments?.getString(ALBUM_ID_ARG).orEmpty()
        AlbumDetailDestination(appContainer, navController, albumId)
      }
    }
  }
}

@Composable
private inline fun <reified VM : ViewModel> immichViewModel(crossinline create: () -> VM): VM =
  viewModel(factory = viewModelFactory { initializer { create() } })

@Composable
private fun HomeDestination(appContainer: AppContainer, navController: NavHostController) {
  val viewModel = immichViewModel {
    HomeViewModel(appContainer.settingsStore, appContainer.repository)
  }
  HomeScreen(
    viewModel = viewModel,
    onNavigateToTimeline = { navController.navigate(ImmichRoutes.TIMELINE) },
    onNavigateToAlbums = { navController.navigate(ImmichRoutes.ALBUMS) },
    onNavigateToFavorites = { navController.navigate(ImmichRoutes.FAVORITES) },
    onNavigateToSettings = { navController.navigate(ImmichRoutes.SETTINGS) },
    onMemoryClick = { assetId ->
      navController.navigate(ImmichRoutes.assetDetailFromMemory(assetId))
    },
  )
}

@Composable
private fun SettingsDestination(appContainer: AppContainer) {
  val viewModel = immichViewModel {
    SettingsViewModel(
      repository = appContainer.repository,
      settingsStore = appContainer.settingsStore,
      onSignedOut = {
        appContainer.imageLoader.memoryCache?.clear()
        appContainer.imageLoader.diskCache?.clear()
      },
    )
  }
  SettingsScreen(viewModel = viewModel)
}

@Composable
private fun TimelineDestination(appContainer: AppContainer, navController: NavHostController) {
  val viewModel = immichViewModel { PagedAssetsViewModel(appContainer.repository::timeline) }
  TimelineScreen(
    viewModel = viewModel,
    onAssetClick = { assetId ->
      navController.navigate(ImmichRoutes.assetDetailFromTimeline(assetId))
    },
    onNavigateToSettings = { navController.navigate(ImmichRoutes.SETTINGS) },
  )
}

@Composable
private fun FavoritesDestination(appContainer: AppContainer, navController: NavHostController) {
  val viewModel = immichViewModel { PagedAssetsViewModel(appContainer.repository::favorites) }
  FavoritesScreen(
    viewModel = viewModel,
    onAssetClick = { assetId ->
      navController.navigate(ImmichRoutes.assetDetailFromFavorites(assetId))
    },
    onNavigateToSettings = { navController.navigate(ImmichRoutes.SETTINGS) },
  )
}

@Composable
private fun AssetDetailDestination(
  appContainer: AppContainer,
  navController: NavHostController,
  source: String,
  assetId: String,
) {
  val viewModel = immichViewModel {
    AssetDetailViewModel(
      repository = appContainer.repository,
      assetId = assetId,
      fetchPage = fetchPageFor(source, appContainer.repository),
    )
  }
  AssetDetailScreen(
    viewModel = viewModel,
    onNavigateToSettings = { navController.navigate(ImmichRoutes.SETTINGS) },
  )
}

// Maps the `source` nav argument (see ImmichRoutes.assetDetailFromTimeline and friends) back to
// the repository call that produces the same paginated list, so AssetDetailViewModel can
// re-locate the tapped asset within it and page to its siblings.
private fun fetchPageFor(
  source: String,
  repository: ImmichRepository,
): suspend (Int?) -> ImmichResult<TimelinePage> {
  val albumId = ImmichRoutes.albumIdFromSource(source)
  return when {
    source == ImmichRoutes.SOURCE_FAVORITES -> repository::favorites
    source == ImmichRoutes.SOURCE_MEMORY -> { _ ->
      memoriesAsPage(repository)
    }
    albumId != null -> { page ->
      repository.albumAssets(albumId, page)
    }
    else -> repository::timeline
  }
}

// Memories aren't paginated - the server returns the whole day's set in one response - so this
// flattens every matching year's assets into a single non-paginated TimelinePage for sibling
// paging, same shape AssetDetailViewModel expects from every other source.
private suspend fun memoriesAsPage(repository: ImmichRepository): ImmichResult<TimelinePage> =
  when (val result = repository.memories()) {
    is ImmichResult.Success ->
      ImmichResult.Success(
        TimelinePage(items = result.value.flatMap { it.assets }, nextPage = null)
      )
    is ImmichResult.Failure -> result
  }

@Composable
private fun AlbumsDestination(appContainer: AppContainer, navController: NavHostController) {
  val viewModel = immichViewModel { AlbumsViewModel(appContainer.repository) }
  AlbumsScreen(
    viewModel = viewModel,
    onAlbumClick = { albumId -> navController.navigate(ImmichRoutes.albumDetail(albumId)) },
    onNavigateToSettings = { navController.navigate(ImmichRoutes.SETTINGS) },
  )
}

@Composable
private fun AlbumDetailDestination(
  appContainer: AppContainer,
  navController: NavHostController,
  albumId: String,
) {
  val viewModel = immichViewModel { AlbumDetailViewModel(appContainer.repository, albumId) }
  AlbumDetailScreen(
    viewModel = viewModel,
    onAssetClick = { assetId ->
      navController.navigate(ImmichRoutes.assetDetailFromAlbum(albumId, assetId))
    },
    onNavigateToSettings = { navController.navigate(ImmichRoutes.SETTINGS) },
  )
}
