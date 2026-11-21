import { CandidatoErrorOcr, ExtraccionErrorOcr } from './extraccion-error.model';

const ERROR = /\b(?:error(?:es)?|fall[oó]|falla|failed|failure|exception|fatal|timeout|offline|atasc\w*|denegad\w*)\b|\b(?:no se puede|no se pudo|no puede|no pudo|sin conexi[oó]n|no responde|cannot|can't|unable to|access denied|not found|not responding|connection (?:refused|reset)|timed out|unreachable|no route to host)\b/i;
const CODIGO = /\b0x[0-9a-f]{4,}\b|\b(?:ora|hresult|sqlstate|err)[-_: ]?(?=[a-z0-9]*\d)[a-z0-9]+\b|\bERR_[A-Z][A-Z_]{2,}\b|\bHTTP\s+[45]\d{2}\b/gi;
const RUTA = /(?:^|\s)(?:[a-z]:[\\/]|[\\/]{1,2}|https?:\/\/)/i;
const BORDES = /[|¦\[\]]/;

/** Extrae propuestas locales, no busca soluciones ni corrige letras/códigos por similitud. */
export class ExtractorErrorOcr {
  extraer(original: string): ExtraccionErrorOcr {
    const lineas = original.split(/\r\n|\n|\r/).map(l => l.replace(/[\t ]+/g,' ').trim());
    const propuestas: Array<CandidatoErrorOcr & {prioridad:number;alternativa:boolean}> = [];
    const vistas = new Set<string>();
    const agregar = (texto: string, desde: number, hasta: number, etiqueta: string, avisos: string[]) => {
      const limpio = texto.trim(); const clave = limpio.toLowerCase().replace(/\s+/g,' ');
      if (!limpio || vistas.has(clave)) return;
      vistas.add(clave);
      propuestas.push({id:'ocr-'+desde+'-'+propuestas.length,texto:limpio,etiqueta,
        lineaDesde:desde+1,lineaHasta:hasta+1,avisos:[...new Set(avisos)],
        alternativa:etiqueta==='Solo mensaje, sin ruta',
        prioridad:(this.codigos(limpio).length?4:0)+(ERROR.test(limpio)?2:0)});
    };
    for (let i=0;i<lineas.length;i++) {
      if (!ERROR.test(lineas[i]) && !this.codigos(lineas[i]).length) continue;
      const inicio=i; let texto=lineas[i]; const avisos:string[]=[];
      // Solo adyacencias verificables; jamás se salta una línea para inventar una ruta.
      for (let n=0;n<3 && i+1<lineas.length;n++) {
        const siguiente=lineas[i+1];
        if (!siguiente) break;
        if (/-$/.test(texto) && /^[a-záéíóúñ]/.test(siguiente) && !ERROR.test(siguiente) && !BORDES.test(siguiente)) {
          texto+=siguiente; i++; avisos.push('Se unió una continuación conservando el guion. Revisa si corresponde a una sola palabra.');
        } else if (/\b0x[0-9a-f]+$/i.test(texto) && /^[0-9a-f]{1,16}$/i.test(siguiente)) {
          texto+=siguiente; i++; avisos.push('Se unieron fragmentos adyacentes de un código. Comprueba todos sus caracteres.');
        } else if (/\b(?:ora|hresult|sqlstate|err)[-_:]$/i.test(texto) && /^[a-z0-9]{3,12}$/i.test(siguiente) && /\d/.test(siguiente)) {
          texto+=siguiente; i++; avisos.push('Se unió un código dividido; verifica su contenido.');
        } else if (/[\\/]$/.test(texto) && this.continuacionRuta(siguiente)) {
          texto+=siguiente; i++; avisos.push('Se unieron líneas de una ruta. Verifica los separadores.');
        } else if (RUTA.test(siguiente) && /[\\/]$/.test(siguiente) && i+2<lineas.length &&
          this.continuacionRuta(lineas[i+2]) && this.rutaCompleta(siguiente+lineas[i+2])) {
          texto+=' '+siguiente+lineas[i+2]; i+=2;
          avisos.push('Se unieron líneas de una ruta. Verifica los separadores.');
        } else if (RUTA.test(siguiente) && this.rutaCompleta(siguiente) && !ERROR.test(siguiente)) {
          texto+=' '+siguiente; i++;
        } else if (!this.codigos(texto).length && this.codigos(siguiente).length && !ERROR.test(siguiente) && this.soloCodigo(siguiente)) {
          texto+=' '+siguiente; i++;
        } else {
          if (RUTA.test(siguiente) || (RUTA.test(texto) && BORDES.test(siguiente)))
            avisos.push('La ruta cercana parece incompleta o contiene ruido. Revisa el OCR original; no se reconstruyó.');
          break;
        }
      }
      const ruta=texto.search(RUTA);
      const mensaje=ruta>0?texto.slice(0,ruta).trim():'';
      // Una ruta incompleta no debe contaminar por defecto un mensaje que sí se observó completo.
      const incompleta=ruta>=0 && (!this.rutaCompleta(texto.slice(ruta).trim()) || BORDES.test(texto.slice(ruta)));
      if (incompleta && mensaje && !this.codigos(texto.slice(ruta)).length) {
        agregar(mensaje,inicio,i,'Mensaje detectado',[...avisos,'La consulta omite una ruta dudosa. Puedes revisarla en el original.']);
      } else {
        agregar(texto,inicio,i,'Mensaje detectado',avisos);
      }
      // Alternativa conservadora solo si no se pierde ningún código técnico observado.
      if (mensaje && ERROR.test(mensaje) && this.codigos(texto).every(c=>this.codigos(mensaje).includes(c)))
        agregar(mensaje,inicio,i,'Solo mensaje, sin ruta',['Esta alternativa omite la ruta. Revisa el texto antes de buscar.']);
    }
    propuestas.sort((a,b)=>Number(a.alternativa)-Number(b.alternativa) || b.prioridad-a.prioridad || a.lineaDesde-b.lineaDesde);
    return {original,candidatos:propuestas.slice(0,3).map(({prioridad,alternativa,...c})=>c),hayMas:propuestas.length>3};
  }
  private codigos(texto:string):string[] { return Array.from(texto.matchAll(new RegExp(CODIGO.source,CODIGO.flags)),m=>m[0].toLowerCase()); }
  private soloCodigo(texto:string):boolean { const c=this.codigos(texto);return c.length===1 && c[0]===texto.toLowerCase(); }
  private rutaCompleta(texto:string):boolean {
    return !BORDES.test(texto) && !/[\\/-]$/.test(texto) &&
      ( /\.[a-z0-9]{1,8}$/i.test(texto) || /^https?:\/\/[^\s/]+(?:\/\S*)?$/i.test(texto));
  }
  private continuacionRuta(texto:string):boolean {
    return !BORDES.test(texto) && !ERROR.test(texto) && !/[$¢€]/.test(texto) &&
      (/[\\/]/.test(texto) || /\.[a-z0-9]{1,8}$/i.test(texto));
  }
}
