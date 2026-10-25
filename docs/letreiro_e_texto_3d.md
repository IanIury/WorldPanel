# Guia: Como Renderizar Letreiro, Texto 3D e Animações no Mundo [![English Version](https://img.shields.io/badge/lang-English%20Version-blue.svg)](3d_signs_and_text_en.md)

Este guia ensina como renderizar **textos soltos** (sem placas ou janelas de fundo), criar **letreiros 3D com relevo físico**, aplicar **animações procedurais de movimento e rotação** (estilo item dropado ou patrulha entre pontos) e diagnosticar problemas de culling e visibilidade em **Entidades e Blocos (Block Entities)** usando o **WorldPanel**.

---

## 1. Métodos Disponíveis

O WorldPanel oferece duas abordagens para renderizar textos sem janela:

1. **`FloatingText` (Dedicado)**: Uma API limpa e direta criada especialmente para letreiros, nomes flutuantes e títulos 3D com animações integradas.
2. **`FloatingWindow.builder().textOnly()`**: Permite usar todo o poder do `FloatingWindow` (linhas, ícones, itens, separadores), mas sem renderizar fundo, borda ou sombras.

---

## 2. Letreiro 3D Animado (Estilo Item Dropado)

Você pode criar um letreiro 3D flutuante no mundo com rotação contínua e flutuação suave (subindo e descendo), idêntico a um item dropado no chão do Minecraft:

```kotlin
import dev.ian.worldpanel.FloatingText
import dev.ian.worldpanel.WindowColor
import dev.ian.worldpanel.WindowAlignment

val letreiroDropado = FloatingText.builder()
    .setOffset(0.0, 2.2, 0.0)
    .setScale(0.035f)
    .setAlignment(WindowAlignment.CENTER)
    .setFixedRotation(yaw = 0f, pitch = 0f) // Não segue o player, fica fixo no mundo
    .set3D(enabled = true, extrusionDepth = 0.8f) // Efeito 3D nas letras com dupla face
    .animateDroppedItem(bobAmplitude = 0.15, spinSpeed = 1.0f) // Gira 360° e sobe/desce
    .addLine(
        text = "★ ESPADA LENDÁRIA ★",
        color = WindowColor.Rainbow(speed = 0.8f),
        shadow = true,
        textScale = 1.2f
    )
    .build()

// No render:
letreiroDropado.render(poseStack, buffer, packedLight)
```

Ou usando o atalho em uma única chamada:

```kotlin
FloatingText.renderAnimated3D(
    poseStack = poseStack,
    buffer = buffer,
    text = "★ RECOMPENSA ★",
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

## 3. Tipos de Animação de Movimento e Rotação

O WorldPanel suporta encadeamento de animações no builder tanto do `FloatingText` quanto do `FloatingWindow`:

### A. Rotação Contínua (`animateSpin`)
Gira o texto continuamente no próprio eixo (padrão: eixo Y vertical):
```kotlin
.animateSpin(speed = 1.0f, axis = "Y")
```

### B. Flutuação / Subir e Descer (`animateBobbing`)
Faz o texto ou a janela oscilar suavemente para cima e para baixo:
```kotlin
.animateBobbing(amplitude = 0.2, speed = 1.0f) // amplitude em blocos
```

### C. Estilo Item Dropado (`animateDroppedItem`)
Combina rotação contínua de 360° com flutuação vertical em ondas senoidais:
```kotlin
.animateDroppedItem(bobAmplitude = 0.15, spinSpeed = 1.0f, bobSpeed = 1.0f)
```

### D. Movimento Relativo / Ping-Pong (`animateMoveOffset`)
Move o painel a partir de sua posição atual até um deslocamento relativo e volta suavemente:
```kotlin
// Move 0.5 blocos para cima e 1 bloco para o lado, depois retorna
.animateMoveOffset(dx = 1.0, dy = 0.5, dz = 0.0, speed = 1.0f)
```

### E. Movimento entre Dois Pontos (`animateMoveBetween`)
Interpola continuamente a posição entre duas coordenadas do mundo:
```kotlin
.animateMoveBetween(
    startX = 0.0, startY = 1.0, startZ = 0.0,
    endX = 2.0, endY = 3.0, endZ = 0.0,
    speed = 0.5f
)
```

---

## 4. Orientação Fixa no Mundo ou Alinhada a Blocos/Entidades

Se você não quer que o texto ou painel vire na direção da câmera do jogador (`setFaceCamera(true)`):

### A. Orientação fixa em graus
```kotlin
.setFaceCamera(false)
.setFixedRotation(yaw = 90f, pitch = 0f, roll = 0f)
```

### B. Orientação Relativa ao Corpo da Entidade ou Face do Bloco
Por padrão, quando você utiliza `.attachToEntityRotation(entity)` ou `.attachToBlockFacing(direction)`, o offset configurado em `.setOffset(ox, oy, oz)` é interpretado **automaticamente como relativo ao corpo/face**:
- **`ox` (Lateral)**: deslocamento para a esquerda/direita do boneco (ombro). Sempre fica no mesmo lado, independente de para onde o boneco está virado (North, South, East ou West).
- **`oy` (Altura)**: deslocamento vertical.
- **`oz` (Profundidade/Frente)**: deslocamento para frente/trás do boneco.

```kotlin
// A janela SEMPRE ficará 0.9 blocos ao lado do ombro e 2.0 blocos de altura,
// girando junto com o boneco e mantendo a posição lateral consistente em qualquer direção!
window
    .attachToEntityRotation(entity)
    .setOffset(0.9, 2.0, 0.0)
    .build()
