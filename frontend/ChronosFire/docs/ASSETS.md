# Recursos de la demo local

- `public/logos/space-apps-lockup.svg` y `space-apps-motif.svg`: archivos existentes en la rama Nati, conservados sin modificación. Identidad de NASA Space Apps; ver su [guía](https://www.spaceappschallenge.org/brand/).
- `public/fonts/FiraSans-*.ttf`: [Google Fonts / Fira Sans](https://github.com/google/fonts/tree/main/ofl/firasans), licencia SIL Open Font License, copia incluida como `OFL-FiraSans.txt`.
- `public/fonts/Overpass.ttf`: [Google Fonts / Overpass](https://github.com/google/fonts/tree/main/ofl/overpass), licencia SIL Open Font License, copia incluida como `OFL-Overpass.txt`.
- `public/maps/south-america.geojson`: países de Sudamérica extraídos de [Natural Earth, Admin 0, 110 m](https://github.com/nvkelso/natural-earth-vector/blob/master/geojson/ne_110m_admin_0_countries.geojson), cartografía de dominio público. Es un fondo generalizado, no una fuente de límites para los filtros ni una declaración de soberanía.
- `public/favicon.svg`: símbolo de la identidad VII.MO (V, órbita y hotspot) sobre Deep Blue.
- `public/Anatomía Órbita-V-html/`: mockup de referencia de la identidad VII.MO (herramienta de diseño visual exportada como página autónoma). Los valores de geometría, color y tipografía se replican en `src/components/BrandMark.vue` y en los tokens de `src/styles/main.css`; el runtime del export (`support.js`, `vendor/`) no se usa en la aplicación.
- Datos: NASA FIRMS, MODIS Terra/Aqua y VIIRS Suomi-NPP. Los archivos científicos permanecen fuera del repositorio.
