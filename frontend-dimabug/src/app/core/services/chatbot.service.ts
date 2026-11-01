import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { apiUrl } from '../config/api';
import { ChatbotResponse } from '../models/chatbot.model';

@Injectable({ providedIn: 'root' })
export class ChatbotService {
  private readonly http = inject(HttpClient);

  consultar(consulta: string) {
    return this.http.post<ChatbotResponse>(apiUrl('/chatbot/consultas'), { consulta }).pipe(
      catchError((error: HttpErrorResponse) => throwError(() => new Error(error.status === 400
        ? 'La consulta no es válida. Revisa el texto e intenta nuevamente.'
        : 'No pudimos consultar la Base de Conocimiento. Intenta nuevamente.'))),
    );
  }
}
