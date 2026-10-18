export interface ChatbotSolucion {
  conocimiento: string;
  titulo: string | null;
  solucion: string;
}

export interface ChatbotResponse {
  mensaje: string;
  soluciones: ChatbotSolucion[];
}
