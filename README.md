# WorldPanel [![English Version](https://img.shields.io/badge/lang-English%20Version-blue.svg)](README_EN.md)

**WorldPanel** é uma biblioteca em Kotlin para criar e renderizar painéis visuais, janelas flutuantes e textos 3D diretamente no mundo do Minecraft (NeoForge).

Ideal para exibir **etiquetas flutuantes sobre entidades**, **status de bonecos de treino (training dummies)**, **placas informativas de blocos/máquinas**, **barras de vida de chefes**, **letreiros 3D**, **cubos holográficos rotativos**, **prismas com múltiplas telas** e **interfaces imersivas no mundo**.

### Por que usar o WorldPanel?
- **Desacoplado e sob seu controle**: Não registra eventos globais ocultos nem interfere na renderização padrão do jogo. Você decide exatamente **quando**, **onde** e **para quem** renderizar chamando `.render(poseStack, buffer, packedLight)`.
- **Formas 3D Multifacetadas**: Crie poliedros 3D reais (`FloatingCube`, `FloatingPrism` e `FloatingCarousel`) onde **cada face é um painel independente** totalmente configurável, com rotação contínua (tumbling) ou travamento suave em faces específicas.
- **Elementos Prontos para HUDs**: Suporte nativo a barras de progresso/vida (`ProgressBar`), mostradores radiais (`RadialProgress`), grades de inventário (`ItemGrid`), presets instantâneos (`FloatingWindowPresets`) e utilitários anti-culling (`WorldPanelCulling`).
- **Recursos Avançados de Câmera e Iluminação**: Modo holograma animado (`setHologram`), brilho total neon (`setFullBright`), visibilidade através de paredes (`setSeeThrough`), fade por distância (`setDistanceFade`) e tamanho constante na tela (`setConstantScreenSize`).
- **Geometria e Layout Inteligentes**: Suporta desde retângulos arredondados até círculos, triângulos, hexágonos e losangos. O motor de layout ajusta automaticamente a posição e escala de cada linha de texto, ícone de item e separador para caber perfeitamente dentro do contorno real da forma escolhida.
- **Renderização 2D e 3D Volumétrica**: Crie desde painéis planos clássicos até placas 3D com espessura física, iluminação lateral e textos com extrusão 3D.
- **Cores e Efeitos Animados**: Gradientes estáticos ou animados, arco-íris (`Rainbow`), pulsação (`Pulse`) e transições suaves (`Fade`) prontos para uso em fundos, bordas e textos.

---

## 📚 Guias Passo a Passo

- 📘 [**Como Criar uma Janela Simples Passo a Passo**](docs/janela_simples.md) — Guia completo com exemplos de builder, tamanhos, padding, bordas, ícones de itens, ajuste automático à forma e onde chamar a renderização.
- 🔤 [**Como Criar Letreiro e Texto 3D**](docs/letreiro_e_texto_3d.md) — Guia ensinando a renderizar texto flutuante sem janela de fundo (`textOnly()`) e letreiros com extrusão 3D física via `FloatingText`.

---

## 📦 Versões Suportadas

| Minecraft | Loader | Artefato | Java | Kotlin | Language Provider |
| :--- | :--- | :--- | :---: | :---: | :--- |
| **1.21.1** | NeoForge 21.1.x | `worldpanel-neoforge-1.21.1` | Java 21 | 2.1.20 | KotlinForForge 5.10.0+ |
| **26.2** | NeoForge 26.2.x | `worldpanel-neoforge-26.2` | Java 25 | 2.4.21 | KotlinLangForge 2.15.0+ |

---

## 🚀 Como Adicionar ao seu Mod

Você pode adicionar o **WorldPanel** ao seu projeto pelo **Modrinth Maven** ou **CurseMaven**.

### No `build.gradle.kts` (Kotlin DSL)

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
    // Provedor Kotlin para NeoForge
    implementation("thedarkcolour:kotlinforforge-neoforge:5.10.0")

    // WorldPanel via Modrinth (substitua <versao> pela versão desejada)
    implementation("maven.modrinth:worldpanel:<versao>")

    // Ou via CurseMaven:
    // implementation("curse.maven:worldpanel-<project-id>:<file-id>")
}
```

### No `build.gradle` (Groovy DSL)

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
    // Provedor Kotlin para NeoForge
    implementation 'thedarkcolour:kotlinforforge-neoforge:5.10.0'

    // WorldPanel via Modrinth (substitua <versao> pela versão desejada)
    implementation "maven.modrinth:worldpanel:<versao>"

    // Ou via CurseMaven:
    // implementation "curse.maven:worldpanel-<project-id>:<file-id>"
}
```

