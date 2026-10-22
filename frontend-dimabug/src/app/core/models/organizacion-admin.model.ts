export interface DepartamentoAdmin { id: number; nombre: string; activo: boolean; version: number; }
export interface ContactoDepartamentoAdmin {
  id: number; departamentoId: number; tipo: string; valor: string; activo: boolean;
  departamentoActivo: boolean; disponible: boolean; version: number;
}

export interface ResponsableConsulta { id: number; nombre: string; cargo?: string | null; contacto?: string | null; }
export interface ResponsableAdmin extends ResponsableConsulta {
  departamentoId: number | null; activo: boolean; revision: string;
}
