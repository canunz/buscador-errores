# Contratos y validaciones

Referencia 2026-11-09. Se referencian las definiciones fuente para evitar duplicar snapshots de DTO que cambian con el código.

| Área | Fuente y criterios |
|---|---|
| Público | AutoservicioPublicoController.SolucionPublica: id, título, instrucciones y tipo; sin autores/contactos/comentarios internos |
| Material público/privado | MaterialSolucionResponse: título/tipo/URL resuelta, sin referencia física file: |
| Chatbot | ConsultaChatbotRequest (texto obligatorio, máximo 300) y ChatbotResponse; fuentes filtradas y nombre padre no expuesto públicamente |
| Procedimiento | DTO de procedimiento/paso con hardware opcional, datos editoriales y configuración; control administrativo de revisión |
| Ejecución | EjecucionPasoResponse con IDs originales, estado, versión y contrato/configuración capturados |
| Evidencias | UUID de recepción, categorías configuradas y archivos; normalización/formatos/tamaño comprobados en backend |
| Decisión | Versión y decisión explícitas, motivo de rechazo; el formulario nuevo separa rechazo de solicitar nuevamente |
| Gmail | Estado sin secretos y contratos administrativos; idempotencia/confirmación de prueba controlada |

Los controles del formulario no sustituyen Bean Validation ni las reglas de aplicación. No se publican ejemplos con tokens, códigos OAuth, cookies o credenciales. Los fixtures de test utilizan datos sintéticos.

[Índice vigente](../README.md).