---

## ⚡ Exemplo Rápido de Uso

Construa sua janela com `FloatingWindow.builder()` e chame `.render(poseStack, buffer, packedLight)` dentro do seu `EntityRenderer`, `BlockEntityRenderer` ou evento `RenderLevelStageEvent`:

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
        // 1. Posição e orientação no mundo
        .setFaceCamera(true)                       // Gira automaticamente para olhar o jogador
        .setOffset(0.0, 2.2, 0.0)                  // 2.2 blocos acima da origem
        .setScale(0.025f)                          // Escala no mundo 3D

        // 2. Formato e estilo visual
        .setShape(PanelShape.roundedRectangle(4f)) // Retângulo com cantos arredondados
        .setAlignment(WindowAlignment.CENTER)
        .setPadding(4f)
        .setBackground(WindowColor.Solid(0xDD181C2A))
        .setBorder(WindowColor.Solid(0xFF38BDF8), width = 0.75f)
        .setShadow(WindowColor.Solid(0x88000000), offset = 1.5f)

        // 3. Conteúdo da janela
        .addText(
            text = "Status da Entidade",
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

    // 4. Renderiza na cena
    window.render(poseStack, buffer, packedLight)
}
```

---

## 🎨 Presets Prontos Instantâneos (`FloatingWindowPresets`)

Para criar painéis sem precisar montar o layout do zero, utilize os templates prontos:

```kotlin
// Barra de vida de entidade ou chefe
val healthBar = FloatingWindowPresets.healthBar(
    name = "Dragão Ancião",
    currentHp = 180f,
    maxHp = 200f,
    level = 50
).build()

// Holograma futurista para máquinas e computadores
val holoDisplay = FloatingWindowPresets.hologramDisplay(
    title = "SISTEMA CENTRAL",
    subtext = "Operacional - 100%"
).build()

// Painel de energia de máquinas (FE/RF)
val machinePanel = FloatingWindowPresets.machineStatus(
    machineName = "Gerador Solar",
    storedEnergy = 75000L,
    maxEnergy = 100000L
).build()

