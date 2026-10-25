package dev.ian.worldpanel.layout

import dev.ian.worldpanel.region.ElementTypeFilter
import dev.ian.worldpanel.region.FlowDirection
import dev.ian.worldpanel.region.OverflowPolicy
import dev.ian.worldpanel.region.RegionAlignment
import dev.ian.worldpanel.region.RegionConfig
import dev.ian.worldpanel.region.RegionDefinition
import dev.ian.worldpanel.region.RegionMap
import dev.ian.worldpanel.shape.Point2D
import dev.ian.worldpanel.shape.RectF
import kotlin.math.max
import kotlin.math.min

/** Strategy for distributing elements inside panel shapes and regions. */
enum class ContentLayout {
    /**
     * Elements are placed sequentially in the first available region,
     * advancing to the next region when space is exhausted.
     */
    AUTO,

    /**
     * Elements with explicit target regions are placed into those regions;
     * untargeted elements flow automatically across remaining regions.
     */
    REGION_DIRECTED,

    /**
     * Elements preserve manual offsets where specified; others flow into regions.
     */
    MANUAL
}

/** Wrapper binding an element to an optional target region, manual coordinates, or explicit [ElementPosition]. */
data class LayoutItem<out T : MeasurableElement>(
    val element: T,
    val position: ElementPosition? = null,
    val targetRegion: String? = null,
    val manualX: Float? = null,
    val manualY: Float? = null,
    val requestedScale: Float = 1f,
    val overflow: ContentOverflow? = null,
) {
    constructor(
        element: T,
        targetRegion: String? = null,
        manualX: Float? = null,
        manualY: Float? = null,
        requestedScale: Float = 1f,
    ) : this(
        element = element,
        position = if (manualX != null && manualY != null) ElementPosition.at(manualX, manualY) else null,
        targetRegion = targetRegion,
        manualX = manualX,
        manualY = manualY,
        requestedScale = requestedScale,
        overflow = null,
    )
}

/** Concrete position and bounding box of a placed element. */
data class PlacedElement<out T : MeasurableElement>(
    val element: T,
    val regionName: String?,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val scale: Float = 1f,
    val safeBounds: RectF,
)

/** The result of a layout calculation. */
data class ComputedLayout<out T : MeasurableElement>(
    val placedElements: List<PlacedElement<T>>,
    val omittedElements: List<T>,
)

/**
 * Pure mathematical layout engine for distributing elements inside regions of a panel.
 * Performs measurements, line breaks, region overflow and alignment with zero allocations on steady state.
 */
object RegionLayoutEngine {

