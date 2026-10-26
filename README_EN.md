# WorldPanel [![Português](https://img.shields.io/badge/lang-Portugu%C3%AAs%20(BR)-green.svg)](README.md)

**WorldPanel** is a Kotlin library for creating and rendering visual panels, floating windows, and 3D text directly in the Minecraft 3D world (NeoForge).

Ideal for displaying **floating entity nametags**, **training dummy status panels**, **machine/block info boards**, **boss health bars**, **3D signs**, and **immersive in-world UIs**.

### Why use WorldPanel?
- **Decoupled and under your control**: Does not register hidden global events or interfere with vanilla rendering. You decide **when**, **where**, and **for whom** to render by calling `.render(poseStack, buffer, packedLight)`.
- **Ready-to-Use HUD Elements**: Native support for health/energy progress bars (`ProgressBar`), radial gauges (`RadialProgress`), inventory item grids (`ItemGrid`), instant templates (`FloatingWindowPresets`), and frustum culling utilities (`WorldPanelCulling`).
- **Advanced Camera & Lighting Controls**: Animated holographic scanlines (`setHologram`), full bright neon mode (`setFullBright`), see-through wall rendering (`setSeeThrough`), distance-based fade/culling (`setDistanceFade`), and constant screen scaling (`setConstantScreenSize`).
- **Smart Geometry & Shape-Aware Layout**: Supports everything from rounded rectangles to circles, triangles, hexagons, and rhombuses. The layout engine automatically adjusts the position and scale of each text line, item icon, and separator to fit cleanly inside the actual contour of the chosen shape.
- **2D & Volumetric 3D Rendering**: Create classic flat panels or thick 3D plates complete with front, shaded side walls, back faces, and extruded 3D text.
- **Animated Colors & Visual Effects**: Built-in static and animated gradients, rainbow (`Rainbow`), pulsing (`Pulse`), and smooth color transitions (`Fade`) ready for backgrounds, borders, and text.

---

## 📚 Step-by-Step Guides

- 📘 [**How to Create a Simple Window Step by Step**](docs/simple_window_en.md) — Complete guide with builder examples, sizing, padding, borders, item icons, automatic shape fitting, and rendering hooks.
- 🔤 [**How to Create 3D Signs and Floating Text**](docs/3d_signs_and_text_en.md) — Guide showing how to render backgroundless floating text (`textOnly()`), animations, and physically extruded 3D signs via `FloatingText`.

---

## 📦 Supported Versions

| Minecraft | Loader | Artifact | Java | Kotlin | Language Provider |
| :--- | :--- | :--- | :---: | :---: | :--- |
| **1.21.1** | NeoForge 21.1.x | `worldpanel-neoforge-1.21.1` | Java 21 | 2.1.20 | KotlinForForge 5.10.0+ |
| **26.2** | NeoForge 26.2.x | `worldpanel-neoforge-26.2` | Java 25 | 2.4.21 | KotlinLangForge 2.15.0+ |

---

## 🚀 Getting Started (Adding to Your Mod)

You can add **WorldPanel** to your project via **Modrinth Maven** or **CurseMaven**.

### In `build.gradle.kts` (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
    maven("https://thedarkcolour.github.io/KotlinForForge/")
    maven("https://api.modrinth.com/maven") {
        content { includeGroup("maven.modrinth") }
    }
    maven("https://cursemaven.com") {
        content { includeGroup("curse.maven") }
    }
}

dependencies {
    // Kotlin language provider for NeoForge
    implementation("thedarkcolour:kotlinforforge-neoforge:5.10.0")

    // WorldPanel via Modrinth (replace <version> with the desired version)
    implementation("maven.modrinth:worldpanel:<version>")

    // Or via CurseMaven:
    // implementation("curse.maven:worldpanel-<project-id>:<file-id>")
}
```

### In `build.gradle` (Groovy DSL)

```groovy
repositories {
    mavenCentral()
    maven { url = "https://thedarkcolour.github.io/KotlinForForge/" }
    maven {
        url = "https://api.modrinth.com/maven"
        content { includeGroup "maven.modrinth" }
    }
    maven {
        url = "https://cursemaven.com"
        content { includeGroup "curse.maven" }
    }
}

dependencies {
    // Kotlin language provider for NeoForge
    implementation 'thedarkcolour:kotlinforforge-neoforge:5.10.0'

    // WorldPanel via Modrinth (replace <version> with the desired version)
    implementation "maven.modrinth:worldpanel:<version>"

    // Or via CurseMaven:
    // implementation "curse.maven:worldpanel-<project-id>:<file-id>"
}
```

---

## ⚡ Quick Start Example

Build your window using `FloatingWindow.builder()` and call `.render(poseStack, buffer, packedLight)` inside your `EntityRenderer`, `BlockEntityRenderer`, or `RenderLevelStageEvent`:

```kotlin
import dev.ian.worldpanel.FloatingWindow
import dev.ian.worldpanel.WindowAlignment
import dev.ian.worldpanel.WindowColor
import dev.ian.worldpanel.WindowElement
import dev.ian.worldpanel.shape.PanelShape
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource

fun renderStatusPanel(
    poseStack: PoseStack,
    buffer: MultiBufferSource,
    packedLight: Int
) {
    val window = FloatingWindow.builder()
        // 1. World position and orientation
        .setFaceCamera(true)                       // Automatically rotates to face the player
        .setOffset(0.0, 2.2, 0.0)                  // 2.2 blocks above the origin
        .setScale(0.025f)                          // Visual scale in 3D space

        // 2. Shape and visual style
        .setShape(PanelShape.roundedRectangle(4f)) // Rounded rectangle
        .setAlignment(WindowAlignment.CENTER)
        .setPadding(4f)
        .setBackground(WindowColor.Solid(0xDD181C2A))
        .setBorder(WindowColor.Solid(0xFF38BDF8), width = 0.75f)
        .setShadow(WindowColor.Solid(0x88000000), offset = 1.5f)

        // 3. Window content
        .addText(
            text = "Entity Status",
            color = WindowColor.Solid(0xFF38BDF8),
            shadow = true,
            textScale = 1.1f,
            alignment = WindowElement.Text.TextAlignment.CENTER
        )
        .addSeparator(
            color = WindowColor.Solid(0xFF475569),
            thickness = 0.75f,
            margin = 2f
        )
        .addProgressBar(
            progress = 0.85f,
            width = 65f,
            height = 3.5f,
            fillColor = WindowColor.Solid(0xFF4ADE80L)
        )
        .addText(
            text = "HP: 85 / 100",
            color = WindowColor.Solid(0xFF4ADE80),
            shadow = true
        )
        .build()

    // 4. Render in the scene
    window.render(poseStack, buffer, packedLight)
}
```

---

## 🎨 Instant HUD Presets (`FloatingWindowPresets`)

Quickly instantiate common HUD elements without building the entire layout manually:

```kotlin
// Entity or Boss health bar
val healthBar = FloatingWindowPresets.healthBar(
    name = "Elder Dragon",
    currentHp = 180f,
    maxHp = 200f,
    level = 50
).build()

// Futuristic hologram panel for sci-fi machines and terminals
val holoDisplay = FloatingWindowPresets.hologramDisplay(
    title = "CENTRAL CORE",
    subtext = "Operational - 100%"
).build()

// Machine stored energy gauge (FE/RF)
val machinePanel = FloatingWindowPresets.machineStatus(
    machineName = "Solar Generator",
    storedEnergy = 75000L,
    maxEnergy = 100000L
).build()