```

Se em algum caso especial você quiser forçar coordenadas absolutas do mundo (onde X é sempre East global e Z é South global):
```kotlin
.setWorldOffset(worldX, worldY, worldZ) // ou .setOffsetRelativeToOrientation(false)
```

### B. Alinhamento com a Face de um Bloco
Para painéis montados em blocos (ex: frente de um Display Block com direção Norte/Sul/Leste/Oeste):
```kotlin
.attachToBlockFacing(Direction.NORTH) // ou attachToBlockFacing(state.getValue(BlockStateProperties.HORIZONTAL_FACING))
```

### C. Alinhamento com o Corpo de uma Entidade
```kotlin
.attachToEntityRotation(minhaEntidade)
```

As letras com `.set3D(enabled = true)` e todas as formas e preenchimentos possuem **renderização dupla face automática (frente e verso)**, permitindo que o painel continue 100% visível de qualquer ângulo da câmera.

---

## 5. Diferença entre `set3D()` (Texto) e `setVolume3D()` (Painel)

É muito importante não confundir as duas funcionalidades 3D da biblioteca:

| Método | O que faz | Onde usar |
|---|---|---|
| `.set3D(enabled, depth)` | Dá **relevo físico 3D às letras/textos** | Letreiros soltos, nomes flutuantes, títulos sem janela |
| `.setVolume3D(config)` | Dá **espessura física 3D à caixa/placa do painel** | Placas, janelas e HUDs que parecem blocos/objetos sólidos |

### Como usar `setVolume3D` para criar uma placa física:
```kotlin
val placa3D = FloatingWindow.builder()
    .setShape(PanelShape.roundedRectangle(4f))
    .setBackground(WindowColor.Solid(0xFF1E293B))
    .setVolume3D(
        thickness = 3.0f, // 3 pixels de espessura física no mundo
        sideColor = WindowColor.Solid(0xFF0F172A), // Cor das paredes laterais 3D
        sideDarkening = 0.6f, // Sombra direcional nas faces laterais
        lightAngleDegrees = 45f // Ângulo da iluminação simulada
    )
    .addText("PLACA METÁLICA 3D", WindowColor.Solid(0xFF38BDF8))
    .build()
