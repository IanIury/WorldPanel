package dev.ian.worldpanel

/**
 * Visual presentation style for [WindowElement.EntityVideoFeed].
 */
enum class VideoFeedStyle {
    /** Clean 2D monitor rendering with true-to-life entity colors and a subtle bezel. */
    CLEAN,

    /** High-contrast CCTV security monitor with subtle scanlines, blink REC badge, and OSD timestamp. */
    SECURITY_CAMERA_CCTV,

    /** Retro cathode ray tube with horizontal scanlines, phosphor glow, and curvature accents. */
    CRT_SCANLINES,

    /** Sci-fi cyberpunk holographic terminal with phosphor green tint and bio-telemetry scan overlay. */
    CYBER_GREEN_HOLOGRAM,
}
