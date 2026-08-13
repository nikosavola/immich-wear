package fi.nikosavola.immichwear.ui.navigation

import androidx.compose.runtime.Composable
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
import fi.nikosavola.immichwear.di.AppContainer
import fi.nikosavola.immichwear.ui.detail.AssetDetailScreen
import fi.nikosavola.immichwear.ui.detail.AssetDetailViewModel
import fi.nikosavola.immichwear.ui.home.HomeScreen
import fi.nikosavola.immichwear.ui.home.HomeViewModel
import fi.nikosavola.immichwear.ui.settings.SettingsScreen
import fi.nikosavola.immichwear.ui.settings.SettingsViewModel
import fi.nikosavola.immichwear.ui.timeline.TimelineScreen
import fi.nikosavola.immichwear.ui.timeline.TimelineViewModel

private const val ASSET_ID_ARG = "assetId"

@Composable
fun ImmichNavHost(
  appContainer: AppContainer,
  navController: NavHostController = rememberSwipeDismissableNavController(),
) {
  AppScaffold {
    SwipeDismissableNavHost(navController = navController, startDestination = ImmichRoutes.HOME) {
      composable(ImmichRoutes.HOME) { HomeDestination(appContainer, navController) }
      composable(ImmichRoutes.SETTINGS) { SettingsDestination(appContainer) }
      composable(ImmichRoutes.TIMELINE) { TimelineDestination(appContainer, navController) }
      composable(
        ImmichRoutes.ASSET_DETAIL_PATTERN,
        arguments = listOf(navArgument(ASSET_ID_ARG) { type = NavType.StringType }),
      ) { backStackEntry ->
        val assetId = backStackEntry.arguments?.getString(ASSET_ID_ARG).orEmpty()
        AssetDetailDestination(appContainer, navController, assetId)
      }
    }
  }
}

@Composable
private fun HomeDestination(appContainer: AppContainer, navController: NavHostController) {
  val viewModel: HomeViewModel =
    viewModel(
      factory = viewModelFactory { initializer { HomeViewModel(appContainer.settingsStore) } }
    )
  HomeScreen(
    viewModel = viewModel,
    onNavigateToTimeline = { navController.navigate(ImmichRoutes.TIMELINE) },
    onNavigateToSettings = { navController.navigate(ImmichRoutes.SETTINGS) },
  )
}

@Composable
private fun SettingsDestination(appContainer: AppContainer) {
  val viewModel: SettingsViewModel =
    viewModel(
      factory =
        viewModelFactory {
          initializer {
            SettingsViewModel(
              repository = appContainer.repository,
              settingsStore = appContainer.settingsStore,
            )
          }
        }
    )
  SettingsScreen(viewModel = viewModel)
}

@Composable
private fun TimelineDestination(appContainer: AppContainer, navController: NavHostController) {
  val viewModel: TimelineViewModel =
    viewModel(
      factory =
        viewModelFactory {
          initializer {
            TimelineViewModel(
              repository = appContainer.repository,
              settingsPrimed = appContainer.settingsPrimed,
            )
          }
        }
    )
  TimelineScreen(
    viewModel = viewModel,
    onAssetClick = { assetId -> navController.navigate(ImmichRoutes.assetDetail(assetId)) },
    onNavigateToSettings = { navController.navigate(ImmichRoutes.SETTINGS) },
  )
}

@Composable
private fun AssetDetailDestination(
  appContainer: AppContainer,
  navController: NavHostController,
  assetId: String,
) {
  val viewModel: AssetDetailViewModel =
    viewModel(
      factory =
        viewModelFactory {
          initializer {
            AssetDetailViewModel(
              repository = appContainer.repository,
              settingsPrimed = appContainer.settingsPrimed,
              assetId = assetId,
            )
          }
        }
    )
  AssetDetailScreen(
    viewModel = viewModel,
    onNavigateToSettings = { navController.navigate(ImmichRoutes.SETTINGS) },
  )
}