// Placa de perigo / sinalização
val danger = FloatingWindowPresets.dangerSign(warningText = "ALTA TENSÃO").build()
```

---

## 📐 Sistema de Formas e Ajuste Inteligente de Conteúdo

O WorldPanel oferece diversas formas geométricas 2D com bordas contínuas de alta precisão (junções em meia-esquadria sem falhas nos cantos e curvas suaves de 72 segmentos) e **encaixe real de conteúdo ao contorno da forma**:

- **Ajuste Automático ao Contorno**: Em formas não retangulares (triângulos, círculos, losangos, trapézios, paralelogramos e polígonos), cada linha de texto ou ícone é posicionada levando em conta a largura disponível naquela altura exata ($Y$). Linhas curtas aproveitam pontas estreitas e linhas longas encaixam-se nas regiões mais largas, sem vazar pelas bordas inclinadas ou curvas.
- **Escala Automática (`autoScaleContent`)**: Se você definir um tamanho fixo (`.setSize(width, height)`), o conteúdo é ampliado ou reduzido proporcionalmente para preencher o interior da forma com perfeição. Sem `.setSize(...)`, a forma cresce automaticamente ao redor do conteúdo.

| Forma | Criação via Factory | Descrição |
| :--- | :--- | :--- |
| **Retângulo** | `PanelShape.rectangle()` | Retângulo reto com bordas em meia-esquadria de 45°. |
| **Quadrado** | `PanelShape.square()` | Quadrado centralizado na menor dimensão. |
| **Cantos Arredondados** | `PanelShape.roundedRectangle(radius)` | Cantos arredondados uniformes ou individuais por canto (`topLeft`, `topRight`, etc.). |
| **Círculo** | `PanelShape.circle()` | Círculo com 72 segmentos radiais e contorno suave. |
| **Elipse** | `PanelShape.ellipse()` | Elipse ajustada à largura e altura total. |
| **Triângulo** | `PanelShape.triangle()`, `equilateralTriangle()`, `rightTriangle()` | Isósceles, equilátero ou retângulo com orientação (`UP`, `DOWN`, `LEFT`, `RIGHT`). |
| **Losango (Diamond)** | `PanelShape.rhombus()` | Losango em diamante conectando os centros das arestas. |
| **Trapézio** | `PanelShape.trapezoid(topWidthRatio)` | Trapézio com proporção da base superior ajustável. |
| **Paralelogramo** | `PanelShape.parallelogram(skewRatio)` | Paralelogramo com inclinação lateral ajustável. |
| **Polígono Regular** | `PanelShape.polygon(sides, rotation)` | Pentágono (`sides = 5`), Hexágono (`sides = 6`), Octógono (`sides = 8`), etc. |
| **Silhueta PNG** | `PanelShape.texture("modid:textures/gui/panel.png")` | Forma livre baseada na silhueta alfa de uma textura PNG. |

---

## ➖ Separadores Adaptáveis à Forma (`addSeparator`)

O `.addSeparator(...)` calcula automaticamente seu comprimento horizontal de acordo com o contorno da forma na altura vertical ($Y$) onde está posicionado:

```kotlin
window.addSeparator(
    color = WindowColor.Solid(0xFF808080L),
    thickness = 0.5f,
    margin = 2f,
    includeBorder = false, // Padrão: para exatamente na face interna da borda
    edgeToEdge = true      // Padrão: acompanha de um lado ao outro da forma naquela altura Y
)
```

Em formas curvas (círculos/elipses) ou inclinadas (triângulos/trapézios), o separador acompanha a geometria da janela sem nunca atravessar ou vazar para fora da borda.

---

## 🧱 Painéis Volumétricos 3D (`PanelRenderMode.VOLUME_3D`)

Além do modo plano (`PanelRenderMode.FLAT_2D`), qualquer forma pode ser transformada em uma placa tridimensional espessa com **frente, laterais sombreadas e face traseira**:

```kotlin
val painel3D = FloatingWindow.builder()
    .setShape(PanelShape.roundedRectangle(cornerRadius = 4f))
    .setRenderMode(PanelRenderMode.VOLUME_3D)
    .setVolume3D(
        thickness = 3.0f,         // Espessura física no eixo Z
        sideColor = null,         // Sombra calculada automaticamente a partir da cor frontal
        sideDarkening = 0.65f,    // Intensidade de escurecimento das laterais
        lightAngleDegrees = 45f   // Ângulo da luz para efeito de relevo
    )
    .setBackground(WindowColor.Solid(0xFF2C3E50))
    .setBorder(WindowColor.Solid(0xFF1ABC9C), width = 1f)
    .addText("Painel 3D Volumétrico", WindowColor.Solid(0xFFFFFFFF))
    .addSeparator()
    .addText("Laterais com relevo físico")
    .build()
```

---

## 🎲 Formas 3D Multifacetadas (`FloatingCube`, `FloatingPrism` e `FloatingCarousel`)

O WorldPanel oferece poliedros 3D reais onde **cada face é uma `FloatingWindow` independente e totalmente configurável**:
- **Zero Z-Fighting:** As faces se encontram exatamente nos vértices das bordas sem sobreposição.
- **Backface Culling Compatível:** Normais apontando para fora, renderizando apenas as faces visíveis para a câmera.
- **Rotação Procedural & Alvo Dinâmico:** Giro contínuo (`animateTumble`, `animateSpin`) ou interpolação suave até uma face/lado específico virado para o jogador (`targetFace`, `targetSide`, `targetPanel`).

### 1. Cubo Holográfico 3D (`FloatingCube`)
```kotlin
val holographicCube = FloatingCube.builder()
    .setSize(size = 48f)
    .setScale(0.025f)
    .setOffset(0.5, 2.0, 0.5)
    // Animação: Giro contínuo suave nos 3 eixos (ou trave com .targetFace(CubeFace.FRONT))
    .animateTumble(speedX = 0.4f, speedY = 0.8f, speedZ = 0.2f)
    .configureFace(CubeFace.FRONT) { face ->
        face.setBackground(WindowColor.Solid(0xEE0F172A))
            .setBorder(WindowColor.Solid(0xFF38BDF8), width = 1f)
            .addText("STATUS PRINCIPAL", color = WindowColor.Solid(0xFF38BDF8))
            .addProgressBar(progress = 0.85f, width = 40f)
    }
    .configureFace(CubeFace.BACK) { face ->
        face.setBackground(WindowColor.Solid(0xEE1E1B4B))
            .addItemText(ItemStack(Items.NETHER_STAR), "Núcleo Estelar")
    }
    .configureFace(CubeFace.TOP) { face ->
        face.setBackground(WindowColor.Solid(0xEE064E3B))
            .addText("TELEMETRIA ORBITAL", color = WindowColor.Solid(0xFF34D399))
    }
    .build()

