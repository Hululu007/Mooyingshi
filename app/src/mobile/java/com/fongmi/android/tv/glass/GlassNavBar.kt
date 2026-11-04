package com.fongmi.android.tv.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/**
 * Liquid-glass bottom navigation bar for the mobile home screen.
 *
 * The visual effects (lens refraction, blur, vibrancy, highlight, shadow) come from
 * Kyant's backdrop library (io.github.kyant0:backdrop). The app's content area is a
 * classic Android View hierarchy rather than Compose, so the backdrop source is a
 * live capture of the content's bottom strip, fed into a CanvasBackdrop each frame.
 */
class GlassNavItem(
    val id: Int,
    val iconRes: Int,
    val label: String,
    val liveItem: Boolean
)

class GlassNavState {

    interface Listener {
        fun onSelect(id: Int)
        fun onLiveLongPress()
    }

    var listener: Listener? = null

    var items: List<GlassNavItem> by mutableStateOf(emptyList())
    var selectedId: Int by mutableIntStateOf(0)
    var followingId: Int by mutableIntStateOf(-1)
    var followingBadge: Int by mutableIntStateOf(0)
    var darkMode: Boolean by mutableStateOf(true)
    var accentColor: Int by mutableIntStateOf(0xFFBB86FC.toInt())

    // Draw-phase state: read only inside the backdrop draw lambda so per-frame
    // captures invalidate drawing without triggering recomposition.
    var stripImage: ImageBitmap? by mutableStateOf(null)
    var stripOffset: IntOffset by mutableStateOf(IntOffset.Zero)
    var fallbackColor: Color by mutableStateOf(Color(0xFF121212))
}

/** Java-friendly entry points used by HomeActivity. */
object GlassNav {

    @JvmStatic
    fun createState(): GlassNavState = GlassNavState()

    @JvmStatic
    fun bind(view: ComposeView, state: GlassNavState) {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent { GlassNavBarContent(state) }
    }

    @JvmStatic
    fun createCapture(source: View, anchor: View, state: GlassNavState): GlassBackdropCapture {
        return GlassBackdropCapture(source, anchor, state)
    }
}

/**
 * Captures the bottom strip of [source] (the fragment container) before each draw
 * and publishes it as the backdrop image for the glass bar.
 *
 * The captured strip covers the glass slot plus an extra margin above it, so the
 * blur/lens effects have real content to sample at the bar's top edge.
 */
class GlassBackdropCapture(
    private val source: View,
    private val anchor: View,
    private val state: GlassNavState
) {
    private var bitmap: Bitmap? = null
    private var canvas: Canvas? = null

    private val listener = ViewTreeObserver.OnPreDrawListener {
        capture()
        true
    }

    fun start() {
        source.viewTreeObserver.addOnPreDrawListener(listener)
    }

    fun stop() {
        if (source.viewTreeObserver.isAlive) {
            source.viewTreeObserver.removeOnPreDrawListener(listener)
        }
        bitmap = null
        canvas = null
    }

    private fun capture() {
        if (anchor.visibility != View.VISIBLE) return
        val width = source.width
        val sourceHeight = source.height
        if (width <= 0 || sourceHeight <= 0) return
        val stripHeight = minOf(sourceHeight, anchor.height + EXTRA_ABOVE_PX)
        if (stripHeight <= 0) return
        var bmp = bitmap
        if (bmp == null || bmp.width != width || bmp.height != stripHeight) {
            bmp = Bitmap.createBitmap(width, stripHeight, Bitmap.Config.ARGB_8888)
            bitmap = bmp
            canvas = Canvas(bmp)
        }
        val c = canvas ?: return
        try {
            bmp.eraseColor(android.graphics.Color.TRANSPARENT)
            c.save()
            c.translate(0f, -(sourceHeight - stripHeight).toFloat())
            source.draw(c)
            c.restore()
        } catch (e: Exception) {
            return
        }
        val sourceLoc = IntArray(2)
        val anchorLoc = IntArray(2)
        source.getLocationInWindow(sourceLoc)
        anchor.getLocationInWindow(anchorLoc)
        // Strip origin in window: (sourceLoc.x, sourceLoc.y + sourceHeight - stripHeight).
        // The bar element spans the whole ComposeView, whose window origin is anchorLoc.
        state.stripOffset = IntOffset(
            sourceLoc[0] - anchorLoc[0],
            sourceLoc[1] + sourceHeight - stripHeight - anchorLoc[1]
        )
        state.stripImage = bmp.asImageBitmap()
    }

    companion object {
        private const val EXTRA_ABOVE_PX = 160
    }
}

/** Shape of the floating bar: an inset rounded rect inside the full slot. */
private object BarShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val insetX = with(density) { 16.dp.toPx() }
        val bottom = with(density) { 10.dp.toPx() }
        val barHeight = with(density) { 64.dp.toPx() }
        val rect = Rect(
            left = insetX,
            top = size.height - bottom - barHeight,
            right = size.width - insetX,
            bottom = size.height - bottom
        )
        return Outline.Rounded(RoundRect(rect, CornerRadius(barHeight / 2f)))
    }
}

@Composable
fun GlassNavBarContent(state: GlassNavState) {
    val backdrop = rememberCanvasBackdrop {
        val image = state.stripImage
        if (image != null) {
            drawImage(image, dstOffset = state.stripOffset)
        } else {
            drawRect(state.fallbackColor)
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Glass surface: backdrop effects clipped to the floating bar shape.
        Box(
            Modifier
                .fillMaxSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { BarShape },
                    effects = {
                        vibrancy()
                        blur(6f.dp.toPx())
                        lens(18f.dp.toPx(), 36f.dp.toPx())
                    },
                    onDrawSurface = {
                        drawRect(
                            if (state.darkMode) Color.Black.copy(alpha = 0.30f)
                            else Color.White.copy(alpha = 0.35f)
                        )
                    }
                )
        )

        // Tab buttons, laid out over the bar's rect.
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp)
                .padding(bottom = 10.dp)
                .height(64.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            state.items.forEach { item ->
                GlassNavButton(
                    item = item,
                    selected = item.id == state.selectedId,
                    badge = if (item.id == state.followingId) state.followingBadge else 0,
                    state = state,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun GlassNavButton(
    item: GlassNavItem,
    selected: Boolean,
    badge: Int,
    state: GlassNavState,
    modifier: Modifier = Modifier
) {
    val accent = Color(state.accentColor)
    val contentColor = when {
        selected -> accent
        state.darkMode -> Color.White.copy(alpha = 0.78f)
        else -> Color.Black.copy(alpha = 0.72f)
    }
    Box(
        modifier
            .height(52.dp)
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(if (selected) accent.copy(alpha = 0.22f) else Color.Transparent)
            .combinedClickable(
                onClick = { state.listener?.onSelect(item.id) },
                onLongClick = if (item.liveItem) {
                    { state.listener?.onLiveLongPress() }
                } else null
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Image(
                    painter = painterResource(item.iconRes),
                    contentDescription = item.label,
                    colorFilter = ColorFilter.tint(contentColor),
                    modifier = Modifier.size(24.dp)
                )
                if (badge > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(start = 14.dp)
                            .background(Color(0xFFE5484D), CircleShape)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = if (badge > 99) "99+" else badge.toString(),
                            style = TextStyle(color = Color.White, fontSize = 9.sp)
                        )
                    }
                }
            }
            BasicText(
                text = item.label,
                style = TextStyle(color = contentColor, fontSize = 10.sp),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
