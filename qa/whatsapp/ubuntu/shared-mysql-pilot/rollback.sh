#!/bin/sh
# Contencion solo QA. Conserva esquema, credenciales y artefacto; no toca DNS/Nginx/Certbot.
set -eu
[ "${1:-}" = "DETENER_Y_BLOQUEAR_SOLO_QA" ] || exit 2
[ "$(id -u)" = 0 ] || exit 2
systemctl stop dimabug-wa-pilot.service dimabug-wa-pilot-graph.service
rm -f -- /etc/dimabug-wa-pilot/PILOT_APPROVED
mysql --protocol=socket --batch --skip-column-names <<'SQL'
ALTER USER 'wa_qa_pilot'@'127.0.0.1' ACCOUNT LOCK;
SQL
echo 'QA detenido y cuenta QA bloqueada; MySQL y aplicaciones existentes sin reiniciar'
