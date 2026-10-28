package dev.ian.worldpanel.camera

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Represents a spatial camera viewpoint with continuous 3D world coordinates,
 * yaw, pitch, and field of view.
 */
data class CameraView(
    val position: Vec3,
    val yaw: Float,
    val pitch: Float,
    val fov: Float = 70f
)

/**
 * Source for feeding flat video or CCTV screens in WorldPanel.
 */
sealed interface CameraSource {
    /**
     * A camera positioned at continuous 3D world coordinates with orientation.
     */
    data class Spatial(val view: CameraView) : CameraSource {
        constructor(position: Vec3, yaw: Float, pitch: Float, fov: Float = 70f) :
                this(CameraView(position, yaw, pitch, fov))
    }

    /**
     * A virtual camera driven by a [VirtualCameraConfig].
     */
    data class Virtual(val config: VirtualCameraConfig) : CameraSource {
        fun resolveView(): CameraView = CameraView(
            position = Vec3(config.x, config.y, config.z),
            yaw = config.yaw,
            pitch = config.pitch,
            fov = config.fov
        )
    }

    /**
     * A living entity providing either its POV (eye position & rotation), serving as the
     * subject displayed on the CCTV monitor, or tracked using multiple follow modes.
     */
    class Entity(
        val entitySupplier: () -> LivingEntity?,
        val fov: Float = 70f,
        val useEyePosition: Boolean = true,
        val spinSpeed: Float = 0f,
        val followMode: CameraFollowMode = CameraFollowMode.FIRST_PERSON,
        val distance: Double = 4.0,
        val heightOffset: Double = 1.5,
        val offset: Triple<Double, Double, Double> = Triple(0.0, 0.0, 0.0),
        val fixedOrientation: Boolean = false,
        val cameraMountPos: Vec3? = null,
    ) : CameraSource {
        constructor(
            entity: LivingEntity,
            fov: Float = 70f,
            useEyePosition: Boolean = true,
            spinSpeed: Float = 0f,
            followMode: CameraFollowMode = CameraFollowMode.FIRST_PERSON,
            distance: Double = 4.0,
            heightOffset: Double = 1.5,
            offset: Triple<Double, Double, Double> = Triple(0.0, 0.0, 0.0),
            fixedOrientation: Boolean = false,
            cameraMountPos: Vec3? = null,
        ) : this(
            entitySupplier = { entity },
            fov = fov,
            useEyePosition = useEyePosition,
            spinSpeed = spinSpeed,
            followMode = followMode,
            distance = distance,
            heightOffset = heightOffset,
            offset = offset,
            fixedOrientation = fixedOrientation,
            cameraMountPos = cameraMountPos,
        )

        fun resolveView(): CameraView? {
            val entity = entitySupplier() ?: return null
            if (entity.isRemoved) return null

            return when (followMode) {
                CameraFollowMode.FIRST_PERSON -> {
                    CameraView(
                        position = if (useEyePosition) entity.eyePosition else entity.position(),
                        yaw = entity.yRot,
                        pitch = entity.xRot,
                        fov = fov
                    )
                }
                CameraFollowMode.THIRD_PERSON -> {
                    // Chase cam: follow behind entity at distance and height
                    val yawRad = Math.toRadians(entity.yRot.toDouble())
                    val pitchRad = Math.toRadians(entity.xRot.toDouble())
                    val backX = sin(yawRad) * cos(pitchRad) * distance
                    val backZ = -cos(yawRad) * cos(pitchRad) * distance
                    val backY = -sin(pitchRad) * distance + heightOffset
                    val camPos = entity.eyePosition.add(backX, backY, backZ)
                    CameraView(
                        position = camPos,
                        yaw = entity.yRot,
                        pitch = entity.xRot,
                        fov = fov
                    )
                }
                CameraFollowMode.FIXED_OFFSET -> {
                    val basePos = entity.position()
                    val camPos = if (fixedOrientation) {
                        basePos.add(offset.first, offset.second, offset.third)
                    } else {
                        // Offset relative to entity facing direction
                        val yawRad = Math.toRadians(entity.yRot.toDouble())
                        val cosY = cos(yawRad)
                        val sinY = sin(yawRad)
                        val rx = offset.first * cosY - offset.third * sinY
                        val rz = offset.first * sinY + offset.third * cosY
                        basePos.add(rx, offset.second, rz)
                    }
                    CameraView(
                        position = camPos,
                        yaw = if (fixedOrientation) 0f else entity.yRot,
                        pitch = if (fixedOrientation) 0f else entity.xRot,
                        fov = fov
                    )
                }
                CameraFollowMode.ENTITY_TRACKING -> {
                    // PTZ security camera mounted at a fixed world location, dynamically tracking target entity
                    val mount = cameraMountPos ?: Vec3(entity.x + 8.0, entity.y + 6.0, entity.z + 8.0)
                    val target = entity.eyePosition
                    val dx = target.x - mount.x
                    val dy = target.y - mount.y
                    val dz = target.z - mount.z
                    val horizontalDist = sqrt(dx * dx + dz * dz).coerceAtLeast(0.001)

                    val targetYaw = (Math.toDegrees(atan2(-dx, dz)).toFloat() + 360f) % 360f
                    val targetPitch = Math.toDegrees(-atan2(dy, horizontalDist)).toFloat()

                    CameraView(
                        position = mount,
                        yaw = targetYaw,
                        pitch = targetPitch,
                        fov = fov
                    )
                }
            }
        }
    }

