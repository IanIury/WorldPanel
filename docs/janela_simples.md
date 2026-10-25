# Guia: Como Criar uma Janela Simples no WorldPanel

Este guia ensina como criar, configurar e renderizar uma janela flutuante no mundo 3D do Minecraft usando a biblioteca **WorldPanel**.

---

## 1. Visão Geral

O WorldPanel utiliza o padrão **Builder** (`FloatingWindow.builder()`) para construir janelas personalizadas. Você pode definir a forma geométrica, dimensões, cores, bordas, orientação e os elementos internos (textos, ícones de itens, separadores, etc.).

A janela construída é renderizada chamando o método `.render(poseStack, buffer, packedLight)` no seu evento ou estágio de renderização desejado (por exemplo, dentro de um `EntityRenderer` ou `RenderLevelStageEvent`).

---

## 2. Exemplo Básico Passo a Passo

```kotlin
import dev.ian.worldpanel.FloatingWindow
import dev.ian.worldpanel.WindowAlignment
import dev.ian.worldpanel.WindowColor
import dev.ian.worldpanel.WindowElement
import dev.ian.worldpanel.shape.PanelShape
import net.minecraft.network.chat.Component

fun renderMinhaJanela(
    poseStack: PoseStack,
    buffer: MultiBufferSource,
    packedLight: Int
) {
    val window = FloatingWindow.builder()
        // 1. Posição e orientação
        .setFaceCamera(true)                  // Gira automaticamente para olhar o jogador
        .setOffset(0.0, 2.2, 0.0)             // 2.2 blocos acima do ponto de origem
        .setScale(0.025f)                     // Escala visual da janela no mundo

        // 2. Formato e aparência da janela
        .setShape(PanelShape.roundedRectangle(4f)) // Retângulo com cantos arredondados
        .setSize(width = 80f, height = 50f, autoScaleContent = true)
        .setAlignment(WindowAlignment.CENTER) // Centraliza a janela em relação ao ponto de offset
        .setPadding(4f)                       // Espaçamento interno entre a borda e o conteúdo
        .setBackground(WindowColor.Solid(0xDD181C2A)) // Fundo escuro semi-transparente
        .setBorder(WindowColor.Solid(0xFF38BDF8), width = 0.75f) // Borda azul clara
        .setShadow(WindowColor.Solid(0x88000000), offset = 1.5f) // Sombra suave projetada

        // 3. Adicionando conteúdo sequencial
        .addText(
            text = "Status da Entidade",
            color = WindowColor.Solid(0xFF38BDF8),
            shadow = true,
            textScale = 1.1f,
            alignment = WindowElement.Text.TextAlignment.CENTER
        )
        // Separador elegante respeitando as bordas da forma
        .addSeparator(
            color = WindowColor.Solid(0xFF475569),
            thickness = 0.75f,
            margin = 2f,
            includeBorder = false // Padrão: vai de um lado ao outro parando antes da borda
        )
        .addText(
            text = "HP: 100 / 100",
            color = WindowColor.Solid(0xFF4ADE80),
            shadow = true,
            textScale = 0.9f
        )
        .addText(
            text = "Nível: 5",
            color = WindowColor.Solid(0xFFFACC15),
            shadow = true,
            textScale = 0.9f
        )
        .build()

    // 4. Renderiza na cena
    window.render(poseStack, buffer, packedLight)
}
```

---

## 3. Entendendo os Parâmetros Principais

### Formato e Geometria (`.setShape(...)`)
Você pode escolher entre várias formas geométricas:
- `PanelShape.roundedRectangle(cornerRadius)`: Retângulo com cantos arredondados uniformes.
- `PanelShape.circle()`: Círculo perfeito com 72 segmentos radiais.
- `PanelShape.rectangle()`: Retângulo reto clássico.
- `PanelShape.polygon(sides = 6)`: Polígono regular (ex: hexágono).
- `PanelShape.triangle(...)`, `PanelShape.rhombus()`, etc.

### Tamanho e Ajuste Automático (`.setSize(...)`)
```kotlin
.setSize(
    width = 30f, 
    height = 30f, 
    autoScaleContent = true, // Se o conteúdo for maior que o painel, reduz proporcionalmente
    autoFitWidth = false,    // Reduz a largura se o conteúdo for menor
    autoFitHeight = false    // Reduz a altura se o conteúdo for menor
)
```

