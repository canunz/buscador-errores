"""Genera (NO ejecuta) el SQL de una base QA completa para la autoatención WhatsApp.

La base resultante contiene, en un solo esquema wa_durable_<hex> (el único nombre que admite
WhatsAppDurablePerfilGuard):
  1. Esquema DimaBug QA (qa/mysql/schema/20_schema_final.sql) + fila estructural (25_structural_rows.sql).
  2. Migraciones posteriores al esquema QA: V20261115 (registro_estado) y V20261116 (datos del solicitante).
  3. Tablas WhatsApp durables (qa/whatsapp/mysql-stage22a/schema.sql).
  4. Mínimo de fixtures mvp-qa-v2: los 4 roles y soporte@qa.dimabug.test (rol SOPORTE), solicitante de tickets.
     Su hash es el marcador '@RUNTIME_BCRYPT:soporte' del fixture: no es BCrypt, por lo que no permite ingresar.
  5. Conocimiento y solución del POS (qa/whatsapp/autoatencion-datos-qa.sql).

Uso:  python qa/whatsapp/build_autoatencion_base_qa.py [--database wa_durable_<hex>]
Salida: qa/runtime/autoatencion/<base>.sql (ignorado por Git). Revisar antes de aplicar con:
      mysql --host 127.0.0.1 --port <puerto QA> -u <admin QA> -p < qa/runtime/autoatencion/<base>.sql
No usar --force: el primer error debe detener la aplicación.
"""
import argparse, json, re, secrets, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FUENTES = {
    "esquema": ROOT / "qa/mysql/schema/20_schema_final.sql",
    "estructural": ROOT / "qa/mysql/schema/25_structural_rows.sql",
    "registro": ROOT / "dima-bug-backend/src/main/resources/db/migration/V20261115_01__registro_universal.sql",
    "solicitante": ROOT / "dima-bug-backend/src/main/resources/db/migration/V20261116_01__ticket_datos_solicitante.sql",
    "whatsapp": ROOT / "qa/whatsapp/mysql-stage22a/schema.sql",
    "fixtures": ROOT / "qa/fixtures/mvp-v2.json",
    "datos": ROOT / "qa/whatsapp/autoatencion-datos-qa.sql",
}
NOMBRE = re.compile(r"wa_durable_[a-f0-9]{8,32}")
ACTOR = "soporte@qa.dimabug.test"


def sin_use(texto: str) -> str:
    """Quita el USE de la base original: todo se aplica sobre la base nueva."""
    lineas = [l for l in texto.splitlines() if not re.match(r"\s*USE\s+`?dima_bug_qa_mvp`?\s*;", l, re.I)]
    resto = "\n".join(lineas)
    if "dima_bug_qa_mvp" in resto:
        raise SystemExit("La fuente aún referencia dima_bug_qa_mvp: revisar antes de generar.")
    return resto


def literal(valor) -> str:
    if valor is None:
        return "NULL"
    if isinstance(valor, bool):
        return "1" if valor else "0"
    if isinstance(valor, (int, float)):
        return str(valor)
    return "'" + str(valor).replace("\\", "\\\\").replace("'", "''") + "'"


def insert(tabla: str, filas: list) -> str:
    columnas = list(filas[0].keys())
    valores = ",\n ".join("(" + ",".join(literal(f[c]) for c in columnas) + ")" for f in filas)
    return f"INSERT INTO {tabla} ({','.join(columnas)}) VALUES\n {valores};"


def fixtures_minimos() -> str:
    datos = json.loads(FUENTES["fixtures"].read_text(encoding="utf-8"))
    if datos.get("version") != "mvp-qa-v2":
        raise SystemExit("Se esperaba el fixture mvp-qa-v2.")
    roles = datos["rows"]["u_rol"]
    actor = [u for u in datos["rows"]["u_usuario"] if u["usuario_email"] == ACTOR]
    if len(actor) != 1 or not any(r["rol_id"] == actor[0]["rol_id"] and r["rol_nombre"] == "SOPORTE" for r in roles):
        raise SystemExit("El fixture no contiene un único soporte@qa.dimabug.test con rol SOPORTE.")
    if not str(actor[0]["usuario_password_hash"]).startswith("@RUNTIME_BCRYPT:"):
        raise SystemExit("El hash del actor no es el marcador esperado: no se copian credenciales reales.")
    return insert("u_rol", roles) + "\n" + insert("u_usuario", actor)


def generar(base: str) -> str:
    for nombre, ruta in FUENTES.items():
        if not ruta.is_file():
            raise SystemExit(f"Falta la fuente {nombre}: {ruta}")
    partes = [
        f"-- Generado por qa/whatsapp/build_autoatencion_base_qa.py. Base QA nueva: {base}.",
        "-- Aplicar una sola vez sobre la instancia MySQL QA local, sin --force.",
        "SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;",
        "-- Prerrequisito visible: debe ser 0. Si la base ya existe, CREATE DATABASE falla y nada se reutiliza.",
        f"SELECT COUNT(*) AS base_ya_existe FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '{base}';",
        f"CREATE DATABASE `{base}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;",
        f"USE `{base}`;",
        "-- 1. Esquema DimaBug QA",
        sin_use(FUENTES["esquema"].read_text(encoding="utf-8")),
        sin_use(FUENTES["estructural"].read_text(encoding="utf-8")),
        "-- 2. Migraciones posteriores al esquema QA",
        FUENTES["registro"].read_text(encoding="utf-8"),
        FUENTES["solicitante"].read_text(encoding="utf-8"),
        "-- 3. Tablas WhatsApp durables",
        FUENTES["whatsapp"].read_text(encoding="utf-8"),
        "-- 4. Fixtures mínimos: roles y solicitante de tickets (sin credenciales utilizables)",
        fixtures_minimos(),
        "-- 5. Conocimiento y solución del POS",
        FUENTES["datos"].read_text(encoding="utf-8"),
        "-- 6. Resumen: tablas DimaBug + 3 tablas wa_* en la misma base",
        "SELECT COUNT(*) AS tablas_totales, SUM(TABLE_NAME LIKE 'wa\\\\_%') AS tablas_whatsapp "
        "FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE();",
    ]
    return "\n\n".join(p.strip() for p in partes) + "\n"


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--database", help="Nombre wa_durable_<hex> (8 a 32 hex). Por defecto se genera uno nuevo.")
    a = p.parse_args()
    base = a.database or "wa_durable_" + secrets.token_hex(6)
    if not NOMBRE.fullmatch(base):
        raise SystemExit("El nombre debe ser wa_durable_<hex> (8 a 32 caracteres hexadecimales en minúscula).")
    destino = ROOT / "qa/runtime/autoatencion" / f"{base}.sql"
    destino.parent.mkdir(parents=True, exist_ok=True)
    if destino.exists():
        raise SystemExit(f"Ya existe {destino}: no se sobrescribe.")
    destino.write_text(generar(base), encoding="utf-8", newline="\n")
    print(json.dumps({"estado": "SQL_GENERADO_NO_EJECUTADO", "base": base, "archivo": str(destino.relative_to(ROOT)),
                      "url_jdbc": f"jdbc:mysql://127.0.0.1:<puerto>/{base}?connectionTimeZone=UTC",
                      "ticket_actor_email": ACTOR}, ensure_ascii=False))


if __name__ == "__main__":
    main()
