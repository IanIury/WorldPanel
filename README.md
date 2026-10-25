# WorldPanel

WorldPanel é uma biblioteca Kotlin para criar e renderizar painéis visuais no mundo 3D do Minecraft (Minecraft/NeoForge): etiquetas flutuantes sobre entidades, status de bonecos de treino (training dummies), placas, letreiros 3D, barras de vida estilizadas, janelas geométricas e interfaces imersivas no mundo.

A biblioteca foi projetada de forma modular, limpa e desacoplada: **não possui classe `@Mod` obrigatória** e **não registra eventos globais automaticamente**. O mod consumidor retém controle total sobre o momento, estágio de renderização, posicionamento, luz e visibilidade do painel.

---

## 📚 Guias Rápidos Práticos

- 📘 [**Como Criar uma Janela Simples Passo a Passo**](docs/janela_simples.md): Guia completo com exemplos de builder, tamanhos, padding, bordas, ícones de itens e renderização.
- 🔤 [**Como Criar Letreiro e Texto 3D**](docs/letreiro_e_texto_3d.md): Guia ensinando a renderizar texto flutuante sem janela de fundo (`textOnly()`) e letreiros com extrusão 3D física via `FloatingText`.

---

## 📦 Versões e Alvos Suportados

| Minecraft / Loader | Artefato | Java | Kotlin | Language Provider |
| :--- | :--- | :---: | :---: | :--- |
| **1.21.1** (NeoForge 21.1.x) | `worldpanel-neoforge-1.21.1` | Java 21 | 2.1.20 | KotlinForForge 5.10.0 |
| **26.2** (NeoForge 26.2.x) | `worldpanel-neoforge-26.2` | Java 25 | 2.4.21 | KotlinLangForge 2.15.0+ |
| **Agnóstico (Core)** | `worldpanel-core` | Java 21+ | 2.x | 100% Kotlin Puro (Sem Minecraft) |

> [!NOTE]
> O artefato `worldpanel-neoforge-1.21.1` embute o código do `core` e inclui metadados `META-INF/neoforge.mods.toml` configurados como `modLoader="lowcodefml"`. Isso permite que o NeoForge FML carregue as classes da biblioteca diretamente na camada de módulos em tempo de execução (`runClient`) e distribuição, sem exigir nenhuma classe `@Mod`.

---

## 🚀 Como Adicionar ao seu Mod

### 1. Publicação Local (Maven Local)
Enquanto a biblioteca não estiver publicada em um repositório Maven remoto público, publique-a no repositório Maven local da sua máquina executando na raiz do projeto WorldPanel:

```bash
./gradlew publishAllToMavenLocal
```

### 2. Configurando o Projeto Consumidor

#### No `build.gradle` (Groovy DSL):
```groovy
repositories {
    mavenLocal() // Obrigatório para localizar os artefatos SNAPSHOT locais
    mavenCentral()
    maven { url = "https://thedarkcolour.github.io/KotlinForForge/" }
}

dependencies {
    // Provedor Kotlin para NeoForge 1.21.1
    implementation 'thedarkcolour:kotlinforforge-neoforge:5.10.0'

    // WorldPanel para 1.21.1
    implementation 'dev.ian.worldpanel:worldpanel-neoforge-1.21.1:0.1.0-SNAPSHOT'

    // Empacotamento Jar-in-Jar (JiJ) para distribuição aos jogadores
    jarJar 'dev.ian.worldpanel:worldpanel-neoforge-1.21.1:0.1.0-SNAPSHOT'
}
```

#### No `build.gradle.kts` (Kotlin DSL):
```kotlin
repositories {
    mavenLocal() // Obrigatório para localizar os artefatos SNAPSHOT locais
    mavenCentral()
    maven("https://thedarkcolour.github.io/KotlinForForge/")
}

dependencies {
    // Provedor Kotlin para NeoForge 1.21.1
    implementation("thedarkcolour:kotlinforforge-neoforge:5.10.0")

    // WorldPanel para 1.21.1
    val worldPanel = implementation("dev.ian.worldpanel:worldpanel-neoforge-1.21.1:0.1.0-SNAPSHOT")

    // Empacotamento Jar-in-Jar (JiJ) para distribuição aos jogadores
    jarJar(worldPanel)
}
```

---

## 📐 Sistema Extensível de Formas e Qualidade de Bordas

WorldPanel padroniza a qualidade visual de todas as formas geométricas 2D, utilizando a mesma precisão do retângulo arredondado de referência (`PanelShape.roundedRectangle`).

- **Junções em Meia-Esquadria (Miter Joins)**: Polígonos (retângulos, triângulos, pentágonos, hexágonos, losangos, trapézios) geram um anel fechado e contínuo de quads com junções de arestas exatas, eliminando cantos abertos ou lacunas.
- **Círculos e Elipses Suaves**: Círculos e elipses são renderizados com 72 subdivisões radiais e bordas concêntricas uniformes.
- **Preenchimento com Inset Preciso**: Quando uma borda é configurada, o preenchimento interno é ajustado para iniciar exatamente onde a borda termina no interior, sem sobreposição nem *z-fighting*.

