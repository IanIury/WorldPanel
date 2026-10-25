# Guide: How to Create a Simple Window in WorldPanel [![Português](https://img.shields.io/badge/lang-Portugu%C3%AAs%20(BR)-green.svg)](janela_simples.md)

This guide explains how to create, configure, and render a floating window in the Minecraft 3D world using the **WorldPanel** library.

---

## 1. Overview

WorldPanel uses the **Builder** pattern (`FloatingWindow.builder()`) to construct custom windows. You can define the geometric shape, dimensions, colors, borders, orientation, and internal elements (text lines, item icons, separators, etc.).

Once built, the window is rendered by calling `.render(poseStack, buffer, packedLight)` inside your desired rendering event or stage (for example, inside an `EntityRenderer`, `BlockEntityRenderer`, or `RenderLevelStageEvent`).

---

## 2. Basic Step-by-Step Example

```kotlin
import dev.ian.worldpanel.FloatingWindow
import dev.ian.worldpanel.WindowAlignment
import dev.ian.worldpanel.WindowColor
import dev.ian.worldpanel.WindowElement
import dev.ian.worldpanel.shape.PanelShape
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource

fun renderMyWindow(
    poseStack: PoseStack,
    buffer: MultiBufferSource,
    packedLight: Int
) {
    val window = FloatingWindow.builder()
        // 1. Position and orientation
        .setFaceCamera(true)                  // Automatically rotates to face the player
        .setOffset(0.0, 2.2, 0.0)             // 2.2 blocks above the origin point
        .setScale(0.025f)                     // Visual scale of the window in the world

        // 2. Window shape and appearance
        .setShape(PanelShape.roundedRectangle(4f)) // Rounded rectangle
        .setSize(width = 80f, height = 50f, autoScaleContent = true)
        .setAlignment(WindowAlignment.CENTER) // Centers the window relative to the offset point
        .setPadding(4f)                       // Inner spacing between the border and content
        .setBackground(WindowColor.Solid(0xDD181C2A)) // Semi-transparent dark background
        .setBorder(WindowColor.Solid(0xFF38BDF8), width = 0.75f) // Light blue border
        .setShadow(WindowColor.Solid(0x88000000), offset = 1.5f) // Soft drop shadow

        // 3. Adding sequential content
        .addText(
            text = "Entity Status",
            color = WindowColor.Solid(0xFF38BDF8),
            shadow = true,
            textScale = 1.1f,
            alignment = WindowElement.Text.TextAlignment.CENTER
        )
        // Elegant separator that respects the shape's borders
        .addSeparator(
            color = WindowColor.Solid(0xFF475569),
            thickness = 0.75f,
            margin = 2f,
            includeBorder = false // Default: spans side-to-side stopping right at the inner border
        )
        .addText(
            text = "HP: 100 / 100",
            color = WindowColor.Solid(0xFF4ADE80),
            shadow = true,
            textScale = 0.9f
        )
        .addText(
            text = "Level: 5",
            color = WindowColor.Solid(0xFFFACC15),
            shadow = true,
            textScale = 0.9f
        )
        .build()

    // 4. Render in the scene
    window.render(poseStack, buffer, packedLight)
}
```

---

## 3. Understanding Key Parameters

### Shape and Geometry (`.setShape(...)`)
You can choose from several built-in geometric shapes:
- `PanelShape.roundedRectangle(cornerRadius)`: Rounded rectangle with uniform or per-corner radii.
- `PanelShape.circle()`: Smooth circle with 72 radial segments.
- `PanelShape.rectangle()`: Classic straight rectangle.
- `PanelShape.polygon(sides = 6)`: Regular polygon (e.g., hexagon).
- `PanelShape.triangle(...)`, `PanelShape.rhombus()`, `PanelShape.trapezoid(...)`, `PanelShape.parallelogram(...)`, etc.

