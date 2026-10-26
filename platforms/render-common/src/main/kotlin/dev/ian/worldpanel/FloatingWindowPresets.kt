package dev.ian.worldpanel

import dev.ian.worldpanel.shape.PanelShape

/**
 * Ready-to-use, professional presets for common Minecraft HUD use-cases:
 * entity health bars, machine/block energy gauges, futuristic hologram displays, and danger signs.
 */
object FloatingWindowPresets {
    /**
     * Pre-configured floating health bar with entity name, current/max HP and a colored progress bar.
     */
    fun healthBar(
        name: String,
        currentHp: Float,
        maxHp: Float,
        level: Int? = null,
        barColor: WindowColor = WindowColor.Solid(0xFF4ADE80L),
        yOffset: Double = 2.2,
    ): FloatingWindow.Builder {
        val fraction = (currentHp / maxHp.coerceAtLeast(0.001f)).coerceIn(0f, 1f)
        val builder = FloatingWindow.builder()
            .setFaceCamera(true)
            .setOffset(0.0, yOffset, 0.0)
            .setScale(0.025f)
            .setShape(PanelShape.roundedRectangle(3f))
            .setPadding(3.5f)
            .setBackground(WindowColor.Solid(0xDD111827L))
            .setBorder(WindowColor.Solid(0xFF374151L), width = 0.5f)
            .setShadow(WindowColor.Solid(0x66000000L), offset = 1.5f)

        val title = if (level != null) "[$level] $name" else name
        builder.addText(
            text = title,
            color = WindowColor.Solid(0xFFF9FAFBL),
            shadow = true,
            textScale = 0.95f,
            alignment = WindowElement.Text.TextAlignment.CENTER,
        )
        builder.addProgressBar(
            progress = fraction,
            width = 65f,
            height = 3.5f,
            fillColor = barColor,
            backgroundColor = WindowColor.Solid(0xFF1F2937L),
            borderColor = WindowColor.Solid(0xFF4B5563L),
            borderWidth = 0.5f,
        )
        val hpText = "${currentHp.toInt()} / ${maxHp.toInt()} HP"
        builder.addText(
            text = hpText,
            color = WindowColor.Solid(0xFF9CA3AFL),
            shadow = true,
            textScale = 0.75f,
            alignment = WindowElement.Text.TextAlignment.CENTER,
        )

        return builder
    }

    /**
     * Pre-configured futuristic hologram display panel with scanline effects and full brightness.
     */
    fun hologramDisplay(
        title: String,
        subtext: String? = null,
        accentColor: WindowColor = WindowColor.Solid(0xFF38BDF8L),
        yOffset: Double = 2.0,
    ): FloatingWindow.Builder {
        val builder = FloatingWindow.builder()
            .setFaceCamera(true)
            .setOffset(0.0, yOffset, 0.0)
            .setScale(0.025f)
            .setShape(PanelShape.roundedRectangle(4f))
            .setPadding(4f)
            .setBackground(WindowColor.Solid(0x770B132BL))
            .setBorder(accentColor, width = 0.75f)
            .setFullBright(true)
            .setHologram(true)
            .addText(
                text = title,
                color = accentColor,
                shadow = true,
                textScale = 1.1f,
                alignment = WindowElement.Text.TextAlignment.CENTER,
            )

        if (subtext != null) {
            builder.addSeparator(color = accentColor, thickness = 0.5f, margin = 2f)
            builder.addText(
                text = subtext,
                color = WindowColor.Solid(0xFFE0F2FEL),
                shadow = true,
                textScale = 0.85f,
                alignment = WindowElement.Text.TextAlignment.CENTER,
            )
        }

        return builder
    }

    /**
     * Pre-configured machine energy/status panel showing stored power and an animated progress bar.
     */
    fun machineStatus(
        machineName: String,
        storedEnergy: Long,
        maxEnergy: Long,
        unit: String = "FE",
        yOffset: Double = 1.5,
    ): FloatingWindow.Builder {
        val fraction = if (maxEnergy > 0) (storedEnergy.toDouble() / maxEnergy.toDouble()).toFloat().coerceIn(0f, 1f) else 0f
        return FloatingWindow.builder()
            .setFaceCamera(true)
            .setOffset(0.0, yOffset, 0.0)
            .setScale(0.025f)
            .setShape(PanelShape.roundedRectangle(4f))
            .setPadding(4f)
            .setBackground(WindowColor.Solid(0xEE1E293BL))
            .setBorder(WindowColor.Solid(0xFF3B82F6L), width = 0.75f)
            .addText(machineName, color = WindowColor.Solid(0xFF93C5FDL), shadow = true, alignment = WindowElement.Text.TextAlignment.CENTER)
            .addSeparator(color = WindowColor.Solid(0xFF475569L), thickness = 0.5f, margin = 2f)
            .addProgressBar(
                progress = fraction,
                width = 70f,
                height = 4f,
                fillColor = WindowColor.Gradient(0xFFEF4444L, 0xFF22C55EL, isVertical = false),
                backgroundColor = WindowColor.Solid(0xFF0F172AL),
            )
            .addText("$storedEnergy / $maxEnergy $unit", color = WindowColor.Solid(0xFFE2E8F0L), shadow = true, textScale = 0.8f, alignment = WindowElement.Text.TextAlignment.CENTER)
    }

    /**
     * Pre-configured triangular warning / danger sign.
     */
    fun dangerSign(
        warningText: String = "PERIGO",
        subtext: String? = null,
        yOffset: Double = 2.0,
    ): FloatingWindow.Builder {
        val builder = FloatingWindow.builder()
            .setFaceCamera(true)
            .setOffset(0.0, yOffset, 0.0)
            .setScale(0.03f)
            .setShape(PanelShape.triangle())
            .setSize(width = 80f, height = 55f)
            .setPadding(3f)
            .setBackground(WindowColor.Solid(0xEEFACC15L))
            .setBorder(WindowColor.Solid(0xFF000000L), width = 1.2f)
            .addText("⚠", color = WindowColor.Solid(0xFF000000L), textScale = 1.3f, alignment = WindowElement.Text.TextAlignment.CENTER)
            .addText(warningText, color = WindowColor.Solid(0xFF000000L), textScale = 0.9f, alignment = WindowElement.Text.TextAlignment.CENTER)

        if (subtext != null) {
            builder.addText(subtext, color = WindowColor.Solid(0xFF1F2937L), textScale = 0.75f, alignment = WindowElement.Text.TextAlignment.CENTER)
        }

        return builder
    }
}