| Forma | Criação via Factory | Descrição |
| :--- | :--- | :--- |
| **Retângulo** | `PanelShape.rectangle()` | Retângulo reto com bordas em meia-esquadria de 45°. |
| **Quadrado** | `PanelShape.square()` | Quadrado centralizado na menor dimensão. |
| **Cantos Arredondados** | `PanelShape.roundedRectangle(radius)` | Cantos arredondados uniformes ou individuais por canto (`topLeft`, `topRight`, etc.). |
| **Círculo** | `PanelShape.circle()` | Círculo com 72 segmentos radiais e contorno suave. |
| **Elipse** | `PanelShape.ellipse()` | Elipse ajustada à largura e altura total. |
| **Triângulo** | `PanelShape.triangle()`, `equilateralTriangle()`, `rightTriangle()` | Isósceles, equilátero ou retângulo com orientação (`UP`, `DOWN`, `LEFT`, `RIGHT`). |
| **Losango (Diamond)** | `PanelShape.rhombus()` | Losango em diamante conectando centros das arestas. |
| **Trapézio** | `PanelShape.trapezoid(topWidthRatio)` | Trapézio com proporção da base superior ajustável. |
| **Paralelogramo** | `PanelShape.parallelogram(skewRatio)` | Paralelogramo com inclinação ajustável. |
| **Polígono Regular** | `PanelShape.polygon(sides, rotation)` | Pentágono (`sides = 5`), Hexágono (`sides = 6`), Octógono (`sides = 8`), etc. |
| **Silhueta PNG** | `PanelShape.texture("modid:textures/gui/panel.png")` | Forma livre baseada na silhueta alfa de um PNG. |

---

## ➖ Separadores Inteligentes Adaptáveis à Forma (`addSeparator`)

O `.addSeparator(...)` ajusta seu comprimento automaticamente de acordo com o contorno real da forma na altura vertical ($Y$) exata onde está posicionado:

```kotlin
window.addSeparator(
    color = WindowColor.Solid(0xFF808080L),
    thickness = 0.5f,
    margin = 2f,
    includeBorder = false, // Padrão: não atravessa nem sobrepõe a borda da janela
    edgeToEdge = true       // Padrão: vai de um lado ao outro da forma naquela altura Y
)
```

- **Geometria Dinâmica por Altura**: Em formas curvas (círculos e elipses) ou polígonos (triângulos, trapézios), um separador posicionado no centro terá largura máxima, enquanto um separador duas linhas abaixo será automaticamente mais curto, acompanhando a curva da forma e **nunca vazando para fora do painel**.
- **Controle da Borda (`includeBorder`)**:
  - `includeBorder = false` *(padrão)*: A linha para exatamente na face interna da borda, garantindo acabamento limpo.
  - `includeBorder = true`: A linha estende-se até o limite geométrico absoluto do painel.

---

## 🔤 Letreiros 3D e Texto Solto Sem Janela

Se você quer renderizar apenas texto sem nenhuma janela de fundo (por exemplo, letreiros flutuantes em 3D, títulos de chefes ou nomes):

### Opção 1: `FloatingText.render3D` em Uma Linha
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

### Opção 2: `FloatingText.builder()` para Várias Linhas
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

### Opção 3: `FloatingWindow.builder().textOnly()`
Transforma o `FloatingWindow` tradicional em um renderizador transparente de elementos (sem placas, bordas ou sombras).

---

## 🧱 Painéis Planos e Volumétricos 3D (`PanelRenderMode`)

O WorldPanel suporta renderização plana convencional e blocos tridimensionais físicos no mundo:

- `PanelRenderMode.FLAT_2D`: Painel plano clássico.
- `PanelRenderMode.VOLUME_3D`: Painel volumétrico com **frente, laterais e traseira**, criando a aparência de uma placa espessa tridimensional.

```kotlin
val painelVolumetrico = FloatingWindow.builder()
    .setShape(PanelShape.roundedRectangle(cornerRadius = 4f))
    .setRenderMode(PanelRenderMode.VOLUME_3D)
    .setVolume3D(
        thickness = 3.0f,               // Espessura física no eixo Z
        sideColor = null,                // Sombra automática baseada na cor frontal e luz
        sideDarkening = 0.65f,           // Fator de tonalidade das laterais
        lightAngleDegrees = 45f          // Ângulo da fonte de luz para relevo
    )
    .setBackground(WindowColor.Solid(0xFF2C3E50))
    .setBorder(WindowColor.Solid(0xFF1ABC9C), width = 1f)
    .addText("Painel 3D Volumétrico", WindowColor.Solid(0xFFFFFFFF))
    .addSeparator()
    .addText("Laterais com relevo físico")
    .build()
```

---

## 🌈 Cores e Efeitos Visuais (`WindowColor`)

A classe `WindowColor` é agnóstica de Minecraft e oferece diversos efeitos sem alocações contínuas de memória:

* `WindowColor.Solid(argb: Long)`: Cor sólida (aceita `0xAARRGGBB` ou `0xRRGGBB`).
* `WindowColor.Gradient(argbStart, argbEnd, isVertical)`: Gradiente estático linear ou vertical.
* `WindowColor.Fade(colorA, colorB, speed)`: Transição periódica suave entre duas cores.
* `WindowColor.Rainbow(speed, saturation, brightness)`: Efeito arco-íris animado.
* `WindowColor.AnimatedGradient(argbStart, argbEnd, speed, scale)`: Gradiente em movimento contínuo.
* `WindowColor.Pulse(color, minAlpha, speed)`: Pulsação de opacidade/brilho.

---

## 🧪 Testes e Validação

Execute os testes automatizados da biblioteca:

```bash
./gradlew test
```

Execute a compilação completa de todos os módulos:

```bash
./gradlew buildAll
```

Ambos os comandos executam com **100% de sucesso** e zero erros ou avisos de compilação.