// No seu evento de renderização (BlockEntity, Entity ou RenderLevelStageEvent):
holographicCube.render(poseStack, bufferSource, packedLight)
```

### 2. Prisma Regular Hexagonal / N-Lados (`FloatingPrism`)
Perfeito para totens arcanos, colunas de energia e reatores modulares:
```kotlin
val prism = FloatingPrism.builder()
    .setSides(6) // Prisma hexagonal
    .setSize(width = 36f, height = 48f)
    .setScale(0.025f)
    .animateSpin(speedY = 0.5f)
    .configureSide(0) { side ->
        side.setBackground(WindowColor.Solid(0xDD111827))
            .addText("GERADOR A", WindowColor.Solid(0xFFF59E0B))
            .addProgressBar(progress = 0.9f, width = 30f)
    }
    .configureTop { top ->
        top.setBackground(WindowColor.Solid(0xFF1F2937))
            .addText("▲", WindowColor.Solid(0xFFF59E0B))
    }
    .build()
```

### 3. Carrossel / Roleta Circular 360° (`FloatingCarousel`)
Ideal para roletas de itens, seletores radiais de habilidades e vitrines giratórias:
```kotlin
val carousel = FloatingCarousel.builder()
    .setPanelCount(8)
    .setPanelSize(width = 32f, height = 32f)
    .setScale(0.025f)
    .targetPanel(0) // Trava suavemente no item selecionado
    .configurePanel(0) { p ->
        p.addItem(ItemStack(Items.DIAMOND_SWORD), size = 20f)
    }
    .configurePanel(1) { p ->
        p.addItem(ItemStack(Items.NETHERITE_CHESTPLATE), size = 20f)
    }
    .build()

// Alterne o painel selecionado com rotação suave:
carousel.nextPanel()
```

### 4. Pirâmide Regular 3D & Tetraedro (`FloatingPyramid`)
Suporte a base quadrada (`PyramidBase.SQUARE`) ou triangular (`PyramidBase.TRIANGLE`), calculando automaticamente os ângulos diedros (`dihedral angle`) e posicionando o conteúdo perfeitamente alinhado com a inclinação das faces:
```kotlin
val pyramid = FloatingPyramid.builder()
    .setBaseType(PyramidBase.SQUARE)
    .setSize(baseWidth = 48f, height = 40f)
    .setScale(0.025f)
    .animateSpin(speedY = 0.5f)
    .configureFace(PyramidFace.FACE_1) { face ->
        face.setBackground(WindowColor.Solid(0xEE0F172A))
            .setBorder(WindowColor.Solid(0xFFF59E0B), width = 1f)
            .addText("TEMPLO DO SOL", color = WindowColor.Solid(0xFFF59E0B))
    }
    .configureBase { base ->
        base.setBackground(WindowColor.Solid(0xFF1E293B))
    }
    .build()
```

### 5. Octaedro Regular 3D (`FloatingOctahedron`)
8 faces triangulares equiláteras simétricas em 3D, ideal para cristais de poder, núcleos espaciais e artefatos mágicos:
```kotlin
val octahedron = FloatingOctahedron.builder()
    .setSize(size = 40f)
    .setScale(0.025f)
    .animateTumble(speedX = 0.3f, speedY = 0.6f, speedZ = 0.2f)
    .configureFace(OctahedronFace.TOP_FRONT_RIGHT) { face ->
        face.setBackground(WindowColor.Solid(0xCC3B82F6))
            .setBorder(WindowColor.Solid(0xFF60A5FA), width = 1f)
            .addText("⚡", textScale = 1.4f)
    }
    .build()
