package fi.nikosavola.immichwear.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// Dynamic color (Material You) on Android 12+, matching the watch app's own ImmichTheme; a static
// scheme below that so the app still looks intentional on older phones.
@Composable
fun LoginTheme(content: @Composable () -> Unit) {
  val context = LocalContext.current
  val darkTheme = isSystemInDarkTheme()
  val colorScheme =
    when {
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(context)
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
      darkTheme -> darkColorScheme()
      else -> lightColorScheme()
    }
  MaterialTheme(colorScheme = colorScheme, content = content)
}