### Sizing and Auto-Scaling (`.setSize(...)`)
```kotlin
.setSize(
    width = 30f, 
    height = 30f, 
    autoScaleContent = true, // Proportionally scales content to fit the panel
    autoFitWidth = false,    // Shrinks width if the content is smaller
    autoFitHeight = false    // Shrinks height if the content is smaller
)
```

### Contour-Aware Content Fitting (circle, triangle, rhombus, trapezoid, parallelogram, polygons)
For **non-rectangular** shapes, content is not restricted to a single conservative bounding box. Instead, each line (text, icon, item, separator) is tested against the **actual horizontal contour of the shape at its vertical height** (already accounting for `padding` and border width). As a result:

- **Triangle / rhombus / trapezoid:** Short lines fit into the narrow upper/lower sections while wider lines settle into the broader sections, maximizing internal space usage.
- **Parallelogram:** Lines follow the slant of the side edges and never spill out of the window.
- **Circle / ellipse / regular polygons:** Content uses the real horizontal chord of the shape at each vertical height.
- **Larger text:** In fixed-size windows (`.setSize(...)` with `autoScaleContent = true`), content is **scaled up** to fill the shape (default max scale: 2x). Long words only shrink the text when they genuinely cannot fit.
- **Without `setSize`:** The panel automatically expands around its content until everything fits cleanly inside the contour.

```kotlin
FloatingWindow.builder()
    .setShape(PanelShape.triangle())
    .setPadding(3f)
    .setSize(width = 120f, height = 70f)     // autoScaleContent = true (default)
    .setMaxContentScale(3f)                  // optional: allows scaling up to 3x (default 2x; use 1f to only scale down)
    .addText("Original damage:")
    .addText("0.84799993")
    .build()
```

> [!NOTE]
> Rectangles, squares, and rounded rectangles retain the classic content-box layout without automatic upscaling.

### The Separator (`.addSeparator(...)`)
The separator divides window sections. It automatically calculates the horizontal chord length of the shape at any vertical height (even in circles or polygons, it never pokes outside the shape boundary).

| Parameter | Default | Description |
| :--- | :--- | :--- |
| `color` | `Solid(0xFF808080)` | Solid or animated line color. |
| `thickness` | `1f` | Line thickness in pixels. |
| `margin` | `2f` | Vertical margin (space above and below the line). |
| `includeBorder` | `false` | When `false` (default), the line stops cleanly at the inner edge of the border. When `true`, it extends to the outer edge. |
| `edgeToEdge` | `true` | When `true`, spans from one side of the shape to the other at height $Y$. When `false`, stays within the inner padding area. |

### Animated Colors
You can use dynamic animated colors on any text, border, or background:
- `WindowColor.Gradient(0xFF67E8F9, 0xFF8B5CF6)`: Smooth static gradient.
- `WindowColor.AnimatedGradient(0xFF67E8F9, 0xFF8B5CF6, speed = 1.2f, scale = 0.8f)`: Continuously scrolling gradient.
- `WindowColor.Rainbow(speed = 1.0f)`: Continuous rainbow cycle.
- `WindowColor.Pulse(0xFF9BE7FF, speed = 1.5f)`: Smooth brightness/opacity pulse.

---

## 4. Where to Call `.render(...)` in Your Mod

### Inside an `EntityRenderer` or `RenderLayer`:
```kotlin
class MyMobRenderer(context: EntityRendererProvider.Context) : MobRenderer<MyMob, MyModel>(context, ...) {
    override fun render(
        entity: MyMob,
        entityYaw: Float,
        partialTicks: Float,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int
    ) {
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight)

        // Create and render the floating window above the mob
        FloatingWindow.builder()
            .setFaceCamera(true)
            .setOffset(0.0, entity.bbHeight + 0.5, 0.0)
            .addText(entity.displayName, color = 0xFFFFFFFF.toInt(), shadow = true)
            .build()
            .render(poseStack, buffer, packedLight)
    }
}
```
