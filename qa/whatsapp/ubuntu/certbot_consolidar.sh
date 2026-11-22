#!/bin/bash
# Consolidación de Certbot (Skill 4.5): un solo renovador (Snap) para avanza, buscadorerrores y staging.dimabug.
# Basado en docs/implementacion/WhatsApp_Certbot_relevo_reversible.md. No desinstala APT, no borra lineages,
# no reinicia Nginx ni emite certificados. Cada fase es explícita:
#   sudo bash certbot_consolidar.sh inventario          # solo lectura
#   sudo bash certbot_consolidar.sh dry-run             # pausa timer APT y prueba renovación simulada (ACME staging)
#   sudo bash certbot_consolidar.sh consolidar          # deshabilita timer APT si los 3 dry-run pasaron
#   sudo bash certbot_consolidar.sh revertir            # vuelve al baseline: certbot.timer habilitado y activo
set -euo pipefail
[[ $(id -u) == 0 ]] || { echo "Ejecutar con sudo"; exit 2; }
LINEAGES=(avanza.casol.cl buscadorerrores.casol.cl staging.dimabug.casol.cl)
SNAP=/snap/bin/certbot
EVID=/root/certbot-consolidacion; mkdir -p "$EVID"; chmod 700 "$EVID"
MARCA="$EVID/dry-run-aprobado"

ocupado() {  # No interferir con una renovación en curso.
  for s in certbot.service snap.certbot.renew.service; do
    [[ $(systemctl show -p ActiveState --value "$s" 2>/dev/null) == activ* ]] && return 0
  done
  pgrep -x certbot >/dev/null 2>&1
}

inventario() {
  echo "== Timers y servicios"
  systemctl show -p Id -p ActiveState -p UnitFileState -- certbot.timer snap.certbot.renew.timer 2>/dev/null
  systemctl list-timers --all --no-pager certbot.timer snap.certbot.renew.timer 2>/dev/null || true
  echo "== Versiones"; "$SNAP" --version 2>&1 || echo "Snap certbot ausente"; /usr/bin/certbot --version 2>&1 || true
  echo "== Certificados (vigencia, sin claves)"
  "$SNAP" certificates 2>/dev/null | grep -E "Certificate Name|Domains|Expiry Date" || true
  echo "== Cron y renovación en curso"
  ls -1 /etc/cron.d/ 2>/dev/null | grep -i certbot || echo "sin cron certbot en /etc/cron.d"
  ocupado && echo "ATENCION: renovación en curso" || echo "sin renovación en curso"
}

case "${1:-}" in
  inventario) inventario | tee "$EVID/inventario-$(date -u +%Y%m%dT%H%M%SZ).txt" ;;
  dry-run)
    [[ -x $SNAP ]] || { echo "NO-GO: falta $SNAP"; exit 1; }
    [[ $(systemctl is-active snap.certbot.renew.timer) == active ]] || { echo "NO-GO: timer Snap inactivo"; exit 1; }
    ocupado && { echo "NO-GO: renovación en curso; reintentar más tarde"; exit 1; }
    echo "Respaldo privado de /etc/letsencrypt y Nginx"
    tar --preserve-permissions -czf "$EVID/respaldo-$(date -u +%Y%m%dT%H%M%SZ).tgz" /etc/letsencrypt /etc/nginx
    systemctl stop certbot.timer   # Pausa temporal; sigue habilitado (enable intacto).
    rm -f "$MARCA"; ok=1
    for c in "${LINEAGES[@]}"; do
      if "$SNAP" renew --cert-name "$c" --dry-run >"$EVID/dry-run-$c.log" 2>&1; then echo "PASS $c"; else echo "FAIL $c"; ok=0; fi
    done
    nginx -t 2>&1 | tail -1
    if [[ $ok == 1 ]]; then date -u > "$MARCA"; echo "GO: ejecutar 'consolidar'"; else
      systemctl start certbot.timer; echo "NO-GO: timer APT reactivado; revisar $EVID/dry-run-*.log"; exit 1; fi ;;
  consolidar)
    [[ -f $MARCA ]] || { echo "NO-GO: primero 'dry-run' aprobado"; exit 1; }
    systemctl disable --now certbot.timer
    echo "certbot.timer: $(systemctl is-enabled certbot.timer 2>&1) / $(systemctl is-active certbot.timer)"
    echo "snap.certbot.renew.timer: $(systemctl is-enabled snap.certbot.renew.timer) / $(systemctl is-active snap.certbot.renew.timer)"
    systemctl list-timers --no-pager snap.certbot.renew.timer
    [[ $(systemctl is-active snap.certbot.renew.timer) == active ]] && echo "GO: renovador único = Snap" || { systemctl enable --now certbot.timer; echo "NO-GO: Snap inactivo, APT restaurado"; exit 1; } ;;
  revertir) systemctl enable --now certbot.timer; rm -f "$MARCA"; inventario ;;
  *) sed -n 2,9p "$0"; exit 2 ;;
esac
