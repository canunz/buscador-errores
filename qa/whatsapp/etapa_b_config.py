"""Preparación pura: ni inicia Docker ni habilita Meta al importar."""
from pathlib import Path
from meta_graph_preflight import read_token

TOKEN_PROPERTY='dimabug.whatsapp.meta-qa.graph-token'

def prepare(props,token_file):
    if props.get('dimabug.whatsapp.autoatencion.habilitada')!='true':raise ValueError('AUTOATENCION_REQUIRED')
    for flag in ['medios','conexion-meta']:
        if props.get('dimabug.whatsapp.meta-qa.'+flag)!='false':raise ValueError('MEDIA_MUST_REMAIN_DISABLED')
    source=Path(token_file).resolve()
    token=read_token(source)
    # configtree elimina saltos finales, pero no normaliza espacios de credenciales.
    raw=source.read_text(encoding='utf-8-sig')
    if raw.rstrip('\r\n')!=token or source.read_bytes().startswith(b'\xef\xbb\xbf'):raise ValueError('TOKEN_FILE_EXTRA_WHITESPACE')
    result=dict(props)
    result.pop(TOKEN_PROPERTY,None)
    result.update({'dimabug.whatsapp.meta-qa.envio-real':'true',
                   'dimabug.whatsapp.meta-qa.graph-version':'v27.0',
                   'spring.config.import':'configtree:/run/qa-secrets/'})
    return result,['--mount',f'type=bind,source={source},target=/run/qa-secrets/{TOKEN_PROPERTY},readonly']
