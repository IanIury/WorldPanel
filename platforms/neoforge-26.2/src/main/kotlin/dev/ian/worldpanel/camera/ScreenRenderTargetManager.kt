package dev.ian.worldpanel.camera

import com.mojang.blaze3d.platform.NativeImage
import dev.ian.worldpanel.VideoFeedStyle
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-performance offscreen render target and dynamic texture manager for 2D flat camera and CCTV feeds.
 * Generates true 2D raster feeds on an isolated texture buffer, eliminating 3D geometry protrusion from the window pane.
 *
 * Supports multi-panel texture sharing, update rate throttling, entity tracking, chunk-load validation,
 * and graceful fallback ("NO SIGNAL" / CRT noise) when areas are unloaded or targets are lost.
 */
object ScreenRenderTargetManager {
    const val BUFFER_WIDTH = 256
    const val BUFFER_HEIGHT = 256
    const val MAX_BUFFERS = 32
    const val EXPIRATION_MS = 6000L

    private class FeedBuffer(val location: Identifier) {
        val image: NativeImage = NativeImage(BUFFER_WIDTH, BUFFER_HEIGHT, false)
        val texture: DynamicTexture = DynamicTexture({ location.toString() }, image)
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
    ): Identifier {
        val now = System.currentTimeMillis()

        // 1. Evict expired buffers (unused for > 6 seconds)
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

        val location = Identifier.fromNamespaceAndPath("worldpanel", "dynamic_feed_${feedId.lowercase()}")
        val buffer = buffers.computeIfAbsent(feedId) {
            val buf = FeedBuffer(location)
            Minecraft.getInstance().textureManager.register(location, buf.texture)
            buf
        }

        buffer.lastAccessMs = now

        // Determine throttle interval
        val minIntervalMs: Long = when (source) {
            is CameraSource.Virtual -> {
                if (source.config.state == VirtualCameraState.PAUSED) {
                    return location // Keep frozen frame
                }
                source.config.minUpdateIntervalMs
            }
            else -> 50L // 20 FPS default
        }

        if (now - buffer.lastUpdateMs >= minIntervalMs) {
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
            VideoFeedStyle.CLEAN -> 0xFF0F172A.toInt()
            VideoFeedStyle.SECURITY_CAMERA_CCTV -> 0xFF060B12.toInt()
            VideoFeedStyle.CRT_SCANLINES -> 0xFF020408.toInt()
            VideoFeedStyle.CYBER_GREEN_HOLOGRAM -> 0xFF010D04.toInt()
        }

        for (y in 0 until BUFFER_HEIGHT) {
            for (x in 0 until BUFFER_WIDTH) {
                img.setPixel(x, y, bgArgb)
            }
        }

        // 2. Render Source Data
        when (source) {
            is CameraSource.Spatial -> {
                renderSpatialScene(img, source.view, style, timeSec)
            }
            is CameraSource.Virtual -> {
                if (source.config.state == VirtualCameraState.DISABLED) {
                    renderNoSignal(img, style, timeSec, "CAMERA DISABLED")
                } else {
                    val view = source.resolveView()
                    renderSpatialScene(img, view, style, timeSec)
                }
            }
            is CameraSource.Entity -> {
                val entity = source.entitySupplier()
                if (entity != null && !entity.isRemoved) {
                    val view = source.resolveView()
                    if (view != null) {
                        renderSpatialScene(img, view, style, timeSec)
                    } else {
                        renderEntitySubjectFeed(img, entity, source.spinSpeed, style, timeSec)
                    }
                } else {
                    renderNoSignal(img, style, timeSec, "TARGET LOST")
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
        val mc = Minecraft.getInstance()
        val level = mc.level
        val camBlockPos = BlockPos.containing(view.position)

        // Validate chunk presence for graceful fallback
        if (level == null || !level.hasChunk(camBlockPos.x shr 4, camBlockPos.z shr 4)) {
            renderNoSignal(img, style, timeSec, "UNLOADED CHUNK")
            return
        }

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

        // 1. Horizon & Sky / Ground Separation
        val pitchOffset = (view.pitch * 1.5f).toInt()
        val horizonY = (cy + pitchOffset).coerceIn(15, BUFFER_HEIGHT - 15)

        // Ground grid pattern below horizon
        var yGrid = horizonY + 12
        while (yGrid < BUFFER_HEIGHT - 8) {
            drawLine(img, 8, yGrid, BUFFER_WIDTH - 8, yGrid, dimColor)
            yGrid += 16
        }

        // Ground perspective lines
        val vanishX = cx
        for (i in -4..4) {
            val bottomX = cx + i * 36
            if (bottomX in 0 until BUFFER_WIDTH) {
                drawLine(img, vanishX, horizonY, bottomX, BUFFER_HEIGHT - 1, dimColor)
            }
        }

        // Horizon line
        drawLine(img, 12, horizonY, BUFFER_WIDTH - 12, horizonY, fgColor)

        // 2. Sample and project real nearby terrain blocks
        renderTerrainVoxelProjection(img, level, view, style)

        // 3. Pitch ladder ticks
        for (p in -30..30 step 15) {
            if (p == 0) continue
            val py = cy + ((view.pitch - p) * 1.5f).toInt()
            if (py in 22 until BUFFER_HEIGHT - 22) {
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
        img.setPixel(cx, cy, fgColor)

        // Corner brackets around center
        val bSize = 32
        drawCornerBrackets(img, cx - bSize, cy - bSize, cx + bSize, cy + bSize, 8, fgColor)

        // Compass heading at top
        val yawDeg = (view.yaw % 360f + 360f) % 360f
        val headingStr = getHeadingName(yawDeg)
        drawTinyString(img, "HDG: $headingStr (${yawDeg.toInt()}°)", cx - 36, 10, fgColor)
        drawLine(img, cx - 44, 18, cx + 44, 18, dimColor)
        drawLine(img, cx, 16, cx, 20, fgColor)

        // Coordinate telemetry at bottom
        val pos = view.position
        val posStr = "X: %.1f  Y: %.1f  Z: %.1f".format(pos.x, pos.y, pos.z)
        drawTinyString(img, posStr, 12, BUFFER_HEIGHT - 16, fgColor)

        // Scan and project nearby entities in field of view
        scanNearbyEntities(img, view, fgColor, timeSec)
    }

    private fun renderTerrainVoxelProjection(
        img: NativeImage,
        level: net.minecraft.world.level.Level,
        view: CameraView,
        style: VideoFeedStyle,
    ) {
        val camPos = view.position
        val camX = camPos.x
        val camY = camPos.y
        val camZ = camPos.z

        val yawRad = Math.toRadians(-view.yaw.toDouble())
        val pitchRad = Math.toRadians(-view.pitch.toDouble())
        val cosY = cos(yawRad)
        val sinY = sin(yawRad)
        val cosP = cos(pitchRad)
        val sinP = sin(pitchRad)

        val fov = view.fov.coerceIn(20f, 120f)
        val focal = (BUFFER_WIDTH / 2.0) / kotlin.math.tan(Math.toRadians(fov / 2.0))
        val cx = BUFFER_WIDTH / 2.0
        val cy = BUFFER_HEIGHT / 2.0

        val blockRadius = 10
        val minBx = (camX - blockRadius).toInt()
        val maxBx = (camX + blockRadius).toInt()
        val minBz = (camZ - blockRadius).toInt()
        val maxBz = (camZ + blockRadius).toInt()
        val minBy = (camY - 6).toInt().coerceAtLeast(level.minY)
        val maxBy = (camY + 6).toInt().coerceAtMost(level.maxY)

        val mutPos = BlockPos.MutableBlockPos()

        for (bx in minBx..maxBx step 2) {
            for (bz in minBz..maxBz step 2) {
                // Find highest non-air block
                for (by in maxBy downTo minBy) {
                    mutPos.set(bx, by, bz)
                    val state = level.getBlockState(mutPos)
                    if (state.isAir || state.`is`(Blocks.VOID_AIR) || state.`is`(Blocks.CAVE_AIR)) continue

                    // Relative coords to camera
                    val rx = bx + 0.5 - camX
                    val ry = by + 0.5 - camY
                    val rz = bz + 0.5 - camZ

                    // Rotate by Yaw
                    val x1 = rx * cosY - rz * sinY
                    val z1 = rx * sinY + rz * cosY

                    // Rotate by Pitch
                    val y2 = ry * cosP - z1 * sinP
                    val z2 = ry * sinP + z1 * cosP

                    if (z2 < 1.0 || z2 > 24.0) break // In front of camera check

                    val px = (cx + (x1 / z2) * focal).toInt()
                    val py = (cy - (y2 / z2) * focal).toInt()

                    if (px in 6 until BUFFER_WIDTH - 6 && py in 18 until BUFFER_HEIGHT - 18) {
                        val mapCol = state.getMapColor(level, mutPos).col
                        val blockCol = when (style) {
                            VideoFeedStyle.CYBER_GREEN_HOLOGRAM -> 0xFF00AA44.toInt()
                            VideoFeedStyle.SECURITY_CAMERA_CCTV -> {
                                val r = (((mapCol ushr 16) and 0xFF) * 0.4f).toInt()
                                val g = (((mapCol ushr 8) and 0xFF) * 0.85f).toInt()
                                val b = (((mapCol) and 0xFF) * 0.5f).toInt()
                                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                            }
                            VideoFeedStyle.CRT_SCANLINES -> {
                                val r = (((mapCol ushr 16) and 0xFF) * 0.6f).toInt()
                                val g = (((mapCol ushr 8) and 0xFF) * 0.8f).toInt()
                                val b = (((mapCol) and 0xFF) * 0.9f).toInt()
                                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                            }
                            VideoFeedStyle.CLEAN -> (0xFF shl 24) or mapCol
                        }

                        val dotSize = (focal / (z2 * 2.5)).toInt().coerceIn(1, 4)
                        for (dx in -dotSize..dotSize) {
                            for (dy in -dotSize..dotSize) {
                                val sx = px + dx
                                val sy = py + dy
                                if (sx in 0 until BUFFER_WIDTH && sy in 0 until BUFFER_HEIGHT) {
                                    img.setPixel(sx, sy, blockCol)
                                }
                            }
                        }
                    }
                    break
                }
            }
        }
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
            img.setPixel(x, ekgY + dy, fgColor)
        }

        // Entity status text
        val name = entity.name.string
        val hp = entity.health.toInt()
        val maxHp = entity.maxHealth.toInt()
        drawTinyString(img, "TARGET: $name", 12, 12, fgColor)
        drawTinyString(img, "HP: $hp/$maxHp", 12, 22, if (hp < maxHp / 3) 0xFFEF4444.toInt() else fgColor)
        drawTinyString(img, "STATUS: TRACKED", 12, 32, fgColor)
    }

    private fun renderNoSignal(img: NativeImage, style: VideoFeedStyle, timeSec: Double, reason: String = "NO SIGNAL") {
        val cx = BUFFER_WIDTH / 2
        val cy = BUFFER_HEIGHT / 2
        val fgColor = 0xFFEF4444.toInt()

        // Static noise pattern
        for (y in 0 until BUFFER_HEIGHT step 2) {
            val seed = ((y * 31337 + (timeSec * 1000.0).toLong()) * 1103515245L)
            for (x in 0 until BUFFER_WIDTH step 2) {
                if (((seed xor (x.toLong() * 48271L)) and 7L) == 0L) {
                    val noise = 0x4494A3B8.toInt()
                    img.setPixel(x, y, noise)
                    img.setPixel(x + 1, y, noise)
                }
            }
        }

        drawTinyString(img, reason, cx - (reason.length * 2), cy - 4, fgColor)
        drawCornerBrackets(img, 24, 24, BUFFER_WIDTH - 24, BUFFER_HEIGHT - 24, 12, 0x66EF4444.toInt())
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
            if (targetCount >= 4) break
            val dist = pos.distanceTo(e.position())
            if (dist < 1.0) continue

            // Angle relative to camera yaw
            val dx = e.x - pos.x
            val dz = e.z - pos.z
            val targetYaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
            val diffYaw = ((targetYaw - view.yaw + 180f) % 360f + 360f) % 360f - 180f

            if (kotlin.math.abs(diffYaw) < view.fov / 2f) {
                val screenX = (BUFFER_WIDTH / 2 + (diffYaw / (view.fov / 2f)) * (BUFFER_WIDTH / 2f * 0.8f)).toInt()
                val screenY = (BUFFER_HEIGHT / 2).coerceIn(36, BUFFER_HEIGHT - 36)

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
                val p = img.getPixel(x, y)
                val a = (p shr 24) and 0xFF
                val r = (((p shr 16) and 0xFF) * (1f - scanAlpha)).toInt()
                val g = (((p shr 8) and 0xFF) * (1f - scanAlpha)).toInt()
                val b = ((p and 0xFF) * (1f - scanAlpha)).toInt()
                img.setPixel(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
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
                    val p = img.getPixel(x, y)
                    val a = (p shr 24) and 0xFF
                    val r = (((p shr 16) and 0xFF) * factor).toInt()
                    val g = (((p shr 8) and 0xFF) * factor).toInt()
                    val b = ((p and 0xFF) * factor).toInt()
                    img.setPixel(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
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
                img.setPixel(x, y, color)
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
            img.setPixel(x, y, color)
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
        'K' to intArrayOf(0b101, 0b110, 0b100, 0b110, 0b101),
        'L' to intArrayOf(0b100, 0b100, 0b100, 0b100, 0b111),
        'M' to intArrayOf(0b101, 0b111, 0b101, 0b101, 0b101),
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
