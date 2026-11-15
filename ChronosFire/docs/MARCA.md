# Revisión de identidad visual

Referencia: [NASA Space Apps Brand Guidelines](https://www.spaceappschallenge.org/brand/) y [PDF del 23/05/2025](https://assets.spaceappschallenge.org/media/documents/Space_Apps_Brand_Guide_-_23_May_2025.pdf), consultados el 14/11/2026.

| Aspecto | Resultado en esta adaptación |
|---|---|
| Paleta | La demo ya usaba los colores de Space Apps. Se conservan. |
| Fondos | Paneles Deep Blue; degradado general Electric Blue → Deep Blue. Se eliminaron las manchas radiales. |
| Amarillo neón | Acentos y controles. Se retiró del fondo de la escala de actividad y de la marca textual del proyecto. |
| Tipografía | Fira Sans para encabezados y Overpass para lectura, alojadas localmente. |
| Logotipo | La cabecera usa el símbolo propio VII.MO (V, órbita y hotspot); el lockup oficial de Space Apps se conserva en el pie como identificación de proyecto participante. |
| Identidad del proyecto | El proyecto se presenta como **VII.MO**, con el punto del nombre siempre en Rocket Red. El logotipo y el símbolo viven en `src/components/BrandMark.vue`, que reproduce la geometría del mockup (`public/Anatomía Órbita-V-html/`). |
| Movimiento | El hotspot del hero orbita lentamente sobre la trayectoria; se sustituye por el punto estático cuando el sistema pide movimiento reducido. |
| Favicon | Símbolo solo (V + órbita + hotspot) sobre Deep Blue. |
| Escala de actividad | Cinco quintiles de la selección con la paleta de marca: `#0A2A66` → `#0960E1` → `#2E96F5` (celeste) → `#EAFE07` → `#E03000`. La leyenda muestra los cortes numéricos de cada tramo. |
| Escala divergente | Cortes fijos en ±15% y ±50% sobre la mediana: verde por debajo, azul profundo en el centro, naranja `#F08A00` y rojo `#E03000` por encima. ▼/▲ y un anillo blanco marcan los extremos, así el dato no depende solo del color. |
| Números críticos | El mes con mayor valor y la desviación del mes seleccionado usan el color de su banda (`#6EE7A8` a `#FF6A3D` sobre fondo oscuro); el resto de cada tarjeta queda neutro. |
| Color de dato | El verde `#1F7A57` / `#8FD9B6` se reserva para «por debajo de la mediana» y no forma parte de la paleta de Space Apps. El rojo de celdas es `#E03000`, apenas más oscuro que el Rocket Red: el `#E43700` puro daba 4,33:1 con texto blanco y no alcanzaba el mínimo de 4,5:1 en texto pequeño. |
| Contraste | Se corrigió el texto azul del encabezado y se calculó luminancia WCAG para elegir texto claro/oscuro en las celdas; todas las examinadas superan 4,5:1. |
| Accesibilidad visual | Las escalas conservan valores numéricos; faltantes y meses parciales tienen etiquetas propias. |

Esta revisión documenta ajustes concretos; no constituye una certificación ni aprobación de NASA.
