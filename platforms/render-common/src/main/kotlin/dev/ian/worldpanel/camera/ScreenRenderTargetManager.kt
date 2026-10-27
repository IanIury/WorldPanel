package dev.ian.worldpanel.camera

import com.mojang.blaze3d.platform.NativeImage
import dev.ian.worldpanel.VideoFeedStyle
import dev.ian.worldpanel.WindowColor
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-performance offscreen render target and dynamic texture manager for 2D flat camera and CCTV feeds.
 * Generates true 2D raster feeds on an isolated buffer, eliminating 3D geometry protrusion from the window pane.
 */
object ScreenRenderTargetManager {
    const val BUFFER_WIDTH = 256
    const val BUFFER_HEIGHT = 256
    const val MAX_BUFFERS = 16
    const val EXPIRATION_MS = 5000L

    private class FeedBuffer(val location: ResourceLocation) {
        val image: NativeImage = NativeImage(BUFFER_WIDTH, BUFFER_HEIGHT, false)
        val texture: DynamicTexture = DynamicTexture(image)
        var lastUpdateMs: Long = 0L
        var lastAccessMs: Long = System.currentTimeMillis()

        fun upload() {
            texture.upload()
        }

        fun destroy() {
            try {
                Minecraft.getInstance().textureManager.release(location)
            } catch (_: Throwable) {}
            try {
                texture.close()
            } catch (_: Throwable) {}
        }
    }

    private val buffers = ConcurrentHashMap<String, FeedBuffer>()