```

---

## 6. Solução de Problemas Comuns (Troubleshooting)

### Problema 1: A janela do Display Block some quando olho de baixo para cima ou em certos ângulos
**Causa:** Frustum Culling em **`BlockEntityRenderer`** do Minecraft.
No Minecraft, um bloco ocupa a posição `blockPos` (uma caixa de 1x1x1 bloco).
Quando você coloca a janela suspensa acima do bloco com um offset (ex: `.setOffset(0.5, 2.5, 0.5)`), e você:
1. Fica embaixo do bloco ou perto dele e olha para cima em direção à janela;
2. Ou olha para a janela de lado, de modo que o bloco no chão fique fora da borda da tela:

O Minecraft testa se o bloco em si (`new AABB(blockPos)`) está dentro do campo de visão da câmera (frustum). Como o bloco físico saiu da tela, o Minecraft **cancela a renderização de todo o bloco** e não executa o método `render(...)` do seu `BlockEntityRenderer`!

**Como resolver no seu `BlockEntityRenderer` (Método Recomendado):**
No seu renderer (que implementa `BlockEntityRenderer<DisplayBlockEntity>`), sobrescreva `shouldRenderOffScreen`:

```kotlin
class DisplayBlockEntityRenderer(context: BlockEntityRendererProvider.Context) : BlockEntityRenderer<DisplayBlockEntity> {

    // Diz ao Minecraft para NUNCA cancelar a renderização por frustum culling.
    // O Minecraft usa isso no BeaconRenderer (Farol) e StructureBlockRenderer porque
    // eles desenham efeitos altos no céu longe do bloco original!
    override fun shouldRenderOffScreen(blockEntity: DisplayBlockEntity): Boolean {
        return true
    }

    override fun getViewDistance(): Int {
        return 128 // Distância máxima em blocos para visualização
    }

    override fun render(
        blockEntity: DisplayBlockEntity,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int
    ) {
        // Renderize sua janela aqui normalmente:
        // window.render(poseStack, bufferSource, packedLight)
    }
}
```

**Solução complementar na classe do seu `BlockEntity`:**
Na sua classe `DisplayBlockEntity` (no NeoForge), expanda a caixa de renderização:
```kotlin
override fun getRenderBoundingBox(): net.minecraft.world.phys.AABB {
    // Expande a caixa 4 blocos para cima e 2 blocos para os lados
    return net.minecraft.world.phys.AABB(blockPos).inflate(2.0, 4.0, 2.0)
}
```

---

### Problema 2: A janela de uma Entidade (`DummyEntity`) some ao olhar para cima
**Causa:** Frustum Culling em `EntityRenderer`.
No renderer da entidade (`DummyRenderer.kt`), sobrescreva `shouldRender`:
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

### Problema 3: O fundo ou as laterais do `setVolume3D` ficaram pretas ou transparentes
**Causa:** Formato de Cor Hexadecimal ARGB.
No Minecraft e no WorldPanel, cores hexadecimais utilizam o formato **ARGB** (`0xAARRGGBB`):
- `0xFFFFFFFF` = Alpha 100% (Totalmente opaco)
- `0x3C3C3CAA` = Alpha `0x3C` (apenas ~23% de opacidade!). O fundo e as laterais inferiores ficaram quase invisíveis devido à transparência extrema da cor final do gradiente.

Para obter cores sólidas, sempre use `0xFF` nos dois primeiros dígitos:
- Em vez de `0x3C3C3CAA`, use `0xFF3C3CAA`.

### Letras como Objeto 3D Físico Real (Extrusão Ortogonal Densa)
O modo `.set3D(enabled = true, extrusionDepth = 1.0f)` foi aprimorado para gerar um **bloco tridimensional físico e denso**:
- **Extrusão Ortogonal Pura no Eixo Z:** As fatias não são mais deslocadas na diagonal (que dava um efeito de sombra/relevo 2.5D). Elas são projetadas diretamente ao longo da espessura $Z$, criando faces laterais sólidas e contínuas como um modelo 3D real de bloco de letras.
- **Micro-fatiamento denso (10 a 48 camadas):** Elimina lacunas visíveis e buracos quando você olha as letras de lado, de cima ou de baixo.
- **Sombreamento volumétrico interno:** As camadas internas do miolo das letras recebem sombreamento de profundidade mais escuro, simulando oclusão de ambiente e faces laterais físicas.
- **Dupla face perfeita:** A face traseira é rotacionada 180° e alinhada na coordenada base, permitindo que as letras sejam lidas por quem está na frente e por quem está atrás quando o letreiro gira 360°.

Valores recomendados de `extrusionDepth`:
- `0.4f` a `0.6f`: Espessura sutil e elegante.
- `0.8f` a `1.2f`: Letras em bloco 3D sólido (destaque volumétrico real).
- `1.5f` a `2.5f`: Letreiro gigante com espessura profunda para visualização à distância.
