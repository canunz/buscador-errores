import { Injectable } from '@angular/core';
@Injectable({ providedIn: 'root' })
export class RespaldoDepartamentosLocalService {
  inspeccionar(): { origen: string; clave: string; datos: unknown }[] {
    const resultado: { origen: string; clave: string; datos: unknown }[] = [];
    for (const [origen, storage] of [['localStorage', localStorage], ['sessionStorage', sessionStorage]] as const) {
      for (const clave of ['dimabug.departamentos.detalle.v1', 'dimabug.departamentos.ocultos.v1']) {
        const raw = storage.getItem(clave);
        if (raw != null) { let datos: unknown; try { datos = JSON.parse(raw); } catch { datos = raw; }
          resultado.push({ origen, clave, datos }); }
      }
    }
    const raw = localStorage.getItem('dimabug.catalogos.v2');
    if (raw) { try {
      const antiguo = JSON.parse(raw) as { departamentos?: unknown };
      if (antiguo.departamentos != null) resultado.push({ origen: 'localStorage', clave: 'dimabug.catalogos.v2.departamentos', datos: antiguo.departamentos });
    } catch { resultado.push({ origen: 'localStorage', clave: 'dimabug.catalogos.v2', datos: 'JSON ilegible: conservar la clave original; no se modifica ni se exportan otras colecciones.' }); } }
    return resultado;
  }
  descargar(): void {
    const datos = { formato: 'dimabug.departamentos.respaldo.v1', fecha: new Date().toISOString(), aviso: 'Datos locales anteriores. No son registros confirmados de MySQL; no importar automáticamente.', registros: this.inspeccionar() };
    const url = URL.createObjectURL(new Blob([JSON.stringify(datos, null, 2)], { type: 'application/json' }));
    const enlace = document.createElement('a'); enlace.href = url; enlace.download = 'dimabug-departamentos-local-respaldo.json'; enlace.click(); URL.revokeObjectURL(url);
  }
}
