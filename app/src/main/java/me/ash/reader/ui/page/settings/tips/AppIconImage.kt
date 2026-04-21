package me.ash.reader.ui.page.settings.tips

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap

@Composable
internal fun AppIconImage(
    size: Dp,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }.coerceAtLeast(1)
    val bitmap =
        remember(context.packageName, sizePx) {
            context.packageManager
                .getApplicationIcon(context.packageName)
                .toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                .asImageBitmap()
        }
    Image(
        bitmap = bitmap,
        contentDescription = contentDescription,
        modifier = modifier,
    )
}