```

### 6. Anel Orbital 3D / Torus Holográfico (`FloatingOrbitalRing`)
Anel poligonal 3D segmentado no espaço com espessura radial, profundidade e rotação orbital inclinada contínua (`orbitalTilt`, `spinSpeed`), com suporte a satélites flutuantes:
```kotlin
val orbitalRing = FloatingOrbitalRing.builder()
    .setRadius(48f)
    .setBandWidth(6f)
    .setDepth(3f)
    .setOrbitalTilt(25f)
    .animateSpin(0.4f)
    .setColor(WindowColor.Solid(0xCC00F0FF))
    .attachSatellite(angleDegrees = 0f) { sat ->
        sat.setBackground(WindowColor.Solid(0xEE0F172A))
            .addText("SATÉLITE ALPHA", color = WindowColor.Solid(0xFF38BDF8))
    }
    .build()
```

---

## 📺 Renderização & Transmissão de Entidades em Janelas

O WorldPanel permite exibir modelos de entidades (jogadores, mobs, companheiros) diretamente em qualquer janela:

### 1. Transmissão de Vídeo 2D (`addEntityVideoFeed`)
Exibe uma entidade em um monitor 2D totalmente achatado no plano da janela ($Z = 0$), sem que a geometria 3D escape para fora da tela. Suporta estilos visuais com filtros de câmera:
- `VideoFeedStyle.CLEAN` — Imagem limpa e nítida.
- `VideoFeedStyle.SECURITY_CAMERA_CCTV` — Monitor de segurança com indicador `REC ●` piscante, carimbo de data/hora (OSD) e scanlines horizontais.
- `VideoFeedStyle.CRT_SCANLINES` — Monitor retrô de tubo CRT com scanlines analógicas.
- `VideoFeedStyle.CYBER_GREEN_HOLOGRAM` — Visor holográfico verde neon com feixe laser de varredura animado.

```kotlin
val cctvMonitor = FloatingWindow.builder()
    .setShape(RectangleShape())
    .addText("SISTEMA DE SEGURANÇA", color = WindowColor.Solid(0xFF38BDF8))
    .addEntityVideoFeed(
        entity = targetMobOrPlayer,
        width = 64f,
        height = 48f,
        style = VideoFeedStyle.SECURITY_CAMERA_CCTV,
        spinSpeed = 0.3f // 0 = frontal estático, >0 = gira suavemente
    )
    .build()
```

### 2. Miniatura 3D Rotativa (`addEntityPreview`)
Ideal para bestiários, mostruários e telas de seleção:
```kotlin
window.addEntityPreview(
    entity = bossEntity,
    size = 40f,
    spinSpeed = 1.0f,
    pitchAngle = 10f
)
```

### 3. Espelho em Tempo Real (`addLiveEntityMirror`)
Exibe uma entidade viva espelhando suas animações corporais, movimento de pernas, equipamento e efeito de flash de dano vermelho em tempo real:
```kotlin
window.addLiveEntityMirror(
    entity = player,
    size = 42f,
    mirrorRotation = true
)
```

---

## 🗺️ Mapa Holográfico 3D de Terreno & Radar de Entidades (`FloatingHoloMap`)

O WorldPanel inclui um sistema de projeção holográfica volumétrica de terreno em tempo real estilo mesa tática militar / sci-fi:
- **Amostragem em Chunks Caching:** Coleta relevo de terreno e blocos de superfície sem impacto no FPS (atualização throttled a cada ~700ms).
- **Projeção Completa:** Anel emissor do pedestal, 4 feixes de luz nos cantos, colunas volumétricas de terreno com relevo real, laser de varredura de altitude e marcadores neon (blips) de entidades em tempo real.
- **Cores por Tipo de Entidade:** Ciano neon para jogadores, vermelho para hostis, esmeralda para passivos e amarelo para outras entidades.

### 1. Mesa Holográfica Independente (`FloatingHoloMap`)
```kotlin
val holoMap = FloatingHoloMap.builder()
    .setChunkRadius(1) // Raio de chunks ao redor do centro (1 = área de 3x3 chunks)
    .setSize(width = 140f, height = 90f)
    .setScale(0.025f)
    .setOffset(0.0, 1.5, 0.0)
    .setTiltPitch(25f) // Inclinação da mesa em relação à visão
    .animateSpin(speedY = 0.4f) // Rotação suave contínua
    .setWireframe(true) // Mistura relevo do terreno com tom holográfico
    .setShowEntityBlips(true) // Diamantes 3D flutuantes com feixes de luz
    .build()

