import { Injectable } from '@angular/core';
@Injectable({ providedIn: 'root' })
export class RespaldoPruebasLocalService {
  inspeccionar(): { origen: string; clave: string; datos: unknown }[] {
    const datos: { origen: string; clave: string; datos: unknown }[] = [];
    for (const [origen, almacenamiento] of [['sessionStorage', sessionStorage], ['localStorage', localStorage]] as const) {
      for (const clave of ['dimabug.pruebas.lista.v1', 'dimabug.pruebas.activo.v1', 'dimabug.pruebas.ocultos.v1']) {
        const raw = almacenamiento.getItem(clave); if (raw == null) continue;
        let valor: unknown; try { valor = JSON.parse(raw); } catch { valor = raw; }
        datos.push({ origen, clave, datos: valor });
      }
    }
    const raw = localStorage.getItem('dimabug.catalogos.v2');
    if (raw) { try { const antiguo = JSON.parse(raw); if (antiguo.pruebas != null) datos.push({ origen: 'localStorage', clave: 'dimabug.catalogos.v2.pruebas', datos: antiguo.pruebas }); }
      catch { datos.push({ origen: 'localStorage', clave: 'dimabug.catalogos.v2', datos: 'JSON ilegible: inspeccionar la clave original. No se modifica.' }); } }
    return datos;
  }
  descargar(): void {
    const respaldo = { fecha: new Date().toISOString(), aviso: 'Datos locales anteriores, no son estados confirmados de MySQL. No importar automáticamente.', registros: this.inspeccionar() };
    const url = URL.createObjectURL(new Blob([JSON.stringify(respaldo, null, 2)], { type: 'application/json' }));
    const a = document.createElement('a'); a.href = url; a.download = 'dimabug-pruebas-local-respaldo.json'; a.click(); URL.revokeObjectURL(url);
  }
}
