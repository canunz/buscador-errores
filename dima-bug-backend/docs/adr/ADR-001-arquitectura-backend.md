> Referencia histórica conservada. Describe una entrega previa y puede diferir del checkout actual. [Documentación vigente](../../../docs/README.md). Los enlaces a archivos ausentes se identifican expresamente; [original íntegro y hash](../../../docs/auditoria/historico/README.md).

# ADR-001: monolito modular con capas hexagonales

Estado: registro retrospectivo del diseño observado; no implica aprobación
histórica comprobada. Fecha, participantes y alternativas consideradas:
**Requiere validación del equipo**.

## Evidencia y decisión observada

[pom.xml](../../pom.xml) define un único proyecto y
[BackendApplication](../../src/main/java/cl/casol/backend/BackendApplication.java)
un único punto de arranque. El código está organizado por dominio y por
`domain`, `application` e `infrastructure`. Application define puertos de salida
implementados por adapters de Infrastructure.

Se mantiene un monolito organizado modularmente, con separación de modelo,
casos de uso y tecnologías de entrada/salida. El estado concreto de módulos y
flujo se describe en [arquitectura](../arquitectura.md).

## Motivación histórica

**Requiere validación del equipo**. El código demuestra la estructura, pero no
documenta las alternativas evaluadas ni por qué se eligió frente a otros diseños.

## Consecuencias observables

- Un proyecto contiene aplicación, persistencia, seguridad HTTP y pruebas.
- Los puertos permiten sustituir persistencia/seguridad por mocks en los casos de uso.
- Las fronteras no están completamente aisladas: hay dependencias entre modelos
  y contratos, y DTO de Conocimiento reutilizados en Procedimiento.
- Application usa Spring para servicios/transacciones; los controllers llaman
  servicios directamente. No se afirma independencia total del framework ni
  cumplimiento automático de límites por herramientas de arquitectura.
