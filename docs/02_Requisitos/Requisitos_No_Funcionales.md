> Referencia de la fase documental inicial. No equivale a aceptación formal ni a una lista exhaustiva de funciones actuales. Véase [estado consolidado](../README.md) y los contratos fuente.

# Requisitos no funcionales y evidencia

Estado documental: actualizado desde el código auditado el 2026-11-06. Los resultados de pruebas citados corresponden a esa auditoría.


No se recibieron umbrales o requisitos no funcionales aprobados. Esta tabla diferencia controles observados de objetivos pendientes; no inventa SLA, tiempos máximos, porcentajes de cobertura ni niveles de accesibilidad.

| ID documental | Tema | Evidencia actual | Criterio formal pendiente |
|---|---|---|---|
| RNF-01 | Acceso y confidencialidad | JWT/BCrypt, roles, propietario, validaciones | Política de sesión, privacidad, rate limiting y pruebas integrales |
| RNF-02 | Integridad | Transacciones, versiones y bloqueos parciales, seis FK en SQL Tickets | DDL físico, carreras prioritarias y escrituras externas |
| RNF-03 | Recuperación | Compensación de archivos y guías de respaldo propuestas | RPO/RTO y restauración BD/uploads comprobada |
| RNF-04 | Rendimiento | Cachés, filtros y consultas parametrizadas | Carga, concurrencia, latencia y uso de recursos acordados |
| RNF-05 | Mantenibilidad | Puertos/adaptadores, pruebas, ArchUnit Tickets | Límites globales y criterios de revisión/CI |
| RNF-06 | Compatibilidad | Versiones de lock/POM, compilación y Chrome local | Matriz de navegadores, SO y versión MySQL objetivo |
| RNF-07 | Usabilidad/accesibilidad | Formularios y guards con pruebas locales | UAT, móvil, teclado, zoom y estándar objetivo |
| RNF-08 | Cadena de suministro | Lockfile y consulta npm con avisos | Evaluación y corrección de dependencias; SCA Maven |
| RNF-09 | Operación | Perfil staging y configuraciones externas previstas | TLS, monitoreo, logs seguros y responsables |

Todos los criterios formales no entregados quedan [PENDIENTE DE VERIFICACIÓN]. Una prueba H2 no cierra compatibilidad MySQL; una guía de respaldo no cierra recuperación.


## Referencias

[Índice vigente](../README.md) · [Auditoría de origen](../auditoria/AUDITORIA_DOCUMENTAL.md)
