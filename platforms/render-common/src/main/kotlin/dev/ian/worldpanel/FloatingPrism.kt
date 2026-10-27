package dev.ian.worldpanel

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import dev.ian.worldpanel.shape.PanelShape
import net.minecraft.client.renderer.MultiBufferSource

/**
 * A composite 3D regular prism (such as a hexagonal, octagonal, or triangular prism)
 * where each of the [sides] lateral faces and optional top/bottom caps are independent [FloatingWindow]s.
 */
class FloatingPrism private constructor(
    val sides: Int,
    val faceWidth: Float,
    val faceHeight: Float,
    val scale: Float,
    val offsetX: Double,
    val offsetY: Double,
    val offsetZ: Double,
    val sideFaces: Map<Int, FloatingWindow>,
    val topFace: FloatingWindow?,
    val bottomFace: FloatingWindow?,
    val spinSpeedY: Float,
    val tumbleSpeedX: Float,
    val tumbleSpeedZ: Float,
    initialTargetSide: Int?,
    var transitionSpeed: Float,
) {
    private var targetSide: Int? = initialTargetSide
    private var currentYaw: Float = if (initialTargetSide != null) -initialTargetSide * (360f / sides) else 0f
    private var currentPitch: Float = 0f
    private var currentRoll: Float = 0f
    private var lastTimeSeconds: Double = -1.0

    fun getSide(index: Int): FloatingWindow? = sideFaces[index]
    fun getTop(): FloatingWindow? = topFace
    fun getBottom(): FloatingWindow? = bottomFace

    fun targetSide(index: Int, transitionSpeed: Float = 5.0f) {
        this.targetSide = (index % sides + sides) % sides
        this.transitionSpeed = transitionSpeed
    }

    fun setTargetSide(index: Int, transitionSpeed: Float = 5.0f) = targetSide(index, transitionSpeed)

    fun clearTargetSide() {
        this.targetSide = null
    }

    fun getTargetSide(): Int? = targetSide

    fun render(poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int) {
        if (sideFaces.isEmpty() && topFace == null && bottomFace == null) return

        val timeSeconds = WindowColor.currentTimeSeconds()

        val (rotYaw, rotPitch, rotRoll) = if (targetSide != null) {
            val ts = targetSide!!
            val targetYaw = -ts * (360f / sides)
            val dt = if (lastTimeSeconds > 0.0) (timeSeconds - lastTimeSeconds).toFloat().coerceIn(0.001f, 0.1f) else 0.05f
            val factor = (dt * transitionSpeed).coerceIn(0.01f, 1f)
            currentYaw = Math3DUtils.lerpAngleDegrees(currentYaw, targetYaw, factor)
            currentPitch = Math3DUtils.lerpAngleDegrees(currentPitch, 0f, factor)
            currentRoll = Math3DUtils.lerpAngleDegrees(currentRoll, 0f, factor)
            Triple(currentYaw, currentPitch, currentRoll)
        } else {
            val y = (timeSeconds * (spinSpeedY * 50.0)).toFloat() % 360f
            val p = (timeSeconds * (tumbleSpeedX * 50.0)).toFloat() % 360f
            val r = (timeSeconds * (tumbleSpeedZ * 50.0)).toFloat() % 360f
            currentYaw = y
            currentPitch = p
            currentRoll = r
            Triple(y, p, r)
        }
        lastTimeSeconds = timeSeconds

        poseStack.pushPose()
        poseStack.translate(offsetX, offsetY, offsetZ)

        if (rotYaw != 0f) poseStack.mulPose(Axis.YP.rotationDegrees(rotYaw))
        if (rotPitch != 0f) poseStack.mulPose(Axis.XP.rotationDegrees(rotPitch))
        if (rotRoll != 0f) poseStack.mulPose(Axis.ZP.rotationDegrees(rotRoll))

        val sideLengthBlocks = (faceWidth * scale).toDouble()
        val apothem = Math3DUtils.regularPolygonApothem(sideLengthBlocks, sides)
        val heightBlocks = (faceHeight * scale).toDouble()

        for ((index, window) in sideFaces) {
            val angle = index * (360f / sides)
            poseStack.pushPose()
            poseStack.mulPose(Axis.YP.rotationDegrees(angle))
            poseStack.translate(0.0, heightBlocks / 2.0, apothem)
            window.render(poseStack, buffer, packedLight)
            poseStack.popPose()
        }

        if (topFace != null) {
            poseStack.pushPose()
            poseStack.translate(0.0, heightBlocks, 0.0)
            poseStack.mulPose(Axis.XP.rotationDegrees(-90f))
            topFace.render(poseStack, buffer, packedLight)
            poseStack.popPose()
        }

        if (bottomFace != null) {
            poseStack.pushPose()
            poseStack.translate(0.0, 0.0, 0.0)
            poseStack.mulPose(Axis.XP.rotationDegrees(90f))
            bottomFace.render(poseStack, buffer, packedLight)
            poseStack.popPose()
        }

        poseStack.popPose()
    }

    class Builder {
        private var sides: Int = 6
        private var faceWidth: Float = 36f
        private var faceHeight: Float = 48f
        private var scale: Float = 0.025f
        private var offsetX: Double = 0.0
        private var offsetY: Double = 1.3
        private var offsetZ: Double = 0.0
        private var spinSpeedY: Float = 0f
        private var tumbleSpeedX: Float = 0f
        private var tumbleSpeedZ: Float = 0f
        private var targetSide: Int? = null
        private var transitionSpeed: Float = 5.0f

        private val sideWindows = mutableMapOf<Int, FloatingWindow>()
        private val sideBuilders = mutableMapOf<Int, (FloatingWindow.Builder) -> Unit>()
        private var topWindow: FloatingWindow? = null
        private var topBuilder: ((FloatingWindow.Builder) -> Unit)? = null
        private var bottomWindow: FloatingWindow? = null
        private var bottomBuilder: ((FloatingWindow.Builder) -> Unit)? = null

        fun setSides(sides: Int) = apply {
            require(sides >= 3) { "Prism must have at least 3 sides (got $sides)" }
            this.sides = sides
        }

        fun setFaceWidth(width: Float) = apply { this.faceWidth = width }
        fun setFaceHeight(height: Float) = apply { this.faceHeight = height }
        fun setSize(width: Float, height: Float) = apply {
            this.faceWidth = width
            this.faceHeight = height
        }

        fun setScale(scale: Float) = apply { this.scale = scale }
        fun setOffset(x: Double, y: Double, z: Double) = apply {
            this.offsetX = x
            this.offsetY = y
            this.offsetZ = z
        }

        fun animateSpin(speedY: Float) = apply { this.spinSpeedY = speedY }
        fun animateTumble(speedX: Float, speedY: Float, speedZ: Float) = apply {
            this.tumbleSpeedX = speedX
            this.spinSpeedY = speedY
            this.tumbleSpeedZ = speedZ
        }

        fun targetSide(index: Int, transitionSpeed: Float = 5.0f) = apply {
            this.targetSide = index
            this.transitionSpeed = transitionSpeed
        }

        fun setTargetSide(index: Int, transitionSpeed: Float = 5.0f) = targetSide(index, transitionSpeed)

        fun clearTargetSide() = apply { this.targetSide = null }

        fun configureFace(face: PrismFace, config: (FloatingWindow.Builder) -> Unit) = apply {
            when (face) {
                is PrismFace.Side -> configureSide(face.index, config)
                is PrismFace.CapTop -> configureTop(config)
                is PrismFace.CapBottom -> configureBottom(config)
            }
        }

        fun setFace(face: PrismFace, window: FloatingWindow) = apply {
            when (face) {
                is PrismFace.Side -> setSide(face.index, window)
                is PrismFace.CapTop -> setTop(window)
                is PrismFace.CapBottom -> setBottom(window)
            }
        }

        fun configureSide(index: Int, config: (FloatingWindow.Builder) -> Unit) = apply {
            sideBuilders[index] = config
        }

        fun setSide(index: Int, window: FloatingWindow) = apply {
            sideWindows[index] = window
        }

        fun configureTop(config: (FloatingWindow.Builder) -> Unit) = apply {
            this.topBuilder = config
        }

        fun setTop(window: FloatingWindow) = apply {
            this.topWindow = window
        }

        fun configureBottom(config: (FloatingWindow.Builder) -> Unit) = apply {
            this.bottomBuilder = config
        }

        fun setBottom(window: FloatingWindow) = apply {
            this.bottomWindow = window
        }

        fun build(): FloatingPrism {
            for ((index, config) in sideBuilders) {
                val b = FloatingWindow.builder()
                    .setSize(faceWidth, faceHeight)
                    .setAlignment(WindowAlignment.CENTER)
                    .setScale(scale)
                    .setFaceCamera(false)
                    .setRenderBackFace(false)
                    .setOffset(0.0, 0.0, 0.0)
                    .setOffsetRelativeToOrientation(true)
                config(b)
                sideWindows[index] = b.build()
            }

            val capDiameter = (Math3DUtils.regularPolygonCircumradius(faceWidth.toDouble(), sides) * 2.0).toFloat()

            if (topBuilder != null) {
                val b = FloatingWindow.builder()
                    .setSize(capDiameter, capDiameter)
                    .setShape(PanelShape.polygon(sides))
                    .setAlignment(WindowAlignment.CENTER)
                    .setScale(scale)
                    .setFaceCamera(false)
                    .setRenderBackFace(false)
                    .setOffset(0.0, 0.0, 0.0)
                    .setOffsetRelativeToOrientation(true)
                topBuilder!!(b)
                topWindow = b.build()
            }

            if (bottomBuilder != null) {
                val b = FloatingWindow.builder()
                    .setSize(capDiameter, capDiameter)
                    .setShape(PanelShape.polygon(sides))
                    .setAlignment(WindowAlignment.CENTER)
                    .setScale(scale)
                    .setFaceCamera(false)
                    .setRenderBackFace(false)
                    .setOffset(0.0, 0.0, 0.0)
                    .setOffsetRelativeToOrientation(true)
                bottomBuilder!!(b)
                bottomWindow = b.build()
            }

            return FloatingPrism(
                sides = sides,
                faceWidth = faceWidth,
                faceHeight = faceHeight,
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
                offsetZ = offsetZ,
                sideFaces = sideWindows.toMap(),
                topFace = topWindow,
                bottomFace = bottomWindow,
                spinSpeedY = spinSpeedY,
                tumbleSpeedX = tumbleSpeedX,
                tumbleSpeedZ = tumbleSpeedZ,
                initialTargetSide = targetSide,
                transitionSpeed = transitionSpeed,
            )
        }
    }

    companion object {
        fun builder(): Builder = Builder()
    }
}
