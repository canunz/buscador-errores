-- Fecha y hora del último inicio de sesión de cada usuario (NULL = nunca ha ingresado).
ALTER TABLE u_usuario
    ADD COLUMN usuario_ultimo_ingreso DATETIME NULL AFTER usuario_fecha_actualizacion;
