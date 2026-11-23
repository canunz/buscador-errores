export interface OpcionSolicitud { id:number; nombre:string; }
export interface CatalogosSolicitud { sucursales:OpcionSolicitud[]; departamentos:OpcionSolicitud[]; }
export interface DatosSolicitanteFormulario {
  nombreCompleto:string; sucursalId:number|null; departamentoId:number|null;
  contacto:string; equipo:string; codigoUsuario:string;
}
export interface DatosSolicitante extends DatosSolicitanteFormulario {
  sucursalNombre:string; departamentoNombre:string;
}
export function contactoSolicitanteValido(valor:string):boolean {
  const v=valor.trim();
  const digitos=v.replace(/[^0-9]/g,'').length;
  return v.length<=50 && digitos>=2 && digitos<=18 && (v.match(/\+/g)??[]).length<=1
    && /^(?:(?:anexo|ext\.?|int\.?)\s*)?[+0-9(][0-9+() .-]*(?:\s+(?:anexo|ext\.?|int\.?)\s*[0-9]{2,6})?$/i.test(v);
}
export function equipoSolicitanteValido(valor:string):boolean {
  const v=valor.trim();
  if(!v)return true;
  if(v.length>100)return false;
  if(/^[0-9 ]+$/.test(v) && /^[0-9]{9,10}$/.test(v.replace(/ /g,'')))return true;
  if(v.includes(':')) {
    if(!/^[0-9a-f:]+$/i.test(v)||v.includes(':::')
      ||v.startsWith(':')&&!v.startsWith('::')||v.endsWith(':')&&!v.endsWith('::'))return false;
    const compressed=v.indexOf('::');
    if(compressed>=0 && compressed!==v.lastIndexOf('::'))return false;
    const groups=v.split(':').filter(Boolean);
    if(groups.some(g=>!/^[0-9a-f]{1,4}$/i.test(g)))return false;
    return compressed>=0 ? groups.length<8 : groups.length===8&&!v.startsWith(':')&&!v.endsWith(':');
  }
  const parts=v.split('.');
  return parts.length===4&&parts.every(p=>/^(0|[1-9][0-9]{0,2})$/.test(p)&&Number(p)<=255);
}