### Ajuste do Conteúdo à Forma (círculo, triângulo, losango, trapézio, paralelogramo, polígonos)
Para formas **não retangulares** o conteúdo não é mais encaixado em um único retângulo conservador. Cada linha (texto, ícone, item, separador) é testada contra o **contorno real da forma na altura em que ela está** (já descontando `padding` e a largura da borda). Resultado:

- **Triângulo / losango / trapézio:** linhas curtas sobem para a parte estreita e linhas largas descem para a parte larga, aproveitando o espaço interno.
- **Paralelogramo:** as linhas acompanham a inclinação das laterais e não saem mais da janela, mesmo com o texto de baixo maior que o de cima.
- **Círculo / elipse / polígonos regulares:** o conteúdo usa a corda real do círculo em cada altura.
- **Texto maior:** em janelas com tamanho fixo (`.setSize(...)` com `autoScaleContent = true`), o conteúdo é **ampliado** até preencher a forma (limite padrão: 2x). Palavras longas só reduzem o texto quando realmente não cabem.
- **Sem `setSize`:** o painel cresce automaticamente ao redor do conteúdo até que tudo caiba dentro do contorno.

```kotlin
FloatingWindow.builder()
    .setShape(PanelShape.triangle())
    .setPadding(3f)
    .setSize(width = 120f, height = 70f)     // autoScaleContent = true (padrão)
    .setMaxContentScale(3f)                  // opcional: permite ampliar até 3x (padrão 2x; use 1f para só reduzir)
    .addText("Original damage:")
    .addText("0.84799993")
    .build()
```

> [!NOTE]
> Retângulo, quadrado e retângulo arredondado continuam com o layout clássico (caixa de conteúdo), sem ampliação automática.

### O Separador (`.addSeparator(...)`)
O separador divide seções da janela. Ele calcula automaticamente o comprimento da corda horizontal da forma em qualquer altura (mesmo em círculos ou polígonos, nunca ultrapassa o limite da forma).

| Parâmetro | Padrão | Descrição |
| :--- | :--- | :--- |
| `color` | `Solid(0xFF808080)` | Cor sólida ou animada do traço. |
| `thickness` | `1f` | Espessura da linha em pixels. |
| `margin` | `2f` | Margem vertical (espaço acima e abaixo da linha). |
| `includeBorder` | `false` | **Novo**: Quando `false` (padrão), a linha para exatamente na face interna da borda. Quando `true`, atravessa até a aresta externa. |
| `edgeToEdge` | `true` | Quando `true`, estende-se de uma extremidade à outra da forma no nível $Y$. Quando `false`, limita-se à área útil interna do padding. |

### Cores Animadas
Você pode usar cores animadas dinâmicas em qualquer texto, borda ou fundo:
- `WindowColor.Gradient(0xFF67E8F9, 0xFF8B5CF6)`: Gradiente estático suave.
- `WindowColor.AnimatedGradient(0xFF67E8F9, 0xFF8B5CF6, speed = 1.2f, scale = 0.8f)`: Gradiente que desliza continuamente.
- `WindowColor.Rainbow(speed = 1.0f)`: Arco-íris contínuo.
- `WindowColor.Pulse(0xFF9BE7FF, speed = 1.5f)`: Pulsação suave de brilho.

---

## 4. Onde Chamar o `.render(...)` no Mod

### Em um `EntityRenderer` ou `RenderLayer`:
```kotlin
class MeuMobRenderer(context: EntityRendererProvider.Context) : MobRenderer<MeuMob, MeuModel>(context, ...) {
    override fun render(
        entity: MeuMob,
        entityYaw: Float,
        partialTicks: Float,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int
    ) {
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight)

        // Cria e renderiza a janela flutuante acima do mob
        FloatingWindow.builder()
            .setFaceCamera(true)
            .setOffset(0.0, entity.bbHeight + 0.5, 0.0)
            .addText(entity.displayName, color = 0xFFFFFFFF.toInt(), shadow = true)
            .build()
            .render(poseStack, buffer, packedLight)
    }
}
```
