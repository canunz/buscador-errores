export interface CandidatoErrorOcr {
  id: string;
  texto: string;
  etiqueta: string;
  lineaDesde: number;
  lineaHasta: number;
  avisos: string[];
}
export interface ExtraccionErrorOcr {
  original: string;
  candidatos: CandidatoErrorOcr[];
  hayMas: boolean;
}