    fun getFeedTexture(
        feedId: String,
        source: CameraSource,
        style: VideoFeedStyle,
    ): ResourceLocation {
        val now = System.currentTimeMillis()

        // 1. Evict expired buffers (unused for > 5 seconds)
        val iterator = buffers.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key != feedId && (now - entry.value.lastAccessMs) > EXPIRATION_MS) {
                entry.value.destroy()
                iterator.remove()
            }
        }

        // 2. Enforce maximum buffer capacity using LRU eviction
        if (!buffers.containsKey(feedId) && buffers.size >= MAX_BUFFERS) {
            val oldest = buffers.minByOrNull { it.value.lastAccessMs }
            if (oldest != null) {
                buffers.remove(oldest.key)?.destroy()
            }
        }

        val location = ResourceLocation.fromNamespaceAndPath("worldpanel", "dynamic_feed_${feedId.lowercase()}")
        val buffer = buffers.computeIfAbsent(feedId) {
            val buf = FeedBuffer(location)
            Minecraft.getInstance().textureManager.register(location, buf.texture)
            buf
        }

        buffer.lastAccessMs = now

        if (now - buffer.lastUpdateMs >= 50L) { // 20 updates per second
            buffer.lastUpdateMs = now
            renderFeedToImage(buffer.image, source, style, now)
            buffer.upload()
        }

        return location
    }

    private fun renderFeedToImage(
        img: NativeImage,
        source: CameraSource,
        style: VideoFeedStyle,
        nowMs: Long,
    ) {
        val timeSec = nowMs / 1000.0

        // 1. Background Fill
        val bgArgb = when (style) {
            VideoFeedStyle.CLEAN -> 0xFF10141D.toInt()
            VideoFeedStyle.SECURITY_CAMERA_CCTV -> 0xFF080D14.toInt()
            VideoFeedStyle.CRT_SCANLINES -> 0xFF04060A.toInt()
            VideoFeedStyle.CYBER_GREEN_HOLOGRAM -> 0xFF021006.toInt()
        }

        for (y in 0 until BUFFER_HEIGHT) {
            for (x in 0 until BUFFER_WIDTH) {
                img.setPixelRGBA(x, y, bgArgb)
            }
        }

        // 2. Render Source Data
        when (source) {
            is CameraSource.Spatial -> {
                renderSpatialScene(img, source.view, style, timeSec)
            }
            is CameraSource.Entity -> {
                val entity = source.entitySupplier()
                if (entity != null) {
                    if (source.useEyePosition) {
                        val view = CameraView(
                            position = entity.eyePosition,
                            yaw = entity.yRot,
                            pitch = entity.xRot,
                            fov = source.fov
                        )
                        renderSpatialScene(img, view, style, timeSec)
                    } else {
                        renderEntitySubjectFeed(img, entity, source.spinSpeed, style, timeSec)
                    }
                } else {
                    renderNoSignal(img, style, timeSec)
                }
            }
        }

        // 3. Post-processing Scanlines & Raster
        applyRasterEffects(img, style, timeSec)
    }

    private fun renderSpatialScene(
        img: NativeImage,
        view: CameraView,
        style: VideoFeedStyle,
        timeSec: Double,
    ) {
        val fgColor = when (style) {
            VideoFeedStyle.CYBER_GREEN_HOLOGRAM -> 0xFF00FF66.toInt()
            VideoFeedStyle.SECURITY_CAMERA_CCTV -> 0xFF4ADE80.toInt()
            VideoFeedStyle.CRT_SCANLINES -> 0xFF38BDF8.toInt()
            VideoFeedStyle.CLEAN -> 0xFFE2E8F0.toInt()
        }
        val dimColor = when (style) {
            VideoFeedStyle.CYBER_GREEN_HOLOGRAM -> 0x5500AA44.toInt()
            VideoFeedStyle.SECURITY_CAMERA_CCTV -> 0x55225533.toInt()
            VideoFeedStyle.CRT_SCANLINES -> 0x551E3A5F.toInt()
            VideoFeedStyle.CLEAN -> 0x55334155.toInt()
        }

        val cx = BUFFER_WIDTH / 2
        val cy = BUFFER_HEIGHT / 2

        // Horizon line with pitch tilt
        val pitchRad = Math.toRadians(view.pitch.toDouble())
        val pitchOffset = (view.pitch * 1.5f).toInt()
        val horizonY = (cy + pitchOffset).coerceIn(20, BUFFER_HEIGHT - 20)

        // Ground grid pattern below horizon
        var yGrid = horizonY + 12
        while (yGrid < BUFFER_HEIGHT - 10) {
            drawLine(img, 10, yGrid, BUFFER_WIDTH - 10, yGrid, dimColor)
            yGrid += 16
        }

        // Horizon line
        drawLine(img, 20, horizonY, BUFFER_WIDTH - 20, horizonY, fgColor)

        // Pitch ladder ticks
        for (p in -30..30 step 15) {
            if (p == 0) continue
            val py = cy + ((view.pitch - p) * 1.5f).toInt()
            if (py in 25 until BUFFER_HEIGHT - 25) {
                drawLine(img, cx - 18, py, cx - 6, py, fgColor)
                drawLine(img, cx + 6, py, cx + 18, py, fgColor)
                drawTinyString(img, "${kotlin.math.abs(p)}", cx + 22, py - 3, dimColor)
            }
        }

        // Center reticle
        drawLine(img, cx - 12, cy, cx - 4, cy, fgColor)
        drawLine(img, cx + 4, cy, cx + 12, cy, fgColor)
        drawLine(img, cx, cy - 12, cx, cy - 4, fgColor)
        drawLine(img, cx, cy + 4, cx, cy + 12, fgColor)
        img.setPixelRGBA(cx, cy, fgColor)

        // Corner brackets around center
        val bSize = 32
        drawCornerBrackets(img, cx - bSize, cy - bSize, cx + bSize, cy + bSize, 8, fgColor)

        // Compass heading at top
        val yawDeg = (view.yaw % 360f + 360f) % 360f
        val headingStr = getHeadingName(yawDeg)
        drawTinyString(img, "HDG: $headingStr (${yawDeg.toInt()}°)", cx - 36, 12, fgColor)
        drawLine(img, cx - 40, 20, cx + 40, 20, dimColor)
        drawLine(img, cx, 18, cx, 22, fgColor)

        // Coordinate telemetry at bottom
        val pos = view.position
        val posStr = "X: %.1f  Y: %.1f  Z: %.1f".format(pos.x, pos.y, pos.z)
        drawTinyString(img, posStr, 12, BUFFER_HEIGHT - 18, fgColor)

        // Scan nearby entities in field of view
        scanNearbyEntities(img, view, fgColor, timeSec)
    }

    private fun renderEntitySubjectFeed(
        img: NativeImage,
        entity: LivingEntity,
        spinSpeed: Float,
        style: VideoFeedStyle,
        timeSec: Double,
    ) {
        val fgColor = when (style) {
            VideoFeedStyle.CYBER_GREEN_HOLOGRAM -> 0xFF00FF66.toInt()
            VideoFeedStyle.SECURITY_CAMERA_CCTV -> 0xFF38BDF8.toInt()
            VideoFeedStyle.CRT_SCANLINES -> 0xFFF59E0B.toInt()
            VideoFeedStyle.CLEAN -> 0xFFE2E8F0.toInt()
        }
        val dimColor = when (style) {
            VideoFeedStyle.CYBER_GREEN_HOLOGRAM -> 0x4400FF66.toInt()
            VideoFeedStyle.SECURITY_CAMERA_CCTV -> 0x4438BDF8.toInt()
            VideoFeedStyle.CRT_SCANLINES -> 0x44F59E0B.toInt()
            VideoFeedStyle.CLEAN -> 0x44E2E8F0.toInt()
        }

        val cx = BUFFER_WIDTH / 2
        val cy = BUFFER_HEIGHT / 2

        // Subject framing box
        val boxW = 50
        val boxH = 70
        drawCornerBrackets(img, cx - boxW, cy - boxH, cx + boxW, cy + boxH, 12, fgColor)

        // Silhouette / Biometric avatar wireframe
        val rotAngle = (timeSec * spinSpeed * 2.0).toFloat()
        val cosR = cos(rotAngle)
        val sinR = sin(rotAngle)

        // Head circle
        val headY = cy - 40
        drawCircle(img, cx, headY, 14, fgColor)

        // Torso wireframe
        val torsoTop = headY + 14
        val torsoBottom = torsoTop + 42
        val shoulderW = (22 * cosR).toInt().coerceAtLeast(6)
        drawLine(img, cx - shoulderW, torsoTop, cx + shoulderW, torsoTop, fgColor)
        drawLine(img, cx - shoulderW, torsoTop, cx - (shoulderW * 0.7).toInt(), torsoBottom, fgColor)
        drawLine(img, cx + shoulderW, torsoTop, cx + (shoulderW * 0.7).toInt(), torsoBottom, fgColor)
        drawLine(img, cx - (shoulderW * 0.7).toInt(), torsoBottom, cx + (shoulderW * 0.7).toInt(), torsoBottom, fgColor)

        // Legs wireframe
        val hipW = (shoulderW * 0.7).toInt()
        drawLine(img, cx - hipW / 2, torsoBottom, cx - hipW / 2, torsoBottom + 35, fgColor)
        drawLine(img, cx + hipW / 2, torsoBottom, cx + hipW / 2, torsoBottom + 35, fgColor)

        // Biometric EKG pulse wave at bottom
        val ekgY = BUFFER_HEIGHT - 32
        drawLine(img, 10, ekgY, BUFFER_WIDTH - 10, ekgY, dimColor)
        for (x in 20 until BUFFER_WIDTH - 20) {
            val wavePhase = ((x + timeSec * 80.0) % 90.0).toFloat()
            val dy = when {
                wavePhase in 40.0..45.0 -> -((wavePhase - 40.0) * 3.0).toInt()
                wavePhase in 45.0..50.0 -> ((wavePhase - 45.0) * 6.0 - 15.0).toInt()
                wavePhase in 50.0..55.0 -> -((wavePhase - 50.0) * 4.0 - 15.0).toInt()
                else -> 0
            }
            img.setPixelRGBA(x, ekgY + dy, fgColor)
        }

        // Entity status text
        val name = entity.name.string
        val hp = entity.health.toInt()
        val maxHp = entity.maxHealth.toInt()
        drawTinyString(img, "TARGET: $name", 12, 12, fgColor)
        drawTinyString(img, "HP: $hp/$maxHp", 12, 22, if (hp < maxHp / 3) 0xFFEF4444.toInt() else fgColor)
        drawTinyString(img, "STATUS: TRACKED", 12, 32, fgColor)
    }

    private fun renderNoSignal(img: NativeImage, style: VideoFeedStyle, timeSec: Double) {
        val cx = BUFFER_WIDTH / 2
        val cy = BUFFER_HEIGHT / 2
        val fgColor = 0xFFEF4444.toInt()
        drawTinyString(img, "NO SIGNAL", cx - 24, cy - 4, fgColor)
        drawCornerBrackets(img, 20, 20, BUFFER_WIDTH - 20, BUFFER_HEIGHT - 20, 10, 0x44EF4444.toInt())
    }

    private fun scanNearbyEntities(
        img: NativeImage,
        view: CameraView,
        color: Int,
        timeSec: Double,
    ) {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val pos = view.position
        val box = AABB(pos.x - 24.0, pos.y - 12.0, pos.z - 24.0, pos.x + 24.0, pos.y + 12.0, pos.z + 24.0)
        val entities = level.getEntitiesOfClass(LivingEntity::class.java, box) { it != mc.player }

        var targetCount = 0
        for (e in entities) {
            if (targetCount >= 3) break
            val dist = pos.distanceTo(e.position())
            if (dist < 1.0) continue

            // Angle relative to camera yaw
            val dx = e.x - pos.x
            val dz = e.z - pos.z
            val targetYaw = Math.toDegrees(kotlin.math.atan2(-dx, dz)).toFloat()
            val diffYaw = ((targetYaw - view.yaw + 180f) % 360f + 360f) % 360f - 180f

            if (kotlin.math.abs(diffYaw) < view.fov / 2f) {
                val screenX = (BUFFER_WIDTH / 2 + (diffYaw / (view.fov / 2f)) * (BUFFER_WIDTH / 2f * 0.8f)).toInt()
                val screenY = (BUFFER_HEIGHT / 2).coerceIn(40, BUFFER_HEIGHT - 40)

                drawCornerBrackets(img, screenX - 12, screenY - 16, screenX + 12, screenY + 16, 4, color)
                val label = "${e.type.description.string.take(8)} ${dist.toInt()}m"
                drawTinyString(img, label, screenX - 16, screenY + 18, color)
                targetCount++
            }
        }
    }

    private fun applyRasterEffects(img: NativeImage, style: VideoFeedStyle, timeSec: Double) {
        val scanAlpha = when (style) {
            VideoFeedStyle.CRT_SCANLINES -> 0.25f
            VideoFeedStyle.SECURITY_CAMERA_CCTV -> 0.12f
            VideoFeedStyle.CYBER_GREEN_HOLOGRAM -> 0.18f
            VideoFeedStyle.CLEAN -> 0.04f
        }

        // Horizontal scanlines
        for (y in 0 until BUFFER_HEIGHT step 2) {
            for (x in 0 until BUFFER_WIDTH) {
                val p = img.getPixelRGBA(x, y)
                val a = (p shr 24) and 0xFF
                val r = (((p shr 16) and 0xFF) * (1f - scanAlpha)).toInt()
                val g = (((p shr 8) and 0xFF) * (1f - scanAlpha)).toInt()
                val b = ((p and 0xFF) * (1f - scanAlpha)).toInt()
                img.setPixelRGBA(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }

        // Vignette edges
        for (y in 0 until BUFFER_HEIGHT) {
            val dy = kotlin.math.abs(y - BUFFER_HEIGHT / 2f) / (BUFFER_HEIGHT / 2f)
            for (x in 0 until BUFFER_WIDTH) {
                val dx = kotlin.math.abs(x - BUFFER_WIDTH / 2f) / (BUFFER_WIDTH / 2f)
                val vig = (dx * dx + dy * dy) * 0.4f
                if (vig > 0.15f) {
                    val factor = (1f - (vig - 0.15f)).coerceIn(0.2f, 1f)
                    val p = img.getPixelRGBA(x, y)
                    val a = (p shr 24) and 0xFF
                    val r = (((p shr 16) and 0xFF) * factor).toInt()
                    val g = (((p shr 8) and 0xFF) * factor).toInt()
                    val b = ((p and 0xFF) * factor).toInt()
                    img.setPixelRGBA(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
                }
            }
        }
    }

    // --- Drawing primitives ---

    private fun drawLine(img: NativeImage, x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
        var x = x0
        var y = y0
        val dx = kotlin.math.abs(x1 - x0)
        val dy = kotlin.math.abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx - dy

        while (true) {
            if (x in 0 until BUFFER_WIDTH && y in 0 until BUFFER_HEIGHT) {
                img.setPixelRGBA(x, y, color)
            }
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 > -dy) {
                err -= dy
                x += sx
            }
            if (e2 < dx) {
                err += dx
                y += sy
            }
        }
    }

    private fun drawCircle(img: NativeImage, cx: Int, cy: Int, r: Int, color: Int) {
        var x = r
        var y = 0
        var err = 0

        while (x >= y) {
            plotPixel(img, cx + x, cy + y, color)
            plotPixel(img, cx + y, cy + x, color)
            plotPixel(img, cx - y, cy + x, color)
            plotPixel(img, cx - x, cy + y, color)
            plotPixel(img, cx - x, cy - y, color)
            plotPixel(img, cx - y, cy - x, color)
            plotPixel(img, cx + y, cy - x, color)
            plotPixel(img, cx + x, cy - y, color)

            y++
            err += 1 + 2 * y
            if (2 * (err - x) + 1 > 0) {
                x--
                err += 1 - 2 * x
            }
        }
    }

    private fun plotPixel(img: NativeImage, x: Int, y: Int, color: Int) {
        if (x in 0 until BUFFER_WIDTH && y in 0 until BUFFER_HEIGHT) {
            img.setPixelRGBA(x, y, color)
        }
    }

    private fun drawCornerBrackets(img: NativeImage, x0: Int, y0: Int, x1: Int, y1: Int, len: Int, color: Int) {
        // Top-left
        drawLine(img, x0, y0, x0 + len, y0, color)
        drawLine(img, x0, y0, x0, y0 + len, color)
        // Top-right
        drawLine(img, x1, y0, x1 - len, y0, color)
        drawLine(img, x1, y0, x1, y0 + len, color)
        // Bottom-left
        drawLine(img, x0, y1, x0 + len, y1, color)
        drawLine(img, x0, y1, x0, y1 - len, color)
        // Bottom-right
        drawLine(img, x1, y1, x1 - len, y1, color)
        drawLine(img, x1, y1, x1, y1 - len, color)
    }

    private fun getHeadingName(yaw: Float): String = when (((yaw + 22.5f) % 360f / 45f).toInt()) {
        0 -> "S"
        1 -> "SW"
        2 -> "W"
        3 -> "NW"
        4 -> "N"
        5 -> "NE"
        6 -> "E"
        7 -> "SE"
        else -> "S"
    }

    // 3x5 compact bitmap font for crisp in-texture telemetry
    private val FONT_PATTERNS = mapOf(
        '0' to intArrayOf(0b111, 0b101, 0b101, 0b101, 0b111),
        '1' to intArrayOf(0b010, 0b110, 0b010, 0b010, 0b111),
        '2' to intArrayOf(0b111, 0b001, 0b111, 0b100, 0b111),
        '3' to intArrayOf(0b111, 0b001, 0b111, 0b001, 0b111),
        '4' to intArrayOf(0b101, 0b101, 0b111, 0b001, 0b001),
        '5' to intArrayOf(0b111, 0b100, 0b111, 0b001, 0b111),
        '6' to intArrayOf(0b111, 0b100, 0b111, 0b101, 0b111),
        '7' to intArrayOf(0b111, 0b001, 0b010, 0b010, 0b010),
        '8' to intArrayOf(0b111, 0b101, 0b111, 0b101, 0b111),
        '9' to intArrayOf(0b111, 0b101, 0b111, 0b001, 0b111),
        'A' to intArrayOf(0b111, 0b101, 0b111, 0b101, 0b101),
        'B' to intArrayOf(0b110, 0b101, 0b110, 0b101, 0b110),
        'C' to intArrayOf(0b111, 0b100, 0b100, 0b100, 0b111),
        'D' to intArrayOf(0b110, 0b101, 0b101, 0b101, 0b110),
        'E' to intArrayOf(0b111, 0b100, 0b111, 0b100, 0b111),
        'F' to intArrayOf(0b111, 0b100, 0b110, 0b100, 0b100),
        'G' to intArrayOf(0b111, 0b100, 0b101, 0b101, 0b111),
        'H' to intArrayOf(0b101, 0b101, 0b111, 0b101, 0b101),
        'I' to intArrayOf(0b111, 0b010, 0b010, 0b010, 0b111),
        'L' to intArrayOf(0b100, 0b100, 0b100, 0b100, 0b111),
        'N' to intArrayOf(0b111, 0b101, 0b101, 0b101, 0b101),
        'O' to intArrayOf(0b111, 0b101, 0b101, 0b101, 0b111),
        'P' to intArrayOf(0b111, 0b101, 0b111, 0b100, 0b100),
        'R' to intArrayOf(0b110, 0b101, 0b110, 0b101, 0b101),
        'S' to intArrayOf(0b111, 0b100, 0b111, 0b001, 0b111),
        'T' to intArrayOf(0b111, 0b010, 0b010, 0b010, 0b010),
        'U' to intArrayOf(0b101, 0b101, 0b101, 0b101, 0b111),
        'V' to intArrayOf(0b101, 0b101, 0b101, 0b101, 0b010),
        'W' to intArrayOf(0b101, 0b101, 0b101, 0b111, 0b101),
        'X' to intArrayOf(0b101, 0b101, 0b010, 0b101, 0b101),
        'Y' to intArrayOf(0b101, 0b101, 0b010, 0b010, 0b010),
        'Z' to intArrayOf(0b111, 0b001, 0b010, 0b100, 0b111),
        ':' to intArrayOf(0b000, 0b010, 0b000, 0b010, 0b000),
        '-' to intArrayOf(0b000, 0b000, 0b111, 0b000, 0b000),
        '.' to intArrayOf(0b000, 0b000, 0b000, 0b000, 0b010),
        '/' to intArrayOf(0b001, 0b001, 0b010, 0b100, 0b100),
        '(' to intArrayOf(0b010, 0b100, 0b100, 0b100, 0b010),
        ')' to intArrayOf(0b010, 0b001, 0b001, 0b001, 0b010),
        '°' to intArrayOf(0b110, 0b110, 0b000, 0b000, 0b000),
    )

    private fun drawTinyString(img: NativeImage, text: String, startX: Int, startY: Int, color: Int) {
        var x = startX
        for (ch in text.uppercase()) {
            if (ch == ' ') {
                x += 3
                continue
            }
            val pattern = FONT_PATTERNS[ch]
            if (pattern != null) {
                for (row in 0 until 5) {
                    val line = pattern[row]
                    for (col in 0 until 3) {
                        if ((line and (1 shl (2 - col))) != 0) {
                            plotPixel(img, x + col, startY + row, color)
                        }
                    }
                }
                x += 4
            } else {
                x += 3
            }
        }
    }
}
