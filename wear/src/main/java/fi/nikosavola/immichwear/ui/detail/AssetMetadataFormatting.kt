package fi.nikosavola.immichwear.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.Text
import fi.nikosavola.immichwear.data.api.dto.ExifInfoDto
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Composable
fun ExifDetails(exif: ExifInfoDto) {
  formatDimensions(exif)?.let { Text(text = it, textAlign = TextAlign.Center) }
  formatCamera(exif)?.let { Text(text = it, textAlign = TextAlign.Center) }
  exif.lensModel?.let { Text(text = it, textAlign = TextAlign.Center) }
  formatCameraSettings(exif)?.let { Text(text = it, textAlign = TextAlign.Center) }
  formatLocation(exif)?.let { Text(text = it, textAlign = TextAlign.Center) }
  exif.fileSizeInByte?.let { Text(text = formatFileSize(it), textAlign = TextAlign.Center) }
}

private fun formatDimensions(exif: ExifInfoDto): String? {
  val width = exif.exifImageWidth
  val height = exif.exifImageHeight
  return if (width != null && height != null) "$width × $height" else null
}

private fun formatCamera(exif: ExifInfoDto): String? =
  listOfNotNull(exif.make, exif.model).joinToString(" ").ifBlank { null }

private fun formatCameraSettings(exif: ExifInfoDto): String? {
  val parts =
    listOfNotNull(
      exif.fNumber?.let { "f/%.1f".format(it) },
      exif.exposureTime?.let { "${it}s" },
      exif.iso?.let { "ISO $it" },
      exif.focalLength?.let { "%.0fmm".format(it) },
    )
  return parts.joinToString(" · ").ifBlank { null }
}

private fun formatLocation(exif: ExifInfoDto): String? =
  listOfNotNull(exif.city, exif.country).joinToString(", ").ifBlank { null }

private const val BYTES_PER_KB = 1024.0

private fun formatFileSize(bytes: Long): String {
  val kb = bytes / BYTES_PER_KB
  val mb = kb / BYTES_PER_KB
  return if (mb >= 1) "%.1f MB".format(mb) else "%.0f KB".format(kb)
}

private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")

// A malformed/unexpected date string is a plain server-response boundary condition, not a bug -
// falling back to the raw string is the correct, deliberate behavior here.
@Suppress("SwallowedException")
fun formatLocalDateTime(raw: String): String =
  try {
    OffsetDateTime.parse(raw).format(DATE_TIME_FORMATTER)
  } catch (e: DateTimeParseException) {
    raw
  }