// Triangular danger / warning sign
val danger = FloatingWindowPresets.dangerSign(warningText = "HIGH VOLTAGE").build()
```

---

## 📐 Shape System & Smart Content Fitting

WorldPanel provides a variety of 2D geometric shapes with high-precision continuous borders (seamless miter joins on corners and smooth 72-segment curves) and **true contour-aware content fitting**:

- **Automatic Contour Fitting**: For non-rectangular shapes (triangles, circles, rhombuses, trapezoids, parallelograms, and regular polygons), each line of text or icon is positioned according to the actual horizontal width available at that exact vertical height ($Y$). Short lines fit neatly into narrow tips while longer lines settle into wider sections without overflowing angled or curved borders.
- **Automatic Content Scaling (`autoScaleContent`)**: When a fixed size is specified (`.setSize(width, height)`), the content is proportionally scaled up or down to fill the shape's interior cleanly. Without `.setSize(...)`, the shape automatically grows around its content.

| Shape | Factory Method | Description |
| :--- | :--- | :--- |
| **Rectangle** | `PanelShape.rectangle()` | Straight rectangle with 45° mitered border joins. |
| **Square** | `PanelShape.square()` | Square centered on the smaller dimension. |
| **Rounded Rectangle** | `PanelShape.roundedRectangle(radius)` | Uniform or per-corner rounded radii (`topLeft`, `topRight`, etc.). |
| **Circle** | `PanelShape.circle()` | Circle with 72 radial segments and smooth border. |
| **Ellipse** | `PanelShape.ellipse()` | Ellipse fitted to total width and height. |
| **Triangle** | `PanelShape.triangle()`, `equilateralTriangle()`, `rightTriangle()` | Isosceles, equilateral, or right triangle with orientation (`UP`, `DOWN`, `LEFT`, `RIGHT`). |
| **Rhombus (Diamond)** | `PanelShape.rhombus()` | Diamond connecting the midpoints of all four edges. |
| **Trapezoid** | `PanelShape.trapezoid(topWidthRatio)` | Trapezoid with adjustable top width ratio. |
| **Parallelogram** | `PanelShape.parallelogram(skewRatio)` | Parallelogram with adjustable horizontal skew. |
| **Regular Polygon** | `PanelShape.polygon(sides, rotation)` | Pentagon (`sides = 5`), Hexagon (`sides = 6`), Octagon (`sides = 8`), etc. |
| **PNG Silhouette** | `PanelShape.texture("modid:textures/gui/panel.png")` | Freeform shape based on a PNG texture's alpha silhouette. |

---

## ➖ Shape-Adaptive Separators (`addSeparator`)

`.addSeparator(...)` automatically calculates its horizontal length based on the shape's inner contour at the exact vertical height ($Y$) where it is placed:

```kotlin
window.addSeparator(
    color = WindowColor.Solid(0xFF808080L),
    thickness = 0.5f,
    margin = 2f,
    includeBorder = false, // Default: stops cleanly at the inner edge of the border
    edgeToEdge = true      // Default: spans from one side of the shape to the other at height Y
)
```

In curved shapes (circles/ellipses) or angled shapes (triangles/trapezoids), the separator follows the geometry of the window without ever poking through the border.

---

## 🧱 Volumetric 3D Panels (`PanelRenderMode.VOLUME_3D`)

In addition to flat rendering (`PanelRenderMode.FLAT_2D`), any shape can be rendered as a thick 3D plate complete with **front, shaded side walls, and back face**:

```kotlin
val volumetricPanel = FloatingWindow.builder()
    .setShape(PanelShape.roundedRectangle(cornerRadius = 4f))
    .setRenderMode(PanelRenderMode.VOLUME_3D)
    .setVolume3D(
        thickness = 3.0f,         // Physical depth along the Z axis
        sideColor = null,         // Automatically shaded from the front color
        sideDarkening = 0.65f,    // Side wall darkening factor
        lightAngleDegrees = 45f   // Light source angle for relief shading
    )
    .setBackground(WindowColor.Solid(0xFF2C3E50))
    .setBorder(WindowColor.Solid(0xFF1ABC9C), width = 1f)
    .addText("Volumetric 3D Panel", WindowColor.Solid(0xFFFFFFFF))
    .addSeparator()
    .addText("Side walls with physical depth")
    .build()
```

---

## 🔤 3D Signs & Backgroundless Floating Text (`FloatingText`)

To render standalone floating text in the world — such as boss titles, damage indicators, or physically extruded 3D signs:

### Single-Line Direct Call
```kotlin
FloatingText.render3D(
    poseStack = poseStack,
    buffer = buffer,
    text = "3D SIGN",
    y = 2.5,
    color = WindowColor.Rainbow(speed = 1.0f),
    extrusionDepth = 0.8f,
    packedLight = packedLight
)
```

### Multi-Line Builder
```kotlin
val sign = FloatingText.builder()
    .setOffset(0.0, 2.8, 0.0)
    .setScale(0.03f)
    .set3D(enabled = true, extrusionDepth = 0.6f)
    .addLine("FINAL BOSS", color = WindowColor.Solid(0xFFFF4500), textScale = 1.4f)
    .addLine("HP: 10,000 / 10,000", color = WindowColor.Solid(0xFF00FF00))
    .build()

sign.render(poseStack, buffer, packedLight)
```

*(You can also use `FloatingWindow.builder().textOnly()` if you want to use window layout features like item icons and text alignment without rendering a background or border).*

---

## 🌈 Colors & Visual Effects (`WindowColor`)

All background, border, separator, and text colors accept dynamic visual effects:

- `WindowColor.Solid(argb: Long)` — Static color (`0xAARRGGBB` or `0xRRGGBB`).
- `WindowColor.Gradient(argbStart, argbEnd, isVertical)` — Static linear gradient.
- `WindowColor.AnimatedGradient(argbStart, argbEnd, speed, scale)` — Continuously scrolling gradient.
- `WindowColor.Rainbow(speed, saturation, brightness)` — Animated rainbow cycle.
- `WindowColor.Fade(colorA, colorB, speed)` — Smooth periodic transition between two colors.
- `WindowColor.Pulse(color, minAlpha, speed)` — Continuous brightness/opacity pulse.

---

## 🤖 AI Assistance Disclosure

This project utilized Artificial Intelligence (AI) as an assistive tool during code development, geometric calculations, automated testing, and documentation writing.

---

## 📄 License

Distributed under the license specified in the [LICENSE](LICENSE) file.
