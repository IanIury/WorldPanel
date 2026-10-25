# Guide: How to Render 3D Signs, Floating Text, and World Animations [![Português](https://img.shields.io/badge/lang-Portugu%C3%AAs%20(BR)-green.svg)](letreiro_e_texto_3d.md)

This guide explains how to render **standalone floating text** (without background plates or windows), create **physically extruded 3D signs**, apply **procedural movement and rotation animations** (dropped-item style or point-to-point patrolling), and troubleshoot frustum culling and visibility issues in **Entities and Block Entities** using **WorldPanel**.

---

## 1. Available Approaches

WorldPanel offers two ways to render windowless text:

1. **`FloatingText` (Dedicated API)**: A clean, direct API built specifically for 3D signs, floating nametags, and titles with built-in animations.
2. **`FloatingWindow.builder().textOnly()`**: Lets you use the full power of `FloatingWindow` (rows, item icons, separators) without rendering the background, border, or shadow.

---

## 2. Animated 3D Sign (Dropped Item Style)

You can create a floating 3D sign in the world with continuous rotation and smooth vertical bobbing, just like a dropped item on the ground in Minecraft:

```kotlin
import dev.ian.worldpanel.FloatingText
import dev.ian.worldpanel.WindowColor
import dev.ian.worldpanel.WindowAlignment

val droppedSign = FloatingText.builder()
    .setOffset(0.0, 2.2, 0.0)
    .setScale(0.035f)
    .setAlignment(WindowAlignment.CENTER)
    .setFixedRotation(yaw = 0f, pitch = 0f) // Fixed in world space rather than facing the camera
    .set3D(enabled = true, extrusionDepth = 0.8f) // Double-sided 3D extruded letters
    .animateDroppedItem(bobAmplitude = 0.15, spinSpeed = 1.0f) // Spins 360° and bobs up/down
    .addLine(
        text = "★ LEGENDARY SWORD ★",
        color = WindowColor.Rainbow(speed = 0.8f),
        shadow = true,
        textScale = 1.2f
    )
    .build()

// During rendering:
droppedSign.render(poseStack, buffer, packedLight)
```

Or using the single-call helper method:

```kotlin
FloatingText.renderAnimated3D(
    poseStack = poseStack,
    buffer = buffer,
    text = "★ REWARD ★",
    x = 0.0, y = 2.0, z = 0.0,
    color = WindowColor.AnimatedGradient(0xFFFFD700, 0xFFFF4500, 1.0f),
    extrusionDepth = 0.8f,
    scale = 0.035f,
    packedLight = packedLight,
    bobAmplitude = 0.12,
    spinSpeed = 1.2f
)
```

---

## 3. Movement and Rotation Animation Types

WorldPanel supports chaining animations on the builder of both `FloatingText` and `FloatingWindow`:

### A. Continuous Rotation (`animateSpin`)
Rotates the text or panel continuously around its own axis (default: vertical `Y` axis):
```kotlin
.animateSpin(speed = 1.0f, axis = "Y")
```

### B. Vertical Bobbing (`animateBobbing`)
Makes the text or window smoothly oscillate up and down:
```kotlin
.animateBobbing(amplitude = 0.2, speed = 1.0f) // amplitude in blocks
```

### C. Dropped Item Style (`animateDroppedItem`)
Combines continuous 360° rotation with sine-wave vertical bobbing:
```kotlin
.animateDroppedItem(bobAmplitude = 0.15, spinSpeed = 1.0f, bobSpeed = 1.0f)
```

### D. Relative Ping-Pong Motion (`animateMoveOffset`)
Moves the panel from its origin to a relative offset and smoothly returns:
```kotlin
// Moves 0.5 blocks up and 1 block sideways, then returns
.animateMoveOffset(dx = 1.0, dy = 0.5, dz = 0.0, speed = 1.0f)
```

### E. Point-to-Point Motion (`animateMoveBetween`)
Continuously interpolates the position between two coordinates:
```kotlin
.animateMoveBetween(
    startX = 0.0, startY = 1.0, startZ = 0.0,
    endX = 2.0, endY = 3.0, endZ = 0.0,
    speed = 0.5f
)
```

---

## 4. Fixed World Orientation or Block/Entity Alignment

If you don't want the text or panel to rotate toward the player's camera (`setFaceCamera(true)`):

### A. Fixed orientation in degrees
```kotlin
.setFaceCamera(false)
.setFixedRotation(yaw = 90f, pitch = 0f, roll = 0f)
```

