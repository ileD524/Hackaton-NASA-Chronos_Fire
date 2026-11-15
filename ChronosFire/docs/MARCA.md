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
| Contraste | Se corrigió el texto azul del encabezado y se calculó luminancia WCAG para elegir texto claro/oscuro en las celdas; todas las examinadas superan 4,5:1. |
| Accesibilidad visual | Las escalas conservan valores numéricos; faltantes y meses parciales tienen etiquetas propias. |

Esta revisión documenta ajustes concretos; no constituye una certificación ni aprobación de NASA.
