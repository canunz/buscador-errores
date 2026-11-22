#!/bin/bash
# Nginx + certificado permanente para el webhook QA: https://wa-qa.dimabug.casol.cl/webhooks/whatsapp
# Solo agrega dos vhosts propios (puertos 80 y 443 por SNI); no toca los vhosts de avanza, buscadorerrores ni staging.
# Requiere: certbot_consolidar.sh consolidar (renovador único) y el registro DNS A (solo DNS) apuntando a este servidor.
#   sudo bash nginx_wa_qa.sh http          # vhost :80 para ACME (webroot) y redirección a HTTPS
#   sudo bash nginx_wa_qa.sh dns           # comprueba que el nombre resuelve a una IP de este servidor
#   sudo bash nginx_wa_qa.sh certificado   # emite el certificado con Snap certbot (webroot, sin tocar Nginx)
#   sudo bash nginx_wa_qa.sh https         # vhost :443 desde nginx-wa-qa.conf.template (mismo directorio)
#   sudo bash nginx_wa_qa.sh verificar     # TLS, 403 fuera del webhook y GET de verificación si QA está activo
#   sudo bash nginx_wa_qa.sh revertir      # quita los dos vhosts (conserva el certificado) y recarga
set -euo pipefail
[[ $(id -u) == 0 ]] || { echo "Ejecutar con sudo"; exit 2; }
HOST=wa-qa.dimabug.casol.cl
ACME=/var/www/wa-qa-acme
V80=/etc/nginx/sites-available/wa-qa-80.conf
V443=/etc/nginx/sites-available/wa-qa-443.conf
LIVE=/etc/letsencrypt/live/$HOST
DIR=$(cd "$(dirname "$0")" && pwd)

activar() {  # Instala un vhost; si nginx -t falla, lo retira y no recarga.
  ln -sf "$1" "/etc/nginx/sites-enabled/$(basename "$1")"
  if nginx -t 2>/tmp/nginx-t.log; then systemctl reload nginx; echo "OK $(basename "$1") activo"
  else rm -f "/etc/nginx/sites-enabled/$(basename "$1")" "$1"; cat /tmp/nginx-t.log; echo "NO-GO: nginx -t falló; vhost retirado"; exit 1; fi
}

case "${1:-}" in
  http)
    install -d -m 0755 "$ACME/.well-known/acme-challenge"
    cat > "$V80" <<EOF
server {
    listen 80;
    listen [::]:80;
    server_name $HOST;
    server_tokens off;
    location ^~ /.well-known/acme-challenge/ { root $ACME; default_type text/plain; }
    location / { return 301 https://\$host\$request_uri; }
}
EOF
    activar "$V80" ;;
  dns)
    resuelto=$(getent ahostsv4 "$HOST" | awk '{print $1}' | sort -u)
    locales=$(ip -4 -o addr show scope global | awk '{split($4,a,"/");print a[1]}')
    echo "DNS $HOST -> ${resuelto:-<sin respuesta>}"; echo "IPv4 del servidor: $(echo $locales)"
    for ip in $resuelto; do grep -qx "$ip" <<<"$locales" && { echo "GO: DNS apunta a este servidor"; exit 0; }; done
    echo "NO-GO: el nombre no apunta a este servidor (o el proxy de Cloudflare está activo)"; exit 1 ;;
  certificado)
    [[ $(systemctl is-active snap.certbot.renew.timer) == active ]] || { echo "NO-GO: renovador Snap inactivo"; exit 1; }
    [[ $(systemctl is-enabled certbot.timer 2>/dev/null || true) != enabled ]] || { echo "NO-GO: consolidar Certbot primero"; exit 1; }
    bash "$0" dns
    /snap/bin/certbot certonly --webroot -w "$ACME" -d "$HOST" --cert-name "$HOST" \
      --non-interactive --agree-tos --keep-until-expiring
    openssl x509 -in "$LIVE/fullchain.pem" -noout -subject -enddate -ext subjectAltName ;;
  https)
    [[ -f $LIVE/fullchain.pem && -f $LIVE/privkey.pem ]] || { echo "NO-GO: falta el certificado; ejecutar 'certificado'"; exit 1; }
    [[ -f $DIR/nginx-wa-qa.conf.template ]] || { echo "NO-GO: falta nginx-wa-qa.conf.template junto al script"; exit 1; }
    install -d -o www-data -g www-data -m 0700 /var/lib/dimabug-wa-qa-nginx/body
    sed -e "s#listen 127.0.0.1:18443 ssl;#listen 443 ssl;\n    listen [::]:443 ssl;#" \
        -e "s#__CERT_FULLCHAIN__#$LIVE/fullchain.pem#" -e "s#__CERT_PRIVATE_KEY__#$LIVE/privkey.pem#" \
        "$DIR/nginx-wa-qa.conf.template" > "$V443"
    grep -q "listen 443 ssl;" "$V443" && ! grep -q "__CERT_" "$V443" || { rm -f "$V443"; echo "NO-GO: plantilla inesperada"; exit 1; }
    activar "$V443" ;;
  verificar)
    echo "== Certificado servido"
    echo | openssl s_client -connect 127.0.0.1:443 -servername "$HOST" 2>/dev/null | openssl x509 -noout -subject -issuer -enddate
    echo "== Fuera del webhook (esperado 403)"
    curl -s -o /dev/null -w "%{http_code}\n" --resolve "$HOST:443:127.0.0.1" "https://$HOST/"
    if [[ $(systemctl is-active dimabug-wa-pilot.service) == active ]]; then
      token=$(cat /etc/dimabug-wa-pilot/secrets/verify-token)
      r=$(curl -s --resolve "$HOST:443:127.0.0.1" "https://$HOST/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token=$token&hub.challenge=424242")
      [[ $r == 424242 ]] && echo "GO: GET de verificación 200 a través de Nginx" || { echo "NO-GO: verificación GET"; exit 1; }
    else echo "QA detenido: GET de verificación omitido (iniciar QA y repetir)"; fi ;;
  revertir)
    rm -f /etc/nginx/sites-enabled/wa-qa-80.conf /etc/nginx/sites-enabled/wa-qa-443.conf "$V80" "$V443"
    nginx -t && systemctl reload nginx && echo "Vhosts QA retirados (certificado conservado)" ;;
  *) sed -n 2,12p "$0"; exit 2 ;;
esac
