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
| Escala de actividad | Cinco quintiles de la selección con rampa **Viridis** (`#440154` → `#414487` → `#2A788E` → `#22A884` → `#FDE725`), elegida para datos por ser perceptualmente uniforme y apta para daltonismo. La paleta de marca queda para la interfaz. La leyenda muestra los cortes numéricos de cada tramo. |
| Escala divergente | Cortes fijos en ±15% y ±50% sobre la mediana con rampa **PuOr**: púrpura `#542788` / `#B2ABD2` por debajo, azul profundo `#0B2A6B` en el centro y naranja `#FDB863` / `#B35806` por encima. ▼/▲ y un anillo blanco marcan los extremos, así el dato no depende solo del color. |
| Mapa | Doble codificación: el color indica la intensidad de hotspots (quintiles de las celdas mostradas) y el tamaño, los días con actividad. |
| Números críticos | El mes con mayor valor y la desviación del mes seleccionado usan el tono claro de su banda (`#D8CCF0` a `#FF9E64` sobre fondo oscuro); el resto de cada tarjeta queda neutro. |
| Color de dato | La rampa de anomalía ya no usa verde/rojo: púrpura y naranja se distinguen en simulaciones de protanopia y deuteranopia (ΔE > 90 entre extremos; > 31 entre bandas contiguas). Los cinco tramos de Viridis también superan ΔE 17 en esas simulaciones. |
| Contraste | Se calculó luminancia WCAG para elegir texto claro/oscuro en cada celda y en cada tramo de leyenda; todas las combinaciones examinadas superan 4,5:1. |
| Accesibilidad visual | Las escalas conservan valores numéricos; faltantes y meses parciales tienen etiquetas propias. |

Esta revisión documenta ajustes concretos; no constituye una certificación ni aprobación de NASA.