    fun <T : MeasurableElement> computeLayout(
        items: List<LayoutItem<T>>,
        panelWidth: Float,
        panelHeight: Float,
        regionMap: RegionMap?,
        fallbackBounds: RectF,
        defaultSpacing: Float = 2f,
        configuredRegionOrder: List<String>? = null,
    ): ComputedLayout<T> {
        if (items.isEmpty()) {
            return ComputedLayout(emptyList(), emptyList())
        }

        // Determine available regions in priority order
        val regions: List<RegionDefinition> = if (regionMap != null && regionMap.allRegions.isNotEmpty()) {
            val orderList = configuredRegionOrder ?: regionMap.allRegions.sortedBy { it.config.order }.map { it.name }
            val resolved = ArrayList<RegionDefinition>(orderList.size)
            for (name in orderList) {
                regionMap.getRegion(name)?.let { resolved.add(it) }
            }
            // Add any remaining regions not mentioned in orderList
            for (reg in regionMap.allRegions) {
                if (resolved.none { it.name == reg.name }) {
                    resolved.add(reg)
                }
            }
            resolved
        } else {
            listOf(
                RegionDefinition(
                    name = "DEFAULT",
                    pixelCount = (fallbackBounds.width * fallbackBounds.height).toInt(),
                    pixelBounds = fallbackBounds,
                    normalizedBounds = RectF(
                        fallbackBounds.left / max(1f, panelWidth),
                        fallbackBounds.top / max(1f, panelHeight),
                        fallbackBounds.right / max(1f, panelWidth),
                        fallbackBounds.bottom / max(1f, panelHeight),
                    ),
                    config = RegionConfig("DEFAULT", spacing = defaultSpacing),
                )
            )
        }

        val placedList = ArrayList<PlacedElement<T>>(items.size)
        val omittedList = ArrayList<T>()

        // Group items into region-directed vs auto-flowing
        val itemsByRegion = HashMap<String, MutableList<LayoutItem<T>>>()
        val autoItems = ArrayList<LayoutItem<T>>()

        for (item in items) {
            val target = item.targetRegion
            if (target != null && regions.any { it.name.equals(target, ignoreCase = true) }) {
                val match = regions.first { it.name.equals(target, ignoreCase = true) }.name
                itemsByRegion.getOrPut(match) { ArrayList() }.add(item)
            } else {
                autoItems.add(item)
            }
        }

        // Layout each region in priority order
        for (reg in regions) {
            val contentArea = reg.resolveContentArea(panelWidth, panelHeight)
            if (contentArea.width <= 0.1f || contentArea.height <= 0.1f) continue

            val regionItems = ArrayList<LayoutItem<T>>()
            itemsByRegion[reg.name]?.let { regionItems.addAll(it) }

            // Pull items from auto-flowing queue that match allowed types
            val it = autoItems.iterator()
            while (it.hasNext()) {
                val candidate = it.next()
                if (isTypeAllowed(candidate.element, reg.config.allowedTypes)) {
                    regionItems.add(candidate)
                    it.remove()
                }
            }

            if (regionItems.isEmpty()) continue

            // Lay out elements inside this region
            val result = layoutRegion(reg, contentArea, regionItems)
            placedList.addAll(result.placed)

            // Any items that overflowed this region and want to try the next region are returned to autoItems
            for (overflowItem in result.overflowed) {
                if (reg.config.overflowPolicy == OverflowPolicy.OVERFLOW_NEXT_REGION) {
                    autoItems.add(0, overflowItem) // higher priority for subsequent regions
                } else {
                    omittedList.add(overflowItem.element)
                }
            }
        }

        // Any leftover auto items that couldn't fit in any region are omitted
        for (item in autoItems) {
            omittedList.add(item.element)
        }

        return ComputedLayout(placedList, omittedList)
    }

    private fun isTypeAllowed(element: MeasurableElement, filter: ElementTypeFilter): Boolean {
        return when (filter) {
            ElementTypeFilter.ALL -> true
            ElementTypeFilter.TEXT_ONLY -> element.isText
            ElementTypeFilter.ICONS_ONLY -> element.isIcon
        }
    }

    private class RegionLayoutResult<T : MeasurableElement>(
        val placed: List<PlacedElement<T>>,
        val overflowed: List<LayoutItem<T>>,
    )

