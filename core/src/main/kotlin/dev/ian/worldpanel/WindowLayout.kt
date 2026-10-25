package dev.ian.worldpanel

/** Pure layout result shared by each Minecraft-specific renderer. */
data class WindowLayout(
    val width: Float,
    val height: Float,
    val left: Float,
    val top: Float,
    val contentScale: Float,
    val availableContentWidth: Float,
)

/** Version-independent sizing rules retained from the original FloatingWindow. */
object WindowLayoutCalculator {
    fun calculate(
        rawContentWidth: Float,
        rawContentHeight: Float,
        padding: Float,
        fixedWidth: Float?,
        fixedHeight: Float?,
        autoScaleContent: Boolean,
        autoFitWidth: Boolean,
        autoFitHeight: Boolean,
        alignment: WindowAlignment,
    ): WindowLayout {
        val safePadding = padding.coerceAtLeast(0f)
        val safeRawWidth = rawContentWidth.coerceAtLeast(0f)
        val safeRawHeight = rawContentHeight.coerceAtLeast(0f)
        val maxWidth = fixedWidth ?: (safeRawWidth + safePadding * 2f)
        val maxHeight = fixedHeight ?: (safeRawHeight + safePadding * 2f)
        val availableWidth = (maxWidth - safePadding * 2f).coerceAtLeast(MINIMUM_SIZE)
        val availableHeight = (maxHeight - safePadding * 2f).coerceAtLeast(MINIMUM_SIZE)
        val fitScale = if (autoScaleContent) {
            minOf(
                1f,
                availableWidth / safeRawWidth.coerceAtLeast(MINIMUM_SIZE),
                availableHeight / safeRawHeight.coerceAtLeast(MINIMUM_SIZE),
            )
        } else {
            1f
        }
        val contentWidth = safeRawWidth * fitScale + safePadding * 2f
        val contentHeight = safeRawHeight * fitScale + safePadding * 2f
        val width = when {
            fixedWidth == null -> contentWidth
            autoFitWidth -> minOf(fixedWidth, contentWidth)
            else -> fixedWidth
        }.coerceAtLeast(MINIMUM_SIZE)
        val height = when {
            fixedHeight == null -> contentHeight
            autoFitHeight -> minOf(fixedHeight, contentHeight)
            else -> fixedHeight
        }.coerceAtLeast(MINIMUM_SIZE)

        return WindowLayout(
            width = width,
            height = height,
            left = -width * alignment.xRatio,
            top = -height * alignment.yRatio,
            contentScale = fitScale,
            availableContentWidth = ((width - safePadding * 2f) / fitScale).coerceAtLeast(0f),
        )
    }

    private const val MINIMUM_SIZE = 0.1f
}