// No seu renderizador:
holoMap.render(poseStack, bufferSource, packedLight)
```

### 2. Elemento de Mapa Embutido em Janela (`addHoloMap`)
Também pode ser incluído como elemento normal de layout em qualquer `FloatingWindow`:
```kotlin
val tacticalWindow = FloatingWindow.builder()
    .addText("RADAR DE SETOR", color = WindowColor.Solid(0xFF00F0FF))
    .addHoloMap(
        radiusChunks = 1,
        width = 64f,
        height = 48f,
        wireframeHologram = true,
        showEntityBlips = true,
        spinSpeedY = 0.5f
    )
    .build()
```

---

## 🏛️ Maquete Cúbica Real do Mundo 1:1 (`FloatingChunkDiorama`)

O `FloatingChunkDiorama` renderiza uma **maquete cúbica tridimensional real do mundo em miniatura**, exibindo blocos texturizados autênticos do Minecraft e entidades vivas em tempo real (com seus modelos, animações corporais e movimentos relativos 1:1).

### Destaques e Arquitetura:
- **Anti-Recursão Zero:** Prevenção automática de loops infinitos quando entidades no diorama possuem letreiros ou janelas anexadas (`IS_RENDERING_DIORAMA = ThreadLocal.withInitial { false }`).
- **Renderização Real de Blocos 1:1:** Cada bloco no raio de amostragem é renderizado com sua textura, modelo e geometria reais do Minecraft.
- **Oclusão Otimizada (`cullHiddenBlocks`):** Blocos totalmente encobertos por 6 vizinhos sólidos são descartados antes do pipeline de renderização, economizando drasticamente chamadas de desenho e mantendo FPS elevado.
- **Entidades Vivas em Tempo Real:** Jogadores, mobs e NPCs dentro da maquete movem-se e animam sincronizados com o mundo real.
- **Pedestal e Cúpula de Exibição:** Suporte a base de exibição com chanfro, redoma de vidro translúcida e moldura cúbica wireframe com cores customizáveis.
- **Controle de Rotação e Inclinação:** Rotação suave contínua (`animateSpin`) e inclinação isométrica (`setTiltPitch`).

### 1. Maquete Independente Flutuante no Mundo (`FloatingChunkDiorama`)
```kotlin
val diorama = FloatingChunkDiorama.builder()
    .setRadius(radiusX = 4, radiusZ = 4) // Volume 9x9 blocos
    .setHeight(heightBelow = 3, heightAbove = 5) // 3 blocos abaixo da superfície, 5 acima
    .setScale(0.035f)
    .setOffset(0.0, 1.8, 0.0)
    .animateSpin(speedY = 0.3f) // Gira suavemente a maquete
    .setTiltPitch(22f) // Inclinação isométrica para melhor visualização
    .setRenderEntities(true) // Renderiza entidades vivas em movimento
    .setCullHiddenBlocks(true) // Otimização extrema de blocos internos
    .setShowBasePlate(true) // Pedestal chanfrado elegante
    .setBasePlateColor(WindowColor.Solid(0xEE1E293B))
    .setShowGlassEnclosure(true) // Cúpula de vidro ao redor da maquete
    .setGlassColor(WindowColor.Solid(0x33E0F2FE))
    .build()

// No seu renderizador (ex: RenderLevelStageEvent):
diorama.render(poseStack, bufferSource, packedLight)
```

### 2. Elemento Embutido em Janela (`addChunkDiorama`)
Você também pode embutir uma maquete cúbica viva diretamente dentro de qualquer painel flutuante `FloatingWindow`:
```kotlin
val architecturalWindow = FloatingWindow.builder()
    .addText("MAQUETE VIVA DO LOCAL", color = WindowColor.Solid(0xFF38BDF8))
    .addChunkDiorama(
        radiusX = 3,
        radiusZ = 3,
        heightBelow = 2,
        heightAbove = 4,
        width = 72f,
        height = 54f,
        renderEntities = true,
        spinSpeedY = 0.4f,
        tiltPitch = 25f
    )
    .build()
