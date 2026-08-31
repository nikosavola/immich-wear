package fi.nikosavola.immichwear.tile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import fi.nikosavola.immichwear.ui.MainActivity
import fi.nikosavola.immichwear.ui.navigation.ImmichRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TileLayoutsTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  private fun clickableOf(
    element: LayoutElementBuilders.LayoutElement
  ): ModifiersBuilders.Clickable {
    val box = element as LayoutElementBuilders.Box
    return box.modifiers!!.clickable!!
  }

  private fun androidActivityOf(
    element: LayoutElementBuilders.LayoutElement
  ): ActionBuilders.AndroidActivity =
    (clickableOf(element).onClick as ActionBuilders.LaunchAction).androidActivity!!

  @Test
  fun `messageLayout without a startDestination opens MainActivity with no extras`() {
    val element = messageLayout(context, "not connected")

    val activity = androidActivityOf(element)
    assertEquals(context.packageName, activity.packageName)
    assertEquals(MainActivity::class.java.name, activity.className)
    assertTrue(activity.keyToExtraMapping.isEmpty())
  }

  @Test
  fun `messageLayout with a startDestination passes it as a launch extra`() {
    val element = messageLayout(context, "not connected", startDestination = ImmichRoutes.SETTINGS)

    val activity = androidActivityOf(element)
    val extra =
      activity.keyToExtraMapping[ImmichRoutes.EXTRA_START_DESTINATION]
        as? ActionBuilders.AndroidStringExtra

    assertEquals(ImmichRoutes.SETTINGS, extra?.value)
  }

  @Test
  fun `messageLayout renders the given text`() {
    val element = messageLayout(context, "hello tile")

    val box = element as LayoutElementBuilders.Box
    val text = box.contents.single() as LayoutElementBuilders.Text
    assertEquals("hello tile", text.text?.value)
  }

  @Test
  fun `photoLayout never carries a startDestination extra`() {
    val element = photoLayout(context, "photo:asset-1")

    val activity = androidActivityOf(element)
    assertNull(activity.keyToExtraMapping[ImmichRoutes.EXTRA_START_DESTINATION])
  }
}
