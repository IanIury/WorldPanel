package dev.ian.worldpanel

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A composite 3D object arranging [panelCount] panels in a circular carousel/ring facing outwards.
 * Ideal for roulette item wheels, skill selectors, and rotating 360-degree showcases.
 */
class FloatingCarousel private constructor(
    val panelCount: Int,
    val panelWidth: Float,
    val panelHeight: Float,
    val radius: Double,
    val scale: Float,
    val offsetX: Double,
    val offsetY: Double,
    val offsetZ: Double,
    val panels: Map<Int, FloatingWindow>,
    val spinSpeedY: Float,
    initialTargetPanel: Int?,
    var transitionSpeed: Float,
) {
    private var targetPanel: Int? = initialTargetPanel
    private var currentYaw: Float = if (initialTargetPanel != null) -initialTargetPanel * (360f / panelCount) else 0f
    private var lastTimeSeconds: Double = -1.0

    fun getPanel(index: Int): FloatingWindow? = panels[index]

    fun targetPanel(index: Int, transitionSpeed: Float = 5.0f) {
        this.targetPanel = (index % panelCount + panelCount) % panelCount
        this.transitionSpeed = transitionSpeed
    }

    fun setTargetPanel(index: Int, transitionSpeed: Float = 5.0f) = targetPanel(index, transitionSpeed)

    fun nextPanel(transitionSpeed: Float = 5.0f) {
        val next = ((targetPanel ?: 0) + 1) % panelCount
        targetPanel(next, transitionSpeed)
    }

    fun previousPanel(transitionSpeed: Float = 5.0f) {
        val prev = (((targetPanel ?: 0) - 1) % panelCount + panelCount) % panelCount
        targetPanel(prev, transitionSpeed)
    }

    fun clearTargetPanel() {
        this.targetPanel = null
    }

    fun getTargetPanel(): Int? = targetPanel

    fun render(poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int) {
        if (panels.isEmpty()) return

        val timeSeconds = WindowColor.currentTimeSeconds()

        val rotYaw = if (targetPanel != null) {
            val tp = targetPanel!!
            val targetYaw = -tp * (360f / panelCount)
            val dt = if (lastTimeSeconds > 0.0) (timeSeconds - lastTimeSeconds).toFloat().coerceIn(0.001f, 0.1f) else 0.05f
            val factor = (dt * transitionSpeed).coerceIn(0.01f, 1f)
            currentYaw = Math3DUtils.lerpAngleDegrees(currentYaw, targetYaw, factor)
            currentYaw
        } else {
            val y = (timeSeconds * (spinSpeedY * 50.0)).toFloat() % 360f
            currentYaw = y
            y
        }
        lastTimeSeconds = timeSeconds

        val camera = Minecraft.getInstance().gameRenderer.mainCamera
        val cameraPos = camera.position

        poseStack.pushPose()
        poseStack.translate(offsetX, offsetY, offsetZ)

        if (rotYaw != 0f) poseStack.mulPose(Axis.YP.rotationDegrees(rotYaw))

        for ((index, window) in panels) {
            val angle = index * (360f / panelCount)
            val totalAngle = angle + rotYaw
            val rad = Math.toRadians(totalAngle.toDouble())
            val normX = sin(rad)
            val normZ = cos(rad)

            val faceCenterX = offsetX + normX * radius
            val faceCenterY = offsetY
            val faceCenterZ = offsetZ + normZ * radius

            val toCamX = cameraPos.x - faceCenterX
            val toCamY = cameraPos.y - faceCenterY
            val toCamZ = cameraPos.z - faceCenterZ
            val dist = sqrt(toCamX * toCamX + toCamY * toCamY + toCamZ * toCamZ)

            if (dist > 0.0001 && !window.seeThrough && !window.renderBackFace) {
                val dot = (normX * toCamX + normZ * toCamZ) / dist
                if (dot <= 0.0) {
                    continue
                }
            }

            poseStack.pushPose()
            poseStack.mulPose(Axis.YP.rotationDegrees(angle))
            poseStack.translate(0.0, 0.0, radius)
            window.render(poseStack, buffer, packedLight)
            poseStack.popPose()
        }

        poseStack.popPose()
    }

    class Builder {
        private var panelCount: Int = 8
        private var panelWidth: Float = 32f
        private var panelHeight: Float = 32f
        private var customRadius: Double? = null
        private var scale: Float = 0.025f
        private var offsetX: Double = 0.0
        private var offsetY: Double = 1.3
        private var offsetZ: Double = 0.0
        private var spinSpeedY: Float = 0f
        private var targetPanel: Int? = null
        private var transitionSpeed: Float = 5.0f

        private val panelWindows = mutableMapOf<Int, FloatingWindow>()
        private val panelBuilders = mutableMapOf<Int, (FloatingWindow.Builder) -> Unit>()

        fun setPanelCount(count: Int) = apply {
            require(count >= 3) { "Carousel must have at least 3 panels (got $count)" }
            this.panelCount = count
        }

        fun setPanelSize(width: Float, height: Float) = apply {
            this.panelWidth = width
            this.panelHeight = height
        }

        fun setRadius(radius: Double) = apply { this.customRadius = radius }
        fun setScale(scale: Float) = apply { this.scale = scale }
        fun setOffset(x: Double, y: Double, z: Double) = apply {
            this.offsetX = x
            this.offsetY = y
            this.offsetZ = z
        }

        fun animateSpin(speedY: Float) = apply { this.spinSpeedY = speedY }

        fun targetPanel(index: Int, transitionSpeed: Float = 5.0f) = apply {
            this.targetPanel = index
            this.transitionSpeed = transitionSpeed
        }

        fun setTargetPanel(index: Int, transitionSpeed: Float = 5.0f) = targetPanel(index, transitionSpeed)

        fun clearTargetPanel() = apply { this.targetPanel = null }

        fun configurePanel(index: Int, config: (FloatingWindow.Builder) -> Unit) = apply {
            panelBuilders[index] = config
        }

        fun setPanel(index: Int, window: FloatingWindow) = apply {
            panelWindows[index] = window
        }

        fun build(): FloatingCarousel {
            for ((index, config) in panelBuilders) {
                val b = FloatingWindow.builder()
                    .setSize(panelWidth, panelHeight)
                    .setAlignment(WindowAlignment.CENTER)
                    .setScale(scale)
                    .setFaceCamera(false)
                    .setOffset(0.0, 0.0, 0.0)
                    .setOffsetRelativeToOrientation(true)
                config(b)
                panelWindows[index] = b.build()
            }

            val effectiveRadius = customRadius ?: Math3DUtils.regularPolygonApothem(
                (panelWidth * scale).toDouble(),
                panelCount
            )

            return FloatingCarousel(
                panelCount = panelCount,
                panelWidth = panelWidth,
                panelHeight = panelHeight,
                radius = effectiveRadius,
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
                offsetZ = offsetZ,
                panels = panelWindows.toMap(),
                spinSpeedY = spinSpeedY,
                initialTargetPanel = targetPanel,
                transitionSpeed = transitionSpeed,
            )
        }
    }

    companion object {
        fun builder(): Builder = Builder()
    }
}
