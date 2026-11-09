> **Antecedente de auditoría fechado.** Sus conteos/hallazgos describen la fase original. Para estado vigente y cierres específicos consultar [documentación actual](../README.md).

# Referencias históricas y procedencia

Los documentos anteriores se conservan como antecedentes fechados. Una referencia histórica a un ensayo o archivo ausente no acredita que la funcionalidad exista en este checkout, ni demuestra que el ensayo nunca ocurrió en otro árbol.

## Archivos no disponibles

Estos enlaces se sustituyeron por una referencia explícita a este registro. No se crearon scripts, workflows, IT o configuración ficticios para hacer que sus destinos existan. El contenido original completo está preservado en el histórico. El README backend ahora existe y proporciona entrada a las guías vigentes.

| Documento histórico | Etiqueta original | Destino original no disponible |
|---|---|---|
| `dima-bug-backend/docs/arquitectura.md` | README | `../README.md#pendientes-técnicos-detectados` |
| `dima-bug-backend/docs/auditoria-cobertura-riesgos-skill4.md` | Tests MySQL | `../src/test/java/cl/casol/backend/integration/` |
| `dima-bug-backend/docs/auditoria-cobertura-riesgos-skill4.md` | configuración funcional | `../src/main/resources/application.properties` |
| `dima-bug-backend/docs/auditoria-cobertura-riesgos-skill4.md` | configuración aislada | `../src/test/resources/integration-mysql.properties` |
| `dima-bug-backend/docs/auditoria-cobertura-riesgos-skill4.md` | esquema de tests | `../src/test/resources/db/dimabug-esquema.sql` |
| `dima-bug-backend/docs/ci-pruebas-backend.md` | workflow | `../.github/workflows/backend-tests.yml` |
| `dima-bug-backend/docs/ci-pruebas-backend.md` | `MySqlIntegrationSupport` | `../src/test/java/cl/casol/backend/integration/MySqlIntegrationSupport.java` |
| `dima-bug-backend/docs/configuracion-dev.md` | application.properties | `../src/main/resources/application.properties` |
| `dima-bug-backend/docs/configuracion-dev.md` | README | `../README.md#pruebas` |
| `dima-bug-backend/docs/configuracion-dev.md` | pendientes técnicos | `../README.md#pendientes-técnicos-detectados` |
| `dima-bug-backend/docs/decision-historial-procedimientos.md` | V20261009_01__snapshot_pasos_ejecucion.sql | `../src/main/resources/db/migration/V20261009_01__snapshot_pasos_ejecucion.sql` |
| `dima-bug-backend/docs/decision-riesgos-residuales-skill4.md` | MaterialesArchivoIT | `../src/test/java/cl/casol/backend/integration/MaterialesArchivoIT.java` |
| `dima-bug-backend/docs/handoff-skill4-a-skill5.md` | application.properties | `../src/main/resources/application.properties` |
| `dima-bug-backend/docs/handoff-skill4-a-skill5.md` | V20261008_01 | `../src/main/resources/db/migration/V20261008_01__agregar_fk_usuarios_autoria_seguimiento.sql` |
| `dima-bug-backend/docs/handoff-skill4-a-skill5.md` | diagnóstico | `../src/main/resources/db/diagnostico/D20261008_01__usuarios_referencias_huerfanas.sql` |
| `dima-bug-backend/docs/handoff-skill4-a-skill5.md` | V20261009_01 | `../src/main/resources/db/migration/V20261009_01__snapshot_pasos_ejecucion.sql` |
| `dima-bug-backend/docs/handoff-skill4-a-skill5.md` | backend-tests.yml | `../.github/workflows/backend-tests.yml` |
| `dima-bug-backend/docs/integracion-mysql.md` | diagnóstico | `../src/main/resources/db/diagnostico/D20261008_01__usuarios_referencias_huerfanas.sql` |
| `dima-bug-backend/docs/integracion-mysql.md` | migración | `../src/main/resources/db/migration/V20261008_01__agregar_fk_usuarios_autoria_seguimiento.sql` |
| `dima-bug-backend/docs/materiales-archivo.md` | pendientes generales | `../README.md#pendientes-técnicos-detectados` |
| `dima-bug-backend/docs/migracion-fk-usuarios.md` | Diagnóstico SELECT | `../src/main/resources/db/diagnostico/D20261008_01__usuarios_referencias_huerfanas.sql` |
| `dima-bug-backend/docs/migracion-fk-usuarios.md` | Migración | `../src/main/resources/db/migration/V20261008_01__agregar_fk_usuarios_autoria_seguimiento.sql` |

Los resultados de integración Testcontainers/CI/snapshots siguen pendientes de conciliación. La suite disponible usa mocks/MVC/H2; las referencias sustituidas no cambian ese límite. Ver [auditoría original](AUDITORIA_DOCUMENTAL.md) y [matriz vigente](MATRIZ_TRAZABILIDAD.md).
