#!/bin/bash
# Comunicación real WhatsApp en DigitalOcean (dimabug-wa-pilot) con OCR en Linux. Fases explícitas:
#   sudo bash whatsapp_real.sh artefactos   # instala whatsapp-qa.jar y GraphHttpsProxy.class nuevos (con respaldo)
#   sudo bash whatsapp_real.sh ocr          # Node 22 + puente OCR + dependencias Linux (npm ci) en /opt/dimabug-wa-pilot
#   sudo bash whatsapp_real.sh ocr-probar   # lee una imagen de prueba como el usuario del servicio, sin red
#   sudo bash whatsapp_real.sh configurar   # drop-in 85 (token Graph + proxy) y claves reales en application.properties
#   sudo bash whatsapp_real.sh iniciar      # habilita y arranca QA + pasarela de forma persistente
#   sudo bash whatsapp_real.sh detener      # detiene, deshabilita y bloquea la cuenta MySQL QA
#   sudo bash whatsapp_real.sh revertir     # detener + restaura application.properties, JAR y pasarela previos
# Archivos esperados junto al script: whatsapp-qa.jar, GraphHttpsProxy.class, ocr-linux-bundle.tgz,
# 85-whatsapp-real.conf. Los secretos reales (graph-token, app-secret, verify-token) los coloca la operadora:
# este script solo verifica que existan con permisos 0600 y nunca los muestra.
set -euo pipefail
[[ $(id -u) == 0 ]] || { echo "Ejecutar con sudo"; exit 2; }
DIR=$(cd "$(dirname "$0")" && pwd)
OPT=/opt/dimabug-wa-pilot; CONF=/etc/dimabug-wa-pilot; STATE=/var/lib/dimabug-wa-pilot
QA=dimabug-wa-pilot.service; GRAPH=dimabug-wa-pilot-graph.service; USUARIO=dimabug-wa-pilot
NODE_VERSION=v22.23.5; NODE=$OPT/node/bin/node
RESP=/root/whatsapp-real-respaldo; install -d -m 700 "$RESP"
SELLO=$(date -u +%Y%m%dT%H%M%SZ)

detenido() { for u in $QA $GRAPH; do [[ $(systemctl is-active $u) == activ* ]] && { echo "NO-GO: detener $u primero"; exit 1; }; done; return 0; }
cuenta() { mysql --protocol=socket --batch --skip-column-names -e "ALTER USER 'wa_qa_pilot'@'127.0.0.1' ACCOUNT $1; SELECT account_locked FROM mysql.user WHERE User='wa_qa_pilot' AND Host='127.0.0.1';"; }
clave() {  # Fija una clave en application.properties (reemplaza o agrega), sin imprimir valores.
  local k=$1 v=$2 f=$CONF/application.properties
  if grep -q "^${k//./\\.}=" "$f"; then sed -i "s#^${k//./\\.}=.*#$k=$v#" "$f"; else echo "$k=$v" >> "$f"; fi
}

