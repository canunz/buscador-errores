# Autenticación y autorización

JWT sigue siendo el mecanismo de identidad de DimaBug y la política de sesión es STATELESS. La API valida usuario activo/rol y los casos de uso comprueban permisos y pertenencia. La URL o un control oculto no otorgan permisos.

El portal de autoservicio utiliza una allowlist pública explícita. Angular no envía JWT a esas consultas y no carga navegación administrativa. Los endpoints privados, estados de soluciones, materiales administrativos, ejecución y correo siguen protegidos.

OAuth Gmail es una autorización de transporte para una cuenta corporativa, no un reemplazo del login. El administrador autenticado inicia la vinculación; el callback público solo completa un estado/cookie/PKCE válido y de un uso. Scope fijo gmail.send; no lectura de buzón, contactos o login openid/profile.

Access/refresh tokens se almacenan cifrados, se renuevan en backend y no llegan a Angular. La revocación protege intentos antiguos. La configuración y autorización de cuenta no habilitan automáticamente envíos reales.

[Índice vigente](../README.md).
