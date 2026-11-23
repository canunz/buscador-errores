#!/bin/sh
# Preparado, NO ejecutado. Solo unidades nuevas; no start/enable y no MySQL/Nginx reload.
set -eu
[ "${1:-}" = "INSTALAR_SOLO_PILOTO_QA" ] || { echo 'Autorizacion requerida'; exit 2; }
[ "$(id -u)" = 0 ] || exit 2
cd "$(dirname "$0")"
[ -f /etc/dimabug-wa-pilot/application.properties ] || { echo 'Falta provision validada'; exit 2; }
[ -f /etc/dimabug-wa-pilot/tls-public.json ] || { echo 'Falta validacion CA+pin con cuenta bloqueada'; exit 2; }
[ ! -e /opt/dimabug-wa-pilot ] || { echo 'Destino existente; no se sobrescribe'; exit 2; }
[ ! -e /etc/systemd/system/dimabug-wa-pilot.service ] || exit 2
[ ! -e /etc/systemd/system/dimabug-wa-pilot-graph.service ] || exit 2
! getent passwd dimabug-wa-pilot >/dev/null || exit 2
! getent passwd dimabug-wa-pilot-graph >/dev/null || exit 2
useradd --system --user-group --home-dir /var/lib/dimabug-wa-pilot --no-create-home --shell /usr/sbin/nologin dimabug-wa-pilot
useradd --system --user-group --home-dir /nonexistent --no-create-home --shell /usr/sbin/nologin dimabug-wa-pilot-graph
install -d -m 0755 /opt/dimabug-wa-pilot /opt/dimabug-wa-pilot/proxy
install -m 0644 whatsapp-qa.jar /opt/dimabug-wa-pilot/whatsapp-qa.jar
install -m 0644 proxy/*.class /opt/dimabug-wa-pilot/proxy/
install -m 0644 /etc/dimabug-wa-pilot/dimabug-wa-pilot.service /etc/systemd/system/
install -m 0644 dimabug-wa-pilot-graph.service /etc/systemd/system/
systemd-analyze verify /etc/systemd/system/dimabug-wa-pilot.service /etc/systemd/system/dimabug-wa-pilot-graph.service
systemctl daemon-reload
echo 'INSTALADO; unidades NO iniciadas y NO habilitadas al arranque'
