package dev.ian.worldpanel

import com.mojang.blaze3d.vertex.PoseStack
import dev.ian.worldpanel.animation.PanelAnimation
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity

/**
 * High-level builder and renderer for floating texts, labels, and 3D extruded signage in the Minecraft world.
 *
 * Renders without window background plates, borders, or shadows unless requested, keeping rendering minimal
 * and focused on lettering.
 */
class FloatingText private constructor(
    private val window: FloatingWindow
) {
    /** Renders the text into the supplied [poseStack] and [buffer]. */
    fun render(poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int) {
        window.render(poseStack, buffer, packedLight)
    }

    class Builder {
        private val lines = mutableListOf<LineConfig>()
        private var defaultFont: ResourceLocation? = null
        private var is3D: Boolean = false
        private var extrusionDepth: Float = 0.5f
        private var scale: Float = 0.025f
        private var alignment: WindowAlignment = WindowAlignment.CENTER
        private var textAlignment: WindowElement.Text.TextAlignment = WindowElement.Text.TextAlignment.CENTER
        private var faceCamera: Boolean = true
        private var fixedYaw: Float = 0f
        private var fixedPitch: Float = 0f
        private var fixedRoll: Float = 0f
        private var attachedEntity: Entity? = null
        private var offsetX: Double = 0.0
        private var offsetY: Double = 1.3
        private var offsetZ: Double = 0.0
        private var lineSpacing: Float = 2f
        private var offsetRelativeToOrientation: Boolean = true
        private var renderBackFace: Boolean = false
        private var fullBright: Boolean = false
        private var seeThrough: Boolean = false
        private var distanceFadeMin: Double? = null
        private var distanceFadeMax: Double? = null
        private var constantScreenSize: Boolean = false
        private var referenceDistance: Double = 8.0
        private val animations = mutableListOf<PanelAnimation>()

        private data class LineConfig(
            val component: Component,
            val color: WindowColor,
            val shadow: Boolean,
            val textScale: Float,
            val is3D: Boolean?,
            val extrusionDepth: Float?,
            val font: ResourceLocation?,
        )

        /** Adds a plain string line to this floating text. */
        fun addLine(
            text: String,
            color: WindowColor = WindowColor.Solid(0xFFFFFFFFL),
            shadow: Boolean = true,
            textScale: Float = 1.0f,
            is3D: Boolean? = null,
            extrusionDepth: Float? = null,
            font: ResourceLocation? = null,
        ) = apply {
            lines += LineConfig(
                component = Component.literal(text),
                color = color,
                shadow = shadow,
                textScale = textScale,
                is3D = is3D,
                extrusionDepth = extrusionDepth,
                font = font
            )
        }

        /** Adds a rich Minecraft [Component] line to this floating text. */
        fun addLine(
            component: Component,
            color: WindowColor = WindowColor.Solid(0xFFFFFFFFL),
            shadow: Boolean = true,
            textScale: Float = 1.0f,
            is3D: Boolean? = null,
            extrusionDepth: Float? = null,
            font: ResourceLocation? = null,
        ) = apply {
            lines += LineConfig(
                component = component,
                color = color,
                shadow = shadow,
                textScale = textScale,
                is3D = is3D,
                extrusionDepth = extrusionDepth,
                font = font
            )
        }

        /** Replaces all lines with a single plain string line. */
        fun setText(
            text: String,
            color: WindowColor = WindowColor.Solid(0xFFFFFFFFL),
            shadow: Boolean = true,
            textScale: Float = 1.0f,
        ) = apply {
            lines.clear()
            addLine(text, color, shadow, textScale)
        }

        /** Replaces all lines with a single rich [Component] line. */
        fun setText(
            component: Component,
            color: WindowColor = WindowColor.Solid(0xFFFFFFFFL),
            shadow: Boolean = true,
            textScale: Float = 1.0f,
        ) = apply {
            lines.clear()
            addLine(component, color, shadow, textScale)
        }

        /** Enables or disables physical 3D extrusion lettering. */
        fun set3D(enabled: Boolean = true, extrusionDepth: Float = 0.5f) = apply {
            this.is3D = enabled
            this.extrusionDepth = extrusionDepth
        }

        /** Sets the default font used for all lines. */
        fun setFont(font: ResourceLocation?) = apply { this.defaultFont = font }
        fun setFont(font: String) = apply { this.defaultFont = ResourceLocation.parse(font) }

        /** Sets the world scale of the lettering. */
        fun setScale(scale: Float) = apply { this.scale = scale.coerceAtLeast(0.001f) }

        /** Sets overall window anchor alignment relative to the offset point. */
        fun setAlignment(alignment: WindowAlignment) = apply { this.alignment = alignment }

        /** Sets text horizontal alignment (LEFT, CENTER, RIGHT). */
        fun setTextAlignment(alignment: WindowElement.Text.TextAlignment) = apply { this.textAlignment = alignment }

        /** Sets vertical spacing between lines in pixels. */
        fun setLineSpacing(spacing: Float) = apply { this.lineSpacing = spacing }

        /** Sets whether the lettering automatically billboards to face the player camera. */
        fun setFaceCamera(faceCamera: Boolean) = apply { this.faceCamera = faceCamera }

        /** Sets fixed 3D world orientation (disables camera billboarding). */
        fun setFixedRotation(yaw: Float, pitch: Float = 0f, roll: Float = 0f) = apply {
            this.fixedYaw = yaw
            this.fixedPitch = pitch
            this.fixedRoll = roll
            this.faceCamera = false
            this.attachedEntity = null
        }

        /** Locks the text orientation to match the target entity's body rotation. */
        fun attachToEntityRotation(entity: Entity) = apply {
            this.attachedEntity = entity
            this.faceCamera = false
        }

        /** Locks the text orientation to match a block facing direction. */
        fun attachToBlockFacing(direction: Direction) = apply {
            fixedYaw = when (direction) {
                Direction.SOUTH -> 0f
                Direction.NORTH -> 180f
                Direction.EAST -> 90f
                Direction.WEST -> 270f
                else -> 0f
            }
            fixedPitch = 0f
            fixedRoll = 0f
            attachedEntity = null
            faceCamera = false
        }
        fun attachToEntityRotation(direction: Direction) = attachToBlockFacing(direction)
        fun setBlockFacing(direction: Direction) = attachToBlockFacing(direction)

        /** Sets the translation offset (X, Y, Z). */
        fun setOffset(x: Double, y: Double, z: Double) = apply {
            this.offsetX = x
            this.offsetY = y
            this.offsetZ = z
        }

        fun setWorldOffset(x: Double, y: Double, z: Double) = apply {
            setOffset(x, y, z)
            setOffsetRelativeToOrientation(false)
        }

        fun setLocalOffset(lateral: Double, height: Double, forward: Double = 0.0) = apply {
            setOffset(lateral, height, forward)
            setOffsetRelativeToOrientation(true)
        }

        fun setOffsetRelativeToOrientation(relative: Boolean) = apply {
            this.offsetRelativeToOrientation = relative
        }

        fun setRenderBackFace(renderBackFace: Boolean = true) = apply {
            this.renderBackFace = renderBackFace
        }

        fun setDoubleSided(doubleSided: Boolean = true) = setRenderBackFace(doubleSided)

        /** Ignores ambient block and sky light, rendering the text at 100% full brightness. */
        fun setFullBright(enabled: Boolean = true) = apply { this.fullBright = enabled }

        /** Renders the text visible even through solid walls. */
        fun setSeeThrough(enabled: Boolean = true) = apply { this.seeThrough = enabled }

        /** Configures smooth distance fade-out and automatic culling beyond [maxDistance] blocks. */
        fun setDistanceFade(minDistance: Double, maxDistance: Double) = apply {
            this.distanceFadeMin = minDistance.coerceAtLeast(0.0)
            this.distanceFadeMax = maxDistance.coerceAtLeast(minDistance)
        }

        /** Keeps visual screen size constant regardless of distance from camera. */
        fun setConstantScreenSize(enabled: Boolean = true, referenceDistance: Double = 8.0) = apply {
            this.constantScreenSize = enabled
            this.referenceDistance = referenceDistance.coerceAtLeast(0.1)
        }

        /** Adds a custom procedural animation to this text. */
        fun addAnimation(animation: PanelAnimation) = apply {
            animations += animation
        }

        /** Continuous 360-degree rotation animation around an axis ("Y", "X", or "Z"). */
        fun animateSpin(speed: Float = 1.0f, axis: String = "Y") = apply {
            addAnimation(PanelAnimation.Spin(speed, axis))
        }

        /** Vertical bobbing/floating animation using a smooth sine wave. */
        fun animateBobbing(amplitude: Double = 0.12, speed: Float = 1.0f) = apply {
            addAnimation(PanelAnimation.Bobbing(amplitude, speed))
        }

        /** Dropped item animation: continuously spins while bobbing up and down like a Minecraft item! */
        fun animateDroppedItem(bobAmplitude: Double = 0.12, spinSpeed: Float = 1.0f, bobSpeed: Float = 1.0f) = apply {
            addAnimation(PanelAnimation.DroppedItem(bobAmplitude, spinSpeed, bobSpeed))
        }

        /** Oscillating relative movement from the initial position to (initial + offset) and back. */
        fun animateMoveOffset(dx: Double, dy: Double, dz: Double, speed: Float = 1.0f) = apply {
            addAnimation(PanelAnimation.MoveOffset(dx, dy, dz, speed))
        }

        /** Oscillating waypoint movement between two absolute world points A and B. */
        fun animateMoveBetween(startX: Double, startY: Double, startZ: Double, endX: Double, endY: Double, endZ: Double, speed: Float = 1.0f) = apply {
            addAnimation(PanelAnimation.MoveBetween(startX, startY, startZ, endX, endY, endZ, speed))
        }

        /** Builds the [FloatingText] instance ready for rendering. */
        fun build(): FloatingText {
            val winBuilder = FloatingWindow.builder()
                .textOnly()
                .setScale(scale)
                .setAlignment(alignment)
                .setFaceCamera(faceCamera)
                .setOffset(offsetX, offsetY, offsetZ)
                .setOffsetRelativeToOrientation(offsetRelativeToOrientation)
                .setRenderBackFace(renderBackFace)

            if (attachedEntity != null) {
                winBuilder.attachToEntityRotation(attachedEntity!!)
            } else if (fixedYaw != 0f || fixedPitch != 0f || fixedRoll != 0f) {
                winBuilder.setFixedRotation(fixedYaw, fixedPitch, fixedRoll)
            }

            for (anim in animations) {
                winBuilder.addAnimation(anim)
            }

            if (fullBright) winBuilder.setFullBright(true)
            if (seeThrough) winBuilder.setSeeThrough(true)
            if (distanceFadeMin != null && distanceFadeMax != null) winBuilder.setDistanceFade(distanceFadeMin!!, distanceFadeMax!!)
            if (constantScreenSize) winBuilder.setConstantScreenSize(true, referenceDistance)

            for ((index, line) in lines.withIndex()) {
                val use3D = line.is3D ?: this.is3D
                val depth = line.extrusionDepth ?: this.extrusionDepth
                val renderMode = if (use3D) TextRenderMode.EXTRUDED_3D else TextRenderMode.FLAT_2D
                val font = line.font ?: this.defaultFont

                winBuilder.addText(
                    component = line.component,
                    color = line.color,
                    shadow = line.shadow,
                    textScale = line.textScale,
                    alignment = textAlignment,
                    font = font,
                    renderMode = renderMode,
                    extrusionDepth = depth
                )
                if (lineSpacing > 0f && index < lines.size - 1) {
                    winBuilder.addSpacer(lineSpacing)
                }
            }

            return FloatingText(winBuilder.build())
        }
    }

    companion object {
        fun builder(): Builder = Builder()

        /** Renders a quick 3D floating text sign in the world in a single call. */
        fun render3D(
            poseStack: PoseStack,
            buffer: MultiBufferSource,
            text: String,
            x: Double = 0.0,
            y: Double = 2.0,
            z: Double = 0.0,
            color: WindowColor = WindowColor.Solid(0xFFFFFFFFL),
            extrusionDepth: Float = 0.5f,
            scale: Float = 0.025f,
            shadow: Boolean = true,
            packedLight: Int,
        ) {
            builder()
                .setText(text, color, shadow)
                .set3D(true, extrusionDepth)
                .setScale(scale)
                .setOffset(x, y, z)
                .build()
                .render(poseStack, buffer, packedLight)
        }

        /** Renders an animated 3D floating text sign spinning and bobbing like a dropped item. */
        fun renderAnimated3D(
            poseStack: PoseStack,
            buffer: MultiBufferSource,
            text: String,
            x: Double = 0.0,
            y: Double = 2.0,
            z: Double = 0.0,
            color: WindowColor = WindowColor.Solid(0xFFFFFFFFL),
            extrusionDepth: Float = 0.5f,
            scale: Float = 0.025f,
            bobAmplitude: Double = 0.12,
            spinSpeed: Float = 1.0f,
            bobSpeed: Float = 1.0f,
            shadow: Boolean = true,
            packedLight: Int,
        ) {
            builder()
                .setText(text, color, shadow)
                .set3D(true, extrusionDepth)
                .setScale(scale)
                .setOffset(x, y, z)
                .animateDroppedItem(bobAmplitude, spinSpeed, bobSpeed)
                .build()
                .render(poseStack, buffer, packedLight)
        }
    }
}
