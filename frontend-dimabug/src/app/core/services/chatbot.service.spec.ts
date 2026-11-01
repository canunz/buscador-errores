import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ChatbotService } from './chatbot.service';

describe('Contrato HTTP chatbot', () => {
  let http: HttpTestingController;
  let api: ChatbotService;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController); api = TestBed.inject(ChatbotService);
  });
  afterEach(() => http.verify());
  it('envía contrato original al endpoint real', () => {
    api.consultar('impresora').subscribe(r => expect(r.soluciones).toEqual([]));
    const req = http.expectOne('/api/chatbot/consultas');
    expect(req.request.method).toBe('POST'); expect(req.request.body).toEqual({ consulta: 'impresora' });
    req.flush({ mensaje: 'Contacta a Soporte', soluciones: [] });
  });
  it('presenta mensaje de validación en 400', () => {
    api.consultar('x').subscribe({ error: (e: Error) => expect(e.message).toContain('no es válida') });
    http.expectOne('/api/chatbot/consultas').flush({}, { status: 400, statusText: 'Bad Request' });
  });
});