```

---

## 🔤 Letreiros 3D, Texto Volumétrico e Texto Flutuante

O WorldPanel oferece tanto letreiros minimalistas sem janela (`FloatingText`) quanto texto com **geometria 3D volumétrica real** (`FloatingVolumetricText`):

### 1. Texto Volumétrico 3D Extrudado Real (`FloatingVolumetricText`)
Diferente de camadas 2D transladadas ou caixas delimitadoras artificiais, o `FloatingVolumetricText` constrói **geometria 3D extrudada real** para cada caractere:
- **Contornos Tipográficos Reais:** Extrusão dos contornos individuais de cada letra, preservando vazios internos (em letras como `O`, `A`, `B`, `D`, `P`, `R` e dígitos `0`, `4`, `6`, `8`, `9`), curvas e concavidades (como em `W`, `M`, `V`), sem superfícies contínuas indevidas no topo ou no fundo.
- **Vazios e Recortes Preservados:** Os espaços internos e vãos entre letras permanecem abertos, permitindo enxergar através do volume tridimensional.
- **Faces Traseiras Alinhadas:** Face posterior de cada glifo invertida e alinhada pixel a pixel com a face frontal na profundidade física configurada (`setThickness`).
- **Sombreamento Direcional com Normais Exatas:** Paredes e facetas laterais calculam iluminação direcional física baseada nas normais de cada segmento e no ângulo de luz (`sideDarkening`, `lightAngleDegrees`).
- **Rotação Livre nos 3 Eixos:** Gira no espaço com `animateSpin` ou `animateTumble` sem distorção geométrica.

```kotlin
val volumetricSign = FloatingVolumetricText.builder()
    .setText("WORLD PANEL", WindowColor.Rainbow(speed = 1.0f))
    .setThickness(2.5f)
    .setBevel(0.3f)
    .setSideDarkening(0.65f)
    .setLightAngle(45f)
    .setScale(0.035f)
    .setOffset(0.0, 2.5, 0.0)
    .animateTumble(speedX = 0.2f, speedY = 0.8f, speedZ = 0.1f)
    .build()

volumetricSign.render(poseStack, bufferSource, packedLight)
```

### 2. Letreiros 3D Rápidos (`FloatingText`)

Para renderizar apenas texto flutuante no mundo — como nomes de chefes, indicadores de dano ou letreiros 3D com profundidade física:

### Chamada Direta em Uma Linha
```kotlin
FloatingText.render3D(
    poseStack = poseStack,
    buffer = buffer,
    text = "LETREIRO 3D",
    y = 2.5,
    color = WindowColor.Rainbow(speed = 1.0f),
    extrusionDepth = 0.8f,
    packedLight = packedLight
)
```

### Múltiplas Linhas com Builder
```kotlin
val letreiro = FloatingText.builder()
    .setOffset(0.0, 2.8, 0.0)
    .setScale(0.03f)
    .set3D(enabled = true, extrusionDepth = 0.6f)
    .addLine("BOSS FINAL", color = WindowColor.Solid(0xFFFF4500), textScale = 1.4f)
    .addLine("Vida: 10.000 / 10.000", color = WindowColor.Solid(0xFF00FF00))
    .build()

letreiro.render(poseStack, buffer, packedLight)
```

*(Você também pode usar `FloatingWindow.builder().textOnly()` se quiser aproveitar o layout da janela com ícones de itens e textos sem renderizar fundo nem borda).*

---

## 🌈 Cores e Efeitos Visuais (`WindowColor`)

Todas as cores de fundo, borda, separadores e textos aceitam efeitos visuais dinâmicos:

- `WindowColor.Solid(argb: Long)` — Cor estática (`0xAARRGGBB` ou `0xRRGGBB`).
- `WindowColor.Gradient(argbStart, argbEnd, isVertical)` — Gradiente linear estático.
- `WindowColor.AnimatedGradient(argbStart, argbEnd, speed, scale)` — Gradiente em movimento contínuo.
- `WindowColor.Rainbow(speed, saturation, brightness)` — Efeito arco-íris animado.
- `WindowColor.Fade(colorA, colorB, speed)` — Transição suave alternando entre duas cores.
- `WindowColor.Pulse(color, minAlpha, speed)` — Pulsação contínua de brilho/opacidade.

---

## 🤖 Nota de Transparência (Uso de IA)

Este projeto contou com o auxílio de Inteligência Artificial (IA) como ferramenta de assistência no desenvolvimento de código, cálculos geométricos, testes automatizados e elaboração da documentação.

---

## 📄 Licença

Distribuído sob a licença especificada no arquivo [LICENSE](LICENSE).
