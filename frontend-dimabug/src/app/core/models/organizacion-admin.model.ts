export interface DepartamentoAdmin { id: number; nombre: string; activo: boolean; version: number; }
export interface ContactoDepartamentoAdmin {
  id: number; departamentoId: number; tipo: string; valor: string; activo: boolean;
  departamentoActivo: boolean; disponible: boolean; version: number;
}
