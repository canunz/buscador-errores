/**
 * Tipos de datos de chatbot que usa el frontend.
 * Deben coincidir con lo que responde el backend.
 */
export interface ChatbotSolucion {
  id?: number;
  conocimiento: string;
  titulo: string | null;
  solucion: string;
}

export interface ChatbotResponse {
  mensaje: string;
  soluciones: ChatbotSolucion[];
}