case "${1:-}" in
  artefactos)
    detenido
    for f in whatsapp-qa.jar GraphHttpsProxy.class; do [[ -f $DIR/$f ]] || { echo "NO-GO: falta $f"; exit 1; }; done
    cp -p $OPT/whatsapp-qa.jar "$RESP/whatsapp-qa.jar.$SELLO"; cp -p $OPT/proxy/GraphHttpsProxy.class "$RESP/GraphHttpsProxy.class.$SELLO"
    install -m 0644 "$DIR/whatsapp-qa.jar" $OPT/whatsapp-qa.jar
    install -m 0644 "$DIR/GraphHttpsProxy.class" $OPT/proxy/GraphHttpsProxy.class
    sha256sum $OPT/whatsapp-qa.jar $OPT/proxy/GraphHttpsProxy.class ;;
  ocr)
    detenido
    [[ -f $DIR/ocr-linux-bundle.tgz ]] || { echo "NO-GO: falta ocr-linux-bundle.tgz"; exit 1; }
    if [[ ! -x $NODE ]]; then
      t=node-$NODE_VERSION-linux-x64.tar.xz; cd /tmp
      curl -fsSLO https://nodejs.org/dist/$NODE_VERSION/$t && curl -fsSLO https://nodejs.org/dist/$NODE_VERSION/SHASUMS256.txt
      grep " $t\$" SHASUMS256.txt | sha256sum -c - ; install -d $OPT/node; tar -xJf $t -C $OPT/node --strip-components=1; cd "$DIR"
    fi
    echo "Node: $($NODE -v)"
    tar -xzf "$DIR/ocr-linux-bundle.tgz" -C $OPT
    (cd $OPT/frontend-dimabug/tools/ocr-node && PATH=$OPT/node/bin:$PATH npm ci --omit=dev --no-audit --no-fund)
    chown -R root:root $OPT/node $OPT/frontend-dimabug $OPT/qa; chmod -R a+rX $OPT/node $OPT/frontend-dimabug $OPT/qa
    install -d -o $USUARIO -g $USUARIO -m 0700 $STATE/ocr-tmp
    echo "OCR instalado en $OPT" ;;
  ocr-probar)
    img=$OPT/frontend-dimabug/src/test-assets/ocr/error-legible.png
    r=$(sudo -u $USUARIO env -i TEMP=$STATE/ocr-tmp TMP=$STATE/ocr-tmp timeout 40 $NODE $OPT/qa/whatsapp/ocr-bridge.cjs --stdin < "$img")
    echo "$r"; grep -q '"candidatos"' <<<"$r" && echo "GO: OCR Linux operativo" || { echo "NO-GO: OCR"; exit 1; } ;;
  configurar)
    detenido
    for s in graph-token app-secret verify-token; do
      f=$CONF/secrets/$s; [[ -s $f && $(stat -c %a "$f") == 600 ]] || { echo "NO-GO: falta $f (0600) con el valor REAL de Meta"; exit 1; }
    done
    [[ -f $DIR/85-whatsapp-real.conf ]] || { echo "NO-GO: falta 85-whatsapp-real.conf"; exit 1; }
    cp -p $CONF/application.properties "$RESP/application.properties.$SELLO"
    # Reutilizar los identificadores y participantes de la configuracion privada existente.
    for k in waba numero participantes; do
      grep -Eq "^dimabug[.]whatsapp[.]meta-qa[.]$k=.+" "$CONF/application.properties" || { echo "NO-GO: falta configuracion privada de $k"; exit 1; }
    done
    clave dimabug.whatsapp.meta-qa.recepcion true; clave dimabug.whatsapp.meta-qa.procesamiento true
    clave dimabug.whatsapp.meta-qa.envio true; clave dimabug.whatsapp.meta-qa.envio-real true
    clave dimabug.whatsapp.meta-qa.medios true; clave dimabug.whatsapp.meta-qa.conexion-meta true
    clave dimabug.whatsapp.autoatencion.habilitada true
    clave dimabug.whatsapp.autoatencion.ticket-actor-email soporte@qa.dimabug.test
    clave dimabug.whatsapp.ocr.node $NODE; clave dimabug.whatsapp.ocr.tmp $STATE/ocr-tmp
    chmod 600 $CONF/application.properties
    install -m 0644 -D "$DIR/85-whatsapp-real.conf" /etc/systemd/system/$QA.d/85-whatsapp-real.conf
    systemctl daemon-reload
    if systemctl show $QA -p Environment | grep -q "https.proxyPort=18888"; then echo "GO: configuración real aplicada (respaldo en $RESP)"
    else echo "NO-GO: systemd no aplicó 85-whatsapp-real.conf"; exit 1; fi ;;
  iniciar)
    touch $CONF/PILOT_APPROVED; chmod 600 $CONF/PILOT_APPROVED
    [[ $(cuenta UNLOCK) == N ]] || { echo "NO-GO: no se pudo desbloquear la cuenta"; exit 1; }
    systemctl reset-failed $QA $GRAPH 2>/dev/null || true
    systemctl enable --now $GRAPH $QA
    for i in $(seq 1 90); do
      token=$(cat $CONF/secrets/verify-token)
      [[ $(curl -s "http://127.0.0.1:8082/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token=$token&hub.challenge=7" 2>/dev/null) == 7 ]] && { echo "GO: QA listo en $((i*2)) s"; exit 0; }
      [[ $(systemctl is-active $QA) == failed ]] && break; sleep 2
    done
    echo "NO-GO: QA no quedó listo; ejecutar 'detener' y revisar journalctl -u $QA"; exit 1 ;;
  detener)
    systemctl disable --now $QA $GRAPH 2>/dev/null || true; rm -f $CONF/PILOT_APPROVED
    echo "Cuenta bloqueada: $(cuenta LOCK)" ;;
  revertir)
    bash "$0" detener
    ultimo() { ls -1t "$RESP"/$1.* 2>/dev/null | head -1; }
    [[ -n $(ultimo application.properties) ]] && cp -p "$(ultimo application.properties)" $CONF/application.properties
    [[ -n $(ultimo whatsapp-qa.jar) ]] && cp -p "$(ultimo whatsapp-qa.jar)" $OPT/whatsapp-qa.jar
    [[ -n $(ultimo GraphHttpsProxy.class) ]] && cp -p "$(ultimo GraphHttpsProxy.class)" $OPT/proxy/GraphHttpsProxy.class
    rm -f /etc/systemd/system/$QA.d/85-whatsapp-real.conf; systemctl daemon-reload; echo "Revertido al estado previo" ;;
  *) sed -n 2,13p "$0"; exit 2 ;;
esac
