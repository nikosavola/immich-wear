package fi.nikosavola.immichwear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

// Wear Compose Material3's own default scheme already uses pure black for `background` (see
// ColorTokens.Background), but a dynamic scheme derives it from the watch's system theme color, so
// it's overridden back to black here - matching the Wear OS quality guideline to keep the base
// screen background black for OLED/legibility, while still letting containers, buttons and other
// surfaces keep their dynamic-color personality. A plain function (not inlined into ImmichTheme)
// so this override is unit-testable without a Composable test harness.
internal fun blackBackground(scheme: ColorScheme?): ColorScheme? =
  scheme?.copy(background = Color.Black)

// dynamicColorScheme derives the palette from the watch's system theme color (Wear OS's equivalent
// of Material You) where the device supports it, but returns null rather than a scheme on older
// API levels or when the user has not enabled a system theme color - falling through to no
// colorScheme argument at all keeps Wear Compose Material3's own built-in scheme in that case.
@Composable
fun ImmichTheme(content: @Composable () -> Unit) {
  val context = LocalContext.current
  val dynamicScheme = remember(context) { blackBackground(dynamicColorScheme(context)) }
  if (dynamicScheme != null) {
    MaterialTheme(colorScheme = dynamicScheme, content = content)
  } else {
    MaterialTheme(content = content)
  }
}
