package dev.ian.worldpanel.region

import dev.ian.worldpanel.shape.RectF
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentHashMap

/**
 * Immutable, preprocessed map of content regions for a panel.
 *
 * Regions are identified either by exact technical colors in a secondary map image
 * or defined programmatically via [RegionMap.Builder].
 */
class RegionMap internal constructor(
    val mapWidth: Int,
    val mapHeight: Int,
    private val regions: Map<String, RegionDefinition>,
) {
    /** All recognized region names in this map. */
    val regionNames: Set<String> get() = regions.keys

    /** All region definitions. */
    val allRegions: Collection<RegionDefinition> get() = regions.values

    /** Retrieves the region definition for [name], or null if absent. */
    fun getRegion(name: String): RegionDefinition? = regions[name]

    /** Checks whether a region with [name] exists and has non-empty area. */
    fun hasRegion(name: String): Boolean = regions.containsKey(name)

    /** Validates that this region map matches the dimensions of the main appearance image. */
    fun validateDimensions(expectedWidth: Int, expectedHeight: Int): Boolean =
        mapWidth == expectedWidth && mapHeight == expectedHeight

    companion object {
        val EMPTY = RegionMap(0, 0, emptyMap())

        /**
         * Creates a [Builder] for programmatically configuring regions.
         */
        fun builder(mapWidth: Int = 100, mapHeight: Int = 100): Builder =
            Builder(mapWidth, mapHeight)

        /**
         * Parses a region map from a raw ARGB pixel array.
         *
         * @param width Width of the pixel grid.
         * @param height Height of the pixel grid.
         * @param pixels Array of size width * height containing 32-bit ARGB values.
         * @param colorToRegion Mapping from exact 32-bit ARGB (or 24-bit 0xRRGGBB) to region name.
         * @param configs Optional per-region layout rules.
         */
        fun fromPixels(
            width: Int,
            height: Int,
            pixels: IntArray,
            colorToRegion: Map<Int, String>,
            configs: Map<String, RegionConfig> = emptyMap(),
        ): RegionMap {
            require(width > 0 && height > 0) { "Dimensions must be positive." }
            require(pixels.size >= width * height) { "Pixels array size does not match dimensions." }

            // Normalize color map keys to 0x00FFFFFF (ignoring alpha channel for match if opaque)
            // but also support full ARGB.
            val normalizedColorMap = HashMap<Int, String>()
            for ((color, name) in colorToRegion) {
                normalizedColorMap[color] = name
                normalizedColorMap[color and 0x00FFFFFF] = name
            }

            class Accumulator {
                var count = 0
                var minX = Int.MAX_VALUE
                var minY = Int.MAX_VALUE
                var maxX = Int.MIN_VALUE
                var maxY = Int.MIN_VALUE

                fun add(x: Int, y: Int) {
                    count++
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }

            val accumulators = HashMap<String, Accumulator>()

            var index = 0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val argb = pixels[index++]
                    val alpha = (argb shr 24) and 0xFF
                    // Transparent pixels (alpha == 0) never define a content region
                    if (alpha == 0) continue

                    val regionName = normalizedColorMap[argb] ?: normalizedColorMap[argb and 0x00FFFFFF]
                    if (regionName != null) {
                        val acc = accumulators.getOrPut(regionName) { Accumulator() }
                        acc.add(x, y)
                    }
                }
            }

            val resultMap = HashMap<String, RegionDefinition>()
            for ((name, acc) in accumulators) {
                if (acc.count > 0) {
                    val pixelBounds = RectF(
                        left = acc.minX.toFloat(),
                        top = acc.minY.toFloat(),
                        right = (acc.maxX + 1).toFloat(),
                        bottom = (acc.maxY + 1).toFloat(),
                    )
                    val normalizedBounds = RectF(
                        left = acc.minX.toFloat() / width.toFloat(),
                        top = acc.minY.toFloat() / height.toFloat(),
                        right = (acc.maxX + 1).toFloat() / width.toFloat(),
                        bottom = (acc.maxY + 1).toFloat() / height.toFloat(),
                    )
                    val config = configs[name] ?: RegionConfig(name)
                    resultMap[name] = RegionDefinition(
                        name = name,
                        pixelCount = acc.count,
                        pixelBounds = pixelBounds,
                        normalizedBounds = normalizedBounds,
                        config = config,
                    )
                }
            }

            return RegionMap(width, height, resultMap)
        }

        /**
         * Parses a region map from a [BufferedImage].
         */
        fun fromBufferedImage(
            image: BufferedImage,
            colorToRegion: Map<Int, String>,
            configs: Map<String, RegionConfig> = emptyMap(),
        ): RegionMap {
            val width = image.width
            val height = image.height
            val pixels = IntArray(width * height)
            image.getRGB(0, 0, width, height, pixels, 0, width)
            return fromPixels(width, height, pixels, colorToRegion, configs)
        }
    }

    /** Fluent builder for creating programmatically defined region maps. */
    class Builder(private val mapWidth: Int, private val mapHeight: Int) {
        private val regions = HashMap<String, RegionDefinition>()

        /**
         * Adds a region using normalized coordinates [0..1] relative to the panel.
         */
        fun addNormalizedRegion(
            name: String,
            normalizedBounds: RectF,
            config: RegionConfig = RegionConfig(name),
        ) = apply {
            val pixelBounds = RectF(
                left = normalizedBounds.left * mapWidth,
                top = normalizedBounds.top * mapHeight,
                right = normalizedBounds.right * mapWidth,
                bottom = normalizedBounds.bottom * mapHeight,
            )
            regions[name] = RegionDefinition(
                name = name,
                pixelCount = (pixelBounds.width * pixelBounds.height).toInt(),
                pixelBounds = pixelBounds,
                normalizedBounds = normalizedBounds,
                config = config,
            )
        }

        /**
         * Adds a region using pixel coordinates in [mapWidth] x [mapHeight].
         */
        fun addPixelRegion(
            name: String,
            pixelBounds: RectF,
            config: RegionConfig = RegionConfig(name),
        ) = apply {
            val normalizedBounds = RectF(
                left = pixelBounds.left / mapWidth.toFloat(),
                top = pixelBounds.top / mapHeight.toFloat(),
                right = pixelBounds.right / mapWidth.toFloat(),
                bottom = pixelBounds.bottom / mapHeight.toFloat(),
            )
            regions[name] = RegionDefinition(
                name = name,
                pixelCount = (pixelBounds.width * pixelBounds.height).toInt(),
                pixelBounds = pixelBounds,
                normalizedBounds = normalizedBounds,
                config = config,
            )
        }

        fun build(): RegionMap = RegionMap(mapWidth, mapHeight, regions)
    }
}

/**
 * Thread-safe cache for preprocessed region maps to ensure zero per-frame parsing.
 */
object RegionMapCache {
    private val cache = ConcurrentHashMap<String, RegionMap>()

    fun getOrLoad(key: String, loader: () -> RegionMap): RegionMap =
        cache.computeIfAbsent(key) { loader() }

    fun put(key: String, regionMap: RegionMap) {
        cache[key] = regionMap
    }

    fun invalidate(key: String) {
        cache.remove(key)
    }

    fun clear() {
        cache.clear()
    }
}
