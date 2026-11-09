import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { apiUrl } from '../config/api';
import { plazoConsulta } from '../http/plazo-consulta';
import { ChatbotResponse } from '../models/chatbot.model';

@Injectable({ providedIn: 'root' })
export class ChatbotService {
  private readonly http = inject(HttpClient);

  consultar(consulta: string) {
    return this.http.post<ChatbotResponse>(apiUrl('/publico/chatbot/consultas'), { consulta }).pipe(plazoConsulta(),
      catchError((error: HttpErrorResponse) => throwError(() => new Error(error.status === 429 ? 'Has realizado demasiadas consultas. Intenta en un minuto.' : error.status === 400
        ? 'La consulta no es válida. Revisa el texto e intenta nuevamente.'
        : 'No pudimos consultar la Base de Conocimiento. Intenta nuevamente.'))),
    );
  }
}
