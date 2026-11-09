# Controles verificados y límites

IMPLEMENTADO: JWT/roles backend; comprobación de pertenencia de ejecuciones/adjuntos; autorización individual de materiales; DTO públicos mínimos; validación de entradas/archivos/URL; state/PKCE/cifrado OAuth; idempotencia de comunicaciones y separación SIMULADO/REAL.

VALIDADO: pruebas HTTP/JPA y unitarias de acceso anónimo, ID privado/inactivo, padre borrador, retirada de permisos, descarga privada, mutaciones sin JWT, rol insuficiente, duplicados, límites y payloads públicos excesivos. No se efectuó un pentest externo.

Límite público actual: 10 consultas chatbot/minuto por dirección, 60 lecturas/minuto por dirección, 600 peticiones/minuto por instancia y almacenamiento de ventanas acotado. No confía en X-Forwarded-For arbitrario. Máximo cuerpo POST público 8 KiB también sin Content-Length; respuesta 429/Retry-After o 413 según corresponda.

PENDIENTE: políticas de despliegue/HTTPS, revisión editorial, control de tráfico centralizado para múltiples instancias/proxies, gestión operativa/rotación de secretos, UAT y restauración. Los hallazgos de auditorías fechadas no se consideran cerrados en bloque. La auditoría de dependencias del 6 de noviembre es un antecedente, no una revisión actual ni una corrección de paquetes.

[Índice vigente](../README.md).
