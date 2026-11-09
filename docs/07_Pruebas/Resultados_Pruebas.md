# Resultados verificables de consolidación

Fecha: 2026-11-09, America/Santiago. Rama `codex/integracion-mvp`. Ver [resumen saneado por suite](VALIDACION_CONSOLIDACION_2026-11-09.json).

| Comprobación | Resultado |
|---|---|
| Backend `mvnw.cmd -q package` | 1118 pruebas, 0 fallos, 0 errores, 0 omitidas; compilación y JAR correctos |
| Angular Chrome Headless | 464 SUCCESS, salida 0 |
| Angular `npm run build` | Salida 0; advertencias de presupuestos CSS |
| Whitespace de cambios | Revisado antes de preparar commits |
| Secretos | Indicadores automáticos y revisión manual de configuración/fixtures/ignorados; sin valores reales detectados en candidatos |

Las variables reales se aislaron para Maven; cada suite conservó su configuración H2. Un intento inicial del harness impuso un datasource global e interfirió con dos pruebas de mapeo de entorno: se retiró ese override, sin cambiar la funcionalidad, y se repitió el empaquetado completo con salida 0.

No se enviaron correos reales, se ejecutaron SQL/reparaciones, se escribieron datos de staging ni se reinició su backend durante esta consolidación. Los logs/XML y artefactos generados quedan locales e ignorados; no se copian propiedades ni tokens del entorno a la evidencia publicada.

La confirmación manual de recepción real de mensajes y fotografías aportada por el usuario pertenece a tareas funcionales anteriores y se distingue de estos tests automatizados. El escaneo/programación de NFC físico queda pendiente de despliegue.

No se acredita porcentaje de cobertura, pentest, carga/UAT/CI remoto, equivalencia completa de MySQL ni restauración. La auditoría npm del 6 de noviembre sigue siendo antecedente: aquí no se modificaron dependencias para remediarla ni se declara saneada sin reevaluación.

[Índice vigente](../README.md).
