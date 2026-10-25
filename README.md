# WorldPanel [![English Version](https://img.shields.io/badge/lang-English%20Version-blue.svg)](README_EN.md)

**WorldPanel** é uma biblioteca em Kotlin para criar e renderizar painéis visuais, janelas flutuantes e textos 3D diretamente no mundo do Minecraft (NeoForge).

Ideal para exibir **etiquetas flutuantes sobre entidades**, **status de bonecos de treino (training dummies)**, **placas informativas de blocos/máquinas**, **barras de vida de chefes**, **letreiros 3D** e **interfaces imersivas no mundo**.

### Por que usar o WorldPanel?
- **Desacoplado e sob seu controle**: Não registra eventos globais ocultos nem interfere na renderização padrão do jogo. Você decide exatamente **quando**, **onde** e **para quem** renderizar chamando `.render(poseStack, buffer, packedLight)`.
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
        .addText(
            text = "HP: 100 / 100",
            color = WindowColor.Solid(0xFF4ADE80),
            shadow = true
        )
        .build()

    // 4. Renderiza na cena
    window.render(poseStack, buffer, packedLight)
}
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

## 🔤 Letreiros 3D e Texto Flutuante Sem Janela (`FloatingText`)

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
