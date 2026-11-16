# Diagramas vigentes

[Draw.io editable](DimaBug_Modelo_Relacional_v2.drawio), [PNG](DimaBug_Modelo_Relacional_v2.png) y [SVG](DimaBug_Modelo_Relacional_v2.svg).

La primera página inventaría las 31 tablas y resume atributos; no dibuja todas las conexiones para evitar cruces ilegibles. Las otras cinco páginas muestran todas las columnas de cada módulo y conectores con referencias externas. La cardinalidad exacta y su origen se consultan en [Modelo_Relacional](../../04_Base_Datos/Modelo_Relacional.md). Los conectores 1:N describen agrupación lógica; la proyección JDBC espera 0..1 sin unicidad física acreditada. ? indica nulabilidad JPA y ND declaración física no disponible.

No se certifica esquema MySQL. Los archivos antiguos de entregables se conservan. La vista global PNG/SVG es una imagen estática real generada desde el inventario, no una captura de la aplicación ni de MySQL Workbench. El diagrama general de aplicación se mantiene como Mermaid editable en Arquitectura_General.