    private fun <T : MeasurableElement> layoutRegion(
        region: RegionDefinition,
        area: RectF,
        items: List<LayoutItem<T>>,
    ): RegionLayoutResult<T> {
        val config = region.config
        val maxCount = config.maxElements ?: Int.MAX_VALUE
        val placed = ArrayList<PlacedElement<T>>()
        val overflowed = ArrayList<LayoutItem<T>>()

        val spacing = config.spacing.coerceAtLeast(0f)

        // Handle manual coordinates first if specified
        val flowingItems = ArrayList<LayoutItem<T>>()
        for (item in items) {
            if (placed.size >= maxCount) {
                overflowed.add(item)
                continue
            }
            val pos = item.position
            val mX = pos?.x ?: item.manualX
            val mY = pos?.y ?: item.manualY
            if (mX != null && mY != null) {
                val scale = item.requestedScale.coerceAtLeast(0.1f)
                val elemW = if (item.element.isSeparator) area.width else item.element.getWidth() * scale
                val elemH = item.element.getHeight() * scale
                val absX = if (pos?.isNormalized == true) area.left + mX * area.width else area.left + mX
                val absY = if (pos?.isNormalized == true) area.top + mY * area.height else area.top + mY

                // Check bounds
                if (absX + elemW <= area.right + 0.5f && absY + elemH <= area.bottom + 0.5f) {
                    placed.add(
                        PlacedElement(
                            element = item.element,
                            regionName = region.name,
                            x = absX,
                            y = absY,
                            width = elemW,
                            height = elemH,
                            scale = scale,
                            safeBounds = area,
                        )
                    )
                } else {
                    overflowed.add(item)
                }
            } else {
                flowingItems.add(item)
            }
        }

        if (flowingItems.isEmpty()) {
            return RegionLayoutResult(placed, overflowed)
        }

        // Automatic flow layout: lines of elements
        class RowElement(val item: LayoutItem<T>, val scale: Float, val w: Float, val h: Float)
        class Row(val elements: MutableList<RowElement> = ArrayList()) {
            val width: Float
                get() = if (elements.isEmpty()) 0f else elements.sumOf { it.w.toDouble() }.toFloat() + spacing * (elements.size - 1)
            val height: Float
                get() = elements.maxOfOrNull { it.h } ?: 0f
        }

        val rows = ArrayList<Row>()
        var currentRow = Row()

        for (item in flowingItems) {
            if (placed.size + rows.sumOf { it.elements.size } + currentRow.elements.size >= maxCount) {
                overflowed.add(item)
                continue
            }

            var scale = item.requestedScale
            var elemW = if (item.element.isSeparator) area.width else item.element.getWidth() * scale
            var elemH = item.element.getHeight() * scale

            // Check if element is wider than entire area and can scale down
            if (elemW > area.width && config.overflowPolicy == OverflowPolicy.SCALE_DOWN && !item.element.isSeparator) {
                val scaleFactor = (area.width / max(0.1f, item.element.getWidth())).coerceIn(config.minScale, config.maxScale)
                scale = scaleFactor
                elemW = item.element.getWidth() * scale
                elemH = item.element.getHeight() * scale
            }

            // Check if item fits in current row
            val neededWidth = if (currentRow.elements.isEmpty()) elemW else currentRow.width + spacing + elemW

            if (neededWidth <= area.width + 0.1f) {
                currentRow.elements.add(RowElement(item, scale, elemW, elemH))
            } else {
                // Must wrap to next row if current row has elements
                if (currentRow.elements.isNotEmpty()) {
                    rows.add(currentRow)
                    currentRow = Row()
                }

                // Check if single element can fit in a brand new row
                if (elemW <= area.width + 0.1f) {
                    currentRow.elements.add(RowElement(item, scale, elemW, elemH))
                } else {
                    // Element cannot even fit in an empty row
                    overflowed.add(item)
                }
            }
        }

        if (currentRow.elements.isNotEmpty()) {
            rows.add(currentRow)
        }

        // Check total height against area.height
        val totalRowsHeight = if (rows.isEmpty()) 0f else rows.sumOf { it.height.toDouble() }.toFloat() + spacing * (rows.size - 1)

        val startY = when (config.verticalAlignment) {
            RegionAlignment.START -> area.top
            RegionAlignment.CENTER -> area.top + max(0f, (area.height - totalRowsHeight) / 2f)
            RegionAlignment.END -> area.bottom - totalRowsHeight
        }

        var currentY = startY

        for (row in rows) {
            // Check vertical boundary
            if (currentY + row.height > area.bottom + 0.5f) {
                // Rows beyond bottom cannot fit
                for (re in row.elements) {
                    overflowed.add(re.item)
                }
                continue
            }

            val startX = when (config.horizontalAlignment) {
                RegionAlignment.START -> area.left
                RegionAlignment.CENTER -> area.left + max(0f, (area.width - row.width) / 2f)
                RegionAlignment.END -> area.right - row.width
            }

            var currentX = startX
            for (re in row.elements) {
                val elemY = currentY + (row.height - re.h) / 2f // center vertically within row
                placed.add(
                    PlacedElement(
                        element = re.item.element,
                        regionName = region.name,
                        x = currentX,
                        y = elemY,
                        width = re.w,
                        height = re.h,
                        scale = re.scale,
                        safeBounds = area,
                    )
                )
                currentX += re.w + spacing
            }
            currentY += row.height + spacing
        }

        return RegionLayoutResult(placed, overflowed)
    }
}