### B. Orientation Relative to Entity Body or Block Face
By default, when you use `.attachToEntityRotation(entity)` or `.attachToBlockFacing(direction)`, the offset configured in `.setOffset(ox, oy, oz)` is **automatically interpreted relative to the body/face**:
- **`ox` (Lateral)**: Left/right offset (e.g., over the entity's shoulder). Always stays on the same side regardless of whether the entity faces North, South, East, or West.
- **`oy` (Height)**: Vertical offset.
- **`oz` (Depth/Forward)**: Forward/backward offset relative to the entity or block face.

```kotlin
// The window will ALWAYS stay 0.9 blocks to the side of the shoulder and 2.0 blocks high,
// rotating along with the entity and keeping a consistent lateral position in any direction!
window
    .attachToEntityRotation(entity)
    .setOffset(0.9, 2.0, 0.0)
    .build()
```

If you ever want to force absolute world coordinates (where X is always global East and Z is global South):
```kotlin
.setWorldOffset(worldX, worldY, worldZ) // or .setOffsetRelativeToOrientation(false)
```

### C. Aligning with a Block Face
For panels mounted on blocks (e.g., the front of a Display Block facing North/South/East/West):
```kotlin
.attachToBlockFacing(Direction.NORTH) // or attachToBlockFacing(state.getValue(BlockStateProperties.HORIZONTAL_FACING))
```

### D. Aligning with an Entity's Body
```kotlin
.attachToEntityRotation(myEntity)
```

Letters with `.set3D(enabled = true)` as well as all panel shapes and fills feature **automatic double-sided rendering (front and back)**, keeping the panel 100% visible from any camera angle.

---

## 5. Difference Between `set3D()` (Text) and `setVolume3D()` (Panel)

Make sure not to confuse the library's two 3D features:

| Method | What it does | Where to use it |
|---|---|---|
| `.set3D(enabled, depth)` | Gives **physical 3D extrusion to letters/text** | Standalone signs, floating names, windowless titles |
| `.setVolume3D(config)` | Gives **physical 3D thickness to the panel plate/box** | Plates, windows, and HUDs that look like solid 3D objects |

### Using `setVolume3D` to create a physical plate:
```kotlin
val plate3D = FloatingWindow.builder()
    .setShape(PanelShape.roundedRectangle(4f))
    .setBackground(WindowColor.Solid(0xFF1E293B))
    .setVolume3D(
        thickness = 3.0f, // 3 pixels of physical depth
        sideColor = WindowColor.Solid(0xFF0F172A), // Color of the 3D side walls
        sideDarkening = 0.6f, // Directional shading on side faces
        lightAngleDegrees = 45f // Simulated light angle
    )
    .addText("3D METAL PLATE", WindowColor.Solid(0xFF38BDF8))
    .build()
```

---

## 6. Troubleshooting Common Issues

### Issue 1: A Block Entity's floating window disappears when looking up or at steep angles
**Cause:** Minecraft's Frustum Culling in **`BlockEntityRenderer`**.
In Minecraft, a block occupies its `blockPos` (a 1×1×1 bounding box). When you place a floating window high above the block using an offset (e.g., `.setOffset(0.5, 2.5, 0.5)`) and the player looks up at the window while the block itself moves off-screen, Minecraft checks whether the block's 1×1×1 `AABB` is inside the camera frustum. Because the block on the ground is outside the screen, Minecraft **skips rendering the entire block** and never calls your `BlockEntityRenderer.render(...)`!

**How to fix it in your `BlockEntityRenderer` (Recommended):**
Override `shouldRenderOffScreen` in your `BlockEntityRenderer`:

```kotlin
class DisplayBlockEntityRenderer(context: BlockEntityRendererProvider.Context) : BlockEntityRenderer<DisplayBlockEntity> {

    // Tells Minecraft to NEVER cancel rendering due to frustum culling
    // (Minecraft uses this in BeaconRenderer and StructureBlockRenderer).
    override fun shouldRenderOffScreen(blockEntity: DisplayBlockEntity): Boolean {
        return true
    }

    override fun getViewDistance(): Int {
        return 128 // Maximum view distance in blocks
    }

    override fun render(
        blockEntity: DisplayBlockEntity,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int
    ) {
        // Render your window here as usual:
        // window.render(poseStack, bufferSource, packedLight)
    }
}
```

**Complementary fix in your `BlockEntity` class:**
Expand the render bounding box in your `BlockEntity` (on NeoForge):
```kotlin
override fun getRenderBoundingBox(): net.minecraft.world.phys.AABB {
    // Expands the bounding box 4 blocks vertically and 2 blocks horizontally
    return net.minecraft.world.phys.AABB(blockPos).inflate(2.0, 4.0, 2.0)
}
```

---

### Issue 2: An Entity's floating window disappears when looking up
**Cause:** Frustum Culling in `EntityRenderer`.
In your entity renderer, override `shouldRender`:
```kotlin
override fun shouldRender(
    entity: DummyEntity,
    camera: net.minecraft.client.renderer.culling.Frustum,
    camX: Double,
    camY: Double,
    camZ: Double
): Boolean {
    val expandedBox = entity.boundingBox.inflate(1.5, 3.5, 1.5)
    return camera.isVisible(expandedBox)
}
```

---

### Issue 3: The background or side walls of `setVolume3D` appear black or transparent
**Cause:** ARGB Hexadecimal Color Format.
In Minecraft and WorldPanel, hexadecimal colors use **ARGB** (`0xAARRGGBB`):
- `0xFFFFFFFF` = 100% Alpha (fully opaque)
- `0x3C3C3CAA` = Alpha `0x3C` (~23% opacity!).

To get solid colors, always start with `0xFF` in the first two hex digits (e.g., `0xFF3C3CAA`).

### Letters as True Solid 3D Objects (Dense Orthogonal Extrusion)
`.set3D(enabled = true, extrusionDepth = 1.0f)` generates a **dense physical 3D block**:
- **Pure Orthogonal Z-Axis Extrusion:** Slices are projected directly along the $Z$ thickness rather than diagonally, creating solid continuous side walls.
- **Dense Micro-Slicing (10 to 48 layers):** Eliminates visible gaps when viewing letters from the side, top, or bottom.
- **Internal Volumetric Shading:** Inner core layers receive darker depth shading, simulating ambient occlusion and physical side faces.
- **True Double-Sided Rendering:** The back face is rotated 180° and aligned so letters remain readable from both front and back when spinning 360°.

Recommended `extrusionDepth` values:
- `0.4f` to `0.6f`: Subtle, sleek thickness.
- `0.8f` to `1.2f`: Solid 3D block letters.
- `1.5f` to `2.5f`: Deep extruded sign for long-distance visibility.