    companion object {
        fun of(position: Vec3, yaw: Float, pitch: Float, fov: Float = 70f): CameraSource =
            Spatial(CameraView(position, yaw, pitch, fov))

        fun of(view: CameraView): CameraSource = Spatial(view)

        fun of(config: VirtualCameraConfig): CameraSource = Virtual(config)

        fun of(entity: LivingEntity, fov: Float = 70f, spinSpeed: Float = 0f): CameraSource =
            Entity({ entity }, fov = fov, spinSpeed = spinSpeed)

        fun of(entitySupplier: () -> LivingEntity?, fov: Float = 70f, spinSpeed: Float = 0f): CameraSource =
            Entity(entitySupplier, fov = fov, spinSpeed = spinSpeed)

        fun firstPerson(entity: LivingEntity, fov: Float = 70f): CameraSource =
            Entity(entity, fov = fov, followMode = CameraFollowMode.FIRST_PERSON)

        fun thirdPerson(entity: LivingEntity, distance: Double = 4.0, height: Double = 1.5, fov: Float = 70f): CameraSource =
            Entity(entity, fov = fov, followMode = CameraFollowMode.THIRD_PERSON, distance = distance, heightOffset = height)

        fun securityPTZ(entity: LivingEntity, mountPos: Vec3, fov: Float = 60f): CameraSource =
            Entity(entity, fov = fov, followMode = CameraFollowMode.ENTITY_TRACKING, cameraMountPos = mountPos)

        fun fixedOffset(entity: LivingEntity, offsetX: Double, offsetY: Double, offsetZ: Double, fov: Float = 70f): CameraSource =
            Entity(entity, fov = fov, followMode = CameraFollowMode.FIXED_OFFSET, offset = Triple(offsetX, offsetY, offsetZ))
    }
}

/**
 * Camera switcher allowing a WorldPanel window to dynamically toggle between multiple
 * surveillance cameras or entity viewpoints with instant or smooth transitions.
 */
class CameraSwitcher(
    cameras: List<Pair<String, CameraSource>> = emptyList()
) {
    private val channelList = ArrayList(cameras)
    private var currentIndex = 0

    fun addCamera(label: String, source: CameraSource): CameraSwitcher = apply {
        channelList.add(label to source)
    }

    fun next(): CameraSource? {
        if (channelList.isEmpty()) return null
        currentIndex = (currentIndex + 1) % channelList.size
        return currentSource()
    }

    fun previous(): CameraSource? {
        if (channelList.isEmpty()) return null
        currentIndex = (currentIndex - 1 + channelList.size) % channelList.size
        return currentSource()
    }

    fun select(index: Int): CameraSource? {
        if (channelList.isEmpty()) return null
        currentIndex = index.coerceIn(0, channelList.size - 1)
        return currentSource()
    }

    fun select(label: String): CameraSource? {
        val found = channelList.indexOfFirst { it.first.equals(label, ignoreCase = true) }
        if (found >= 0) {
            currentIndex = found
            return currentSource()
        }
        return null
    }

    fun currentSource(): CameraSource? {
        if (channelList.isEmpty()) return null
        return channelList[currentIndex].second
    }

    fun currentLabel(): String {
        if (channelList.isEmpty()) return "NO CAM"
        return channelList[currentIndex].first
    }

    val cameraCount: Int get() = channelList.size
}
