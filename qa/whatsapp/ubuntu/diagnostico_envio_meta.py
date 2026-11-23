"""Diagnóstico del envío real a Meta (dimabug-wa-pilot), SIN exponer token, teléfonos ni contenido.

Reproduce el mismo camino que usa el backend: pasarela local 127.0.0.1:18888 (CONNECT) -> graph.facebook.com.
Imprime solo: resultado del CONNECT, código HTTP y campos de error de Meta (code, error_subcode, type, fbtrace_id).

  sudo python3 diagnostico_envio_meta.py               # solo lectura: config, alias, pasarela y GET del número (no envía)
  sudo python3 diagnostico_envio_meta.py --enviar      # además envía UN texto fijo de diagnóstico al participante QA

El envío usa el texto fijo "Diagnóstico DimaBug: prueba de envío." y solo al alias configurado con entrada tel:.
No modifica archivos, servicios, base de datos ni configuración.
"""
import argparse, json, re, socket, ssl
from pathlib import Path

CONF = Path('/etc/dimabug-wa-pilot')
PROXY = ('127.0.0.1', 18888)
TEXTO = 'Diagnóstico DimaBug: prueba de envío.'


def propiedades():
    datos = {}
    for linea in (CONF / 'application.properties').read_text(encoding='utf-8').splitlines():
        if '=' in linea and not linea.lstrip().startswith('#'):
            k, v = linea.split('=', 1); datos[k.strip()] = v.strip()
    return datos


def via_pasarela(host, metodo, ruta, token, cuerpo=None):
    """HTTPS por la pasarela, exactamente como el backend. Devuelve (connect, http, json|None)."""
    crudo = socket.create_connection(PROXY, timeout=10)
    crudo.sendall(f'CONNECT {host}:443 HTTP/1.1\r\nHost: {host}:443\r\n\r\n'.encode('ascii'))
    respuesta = b''
    while b'\r\n\r\n' not in respuesta and len(respuesta) < 8192:
        parte = crudo.recv(1024)
        if not parte: break
        respuesta += parte
    linea = respuesta.split(b'\r\n', 1)[0].decode('ascii', 'replace')
    if ' 200 ' not in linea + ' ':
        crudo.close(); return linea, None, None
    tls = ssl.create_default_context().wrap_socket(crudo, server_hostname=host)
    datos = json.dumps(cuerpo).encode() if cuerpo is not None else b''
    encabezados = [f'{metodo} {ruta} HTTP/1.1', f'Host: {host}', f'Authorization: Bearer {token}',
                   'Connection: close', f'Content-Length: {len(datos)}']
    if cuerpo is not None: encabezados.append('Content-Type: application/json')
    tls.sendall(('\r\n'.join(encabezados) + '\r\n\r\n').encode('ascii') + datos)
    bruto = b''
    while len(bruto) < 131072:
        parte = tls.recv(4096)
        if not parte: break
        bruto += parte
    tls.close()
    cabecera, _, resto = bruto.partition(b'\r\n\r\n')
    estado = int(cabecera.split(b' ', 2)[1]) if cabecera else 0
    if b'chunked' in cabecera.lower():  # Decodificación mínima de chunked.
        cuerpo_txt, r = b'', resto
        while r:
            tam, _, r = r.partition(b'\r\n'); n = int(tam.split(b';')[0] or b'0', 16)
            if n == 0: break
            cuerpo_txt += r[:n]; r = r[n + 2:]
        resto = cuerpo_txt
    try: js = json.loads(resto)
    except ValueError: js = None
    return linea, estado, js


def error_meta(js):
    e = (js or {}).get('error') or {}
    return {k: e.get(k) for k in ('code', 'error_subcode', 'type', 'fbtrace_id') if e.get(k) is not None} or None


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('--enviar', action='store_true')
    a = p.parse_args()
    cfg = propiedades(); token = (CONF / 'secrets/graph-token').read_text().strip()
    numero, version = cfg.get('dimabug.whatsapp.meta-qa.numero', ''), cfg.get('dimabug.whatsapp.meta-qa.graph-version', 'v23.0')
    participantes = cfg.get('dimabug.whatsapp.meta-qa.participantes', '')
    pares = [x.strip().split('=', 1) for x in participantes.split(',') if '=' in x]
    alias_tel = {v for k, v in pares if k.startswith('tel:')}
    alias_bsuid = {v for k, v in pares if k.startswith('bsuid:')}
    tel = next((k[4:] for k, v in pares if k.startswith('tel:')), None)
    r = {'config': {
        'envio_real': cfg.get('dimabug.whatsapp.meta-qa.envio-real'), 'version_graph': version,
        'numero_es_id_numerico': bool(re.fullmatch(r'\d{10,20}', numero)),
        'token_presente': bool(token), 'token_formato_valido': bool(re.fullmatch(r'[A-Za-z0-9_.|-]+', token)),
        'alias_con_tel': len(alias_tel), 'alias_bsuid_con_tel': sorted(alias_bsuid) == sorted(alias_tel) and bool(alias_tel),
        'telefono_formato_e164_sin_mas': bool(tel and re.fullmatch(r'\d{8,15}', tel))}}
    for host in ('graph.facebook.com', 'lookaside.fbsbx.com'):
        try:
            c = socket.create_connection(PROXY, timeout=10)
            c.sendall(f'CONNECT {host}:443 HTTP/1.1\r\nHost: {host}:443\r\n\r\n'.encode()); linea = c.recv(256).split(b'\r\n')[0].decode()
            c.close()
        except OSError as e:
            linea = 'SIN_CONEXION_' + type(e).__name__
        r.setdefault('pasarela_connect', {})[host] = linea
    try:
        con, http, js = via_pasarela('graph.facebook.com', 'GET', f'/{version}/{numero}?fields=id,verified_name,quality_rating', token)
        r['get_numero'] = {'connect': con, 'http': http, 'error_meta': error_meta(js),
                           'id_coincide': bool(js and js.get('id') == numero)}
    except OSError as e:
        r['get_numero'] = {'error_red': type(e).__name__}
    if a.enviar:
        if not tel:
            r['envio'] = 'SIN_TELEFONO_EN_PARTICIPANTES'
        else:
            cuerpo = {'messaging_product': 'whatsapp', 'recipient_type': 'individual', 'to': tel,
                      'type': 'text', 'text': {'preview_url': False, 'body': TEXTO}}
            con, http, js = via_pasarela('graph.facebook.com', 'POST', f'/{version}/{numero}/messages', token, cuerpo)
            r['envio'] = {'connect': con, 'http': http, 'error_meta': error_meta(js),
                          'wamid_recibido': bool(js and (js.get('messages') or [{}])[0].get('id'))}
    print(json.dumps(r, indent=2, ensure_ascii=False))


if __name__ == '__main__':
    main()
