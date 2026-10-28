package dev.ian.worldpanel.camera

/**
 * Immutable configuration for a virtual camera in the Minecraft world.
 *
 * Supports continuous 3D world positioning, orientation, field-of-view,
 * target aspect ratio/resolution, throttled update rate, and follow modes.
 */
data class VirtualCameraConfig(
    val id: String,
    val dimension: String? = null,
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
    val yaw: Float = 0f,
    val pitch: Float = 0f,
    val fov: Float = 70f,
    val resolutionWidth: Int = 256,
    val resolutionHeight: Int = 256,
    val updateRateFps: Int = 20,
    val state: VirtualCameraState = VirtualCameraState.ACTIVE,
    val followMode: CameraFollowMode? = null,
    val followOffset: Triple<Double, Double, Double> = Triple(0.0, 0.0, 0.0),
    val fixedOrientation: Boolean = false,
) {
    val aspectRatio: Float
        get() = resolutionWidth.toFloat() / resolutionHeight.coerceAtLeast(1).toFloat()

    val minUpdateIntervalMs: Long
        get() = if (updateRateFps <= 0) 1000L else (1000L / updateRateFps).coerceAtLeast(16L)

    class Builder(var id: String = "cam_${System.nanoTime()}") {
        private var dimension: String? = null
        private var x: Double = 0.0
        private var y: Double = 0.0
        private var z: Double = 0.0
        private var yaw: Float = 0f
        private var pitch: Float = 0f
        private var fov: Float = 70f
        private var resolutionWidth: Int = 256
        private var resolutionHeight: Int = 256
        private var updateRateFps: Int = 20
        private var state: VirtualCameraState = VirtualCameraState.ACTIVE
        private var followMode: CameraFollowMode? = null
        private var followOffset: Triple<Double, Double, Double> = Triple(0.0, 0.0, 0.0)
        private var fixedOrientation: Boolean = false

        fun id(id: String) = apply { this.id = id }
        fun dimension(dimension: String?) = apply { this.dimension = dimension }
        fun position(x: Double, y: Double, z: Double) = apply {
            this.x = x
            this.y = y
            this.z = z
        }
        fun rotation(yaw: Float, pitch: Float) = apply {
            this.yaw = yaw
            this.pitch = pitch
        }
        fun fov(fov: Float) = apply { this.fov = fov.coerceIn(10f, 160f) }
        fun resolution(width: Int, height: Int) = apply {
            this.resolutionWidth = width.coerceIn(32, 1920)
            this.resolutionHeight = height.coerceIn(32, 1080)
        }
        fun aspectRatio16x9(baseWidth: Int = 256) = apply {
            this.resolutionWidth = baseWidth
            this.resolutionHeight = (baseWidth * 9) / 16
        }
        fun aspectRatio4x3(baseWidth: Int = 256) = apply {
            this.resolutionWidth = baseWidth
            this.resolutionHeight = (baseWidth * 3) / 4
        }
        fun updateRate(fps: Int) = apply { this.updateRateFps = fps.coerceIn(1, 60) }
        fun state(state: VirtualCameraState) = apply { this.state = state }
        fun active() = apply { this.state = VirtualCameraState.ACTIVE }
        fun paused() = apply { this.state = VirtualCameraState.PAUSED }
        fun disabled() = apply { this.state = VirtualCameraState.DISABLED }

        fun followMode(mode: CameraFollowMode?) = apply { this.followMode = mode }
        fun followOffset(dx: Double, dy: Double, dz: Double) = apply {
            this.followOffset = Triple(dx, dy, dz)
        }
        fun fixedOrientation(fixed: Boolean) = apply { this.fixedOrientation = fixed }

        fun build(): VirtualCameraConfig = VirtualCameraConfig(
            id = id,
            dimension = dimension,
            x = x,
            y = y,
            z = z,
            yaw = yaw,
            pitch = pitch,
            fov = fov,
            resolutionWidth = resolutionWidth,
            resolutionHeight = resolutionHeight,
            updateRateFps = updateRateFps,
            state = state,
            followMode = followMode,
            followOffset = followOffset,
            fixedOrientation = fixedOrientation,
        )
    }

    companion object {
        fun builder(id: String = "camera_${System.nanoTime()}"): Builder = Builder(id)
    }
}
