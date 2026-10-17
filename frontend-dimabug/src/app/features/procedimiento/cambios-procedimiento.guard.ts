import { CanDeactivateFn } from '@angular/router';
export interface ProcedimientoConCambios {
  tieneCambiosPendientes(): boolean;
  operacionEnCurso(): boolean;
}
export const cambiosProcedimientoGuard: CanDeactivateFn<ProcedimientoConCambios> = componente => {
  if (componente.operacionEnCurso()) return false;
  return !componente.tieneCambiosPendientes() || window.confirm('Hay cambios pendientes o un guardado sin verificar. ¿Desea salir y descartar los datos del formulario? Los registros confirmados se conservan.');
};
