import { TestBed } from '@angular/core/testing';
import { HttpClient, HttpHeaders, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';
import { backendApiPath, isPublicApiUrl } from '../config/api';
import { environment } from '../../../environments/environment';

describe('JWT limitado al backend autorizado', () => {
  let http: HttpClient; let backend: HttpTestingController;
  let auth: { token: jasmine.Spy; me: jasmine.Spy; logout: jasmine.Spy };
  const original = { ...environment };
  beforeEach(() => {
    environment.production = false; environment.apiUrl = '';
    auth = { token: jasmine.createSpy().and.returnValue('token-sintetico'),
      me: jasmine.createSpy().and.returnValue(of({})), logout: jasmine.createSpy() };
    TestBed.configureTestingModule({ providers: [
      provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting(),
      { provide: AuthService, useValue: auth },
    ] });
    http = TestBed.inject(HttpClient); backend = TestBed.inject(HttpTestingController);
  });
  afterEach(() => { backend.verify(); Object.assign(environment, original); });
  for (const path of ['/api/tickets', '/api/admin/responsables', '/api/chatbot/consultas', location.origin + '/api/conocimientos']) {
    it('envía JWT a ' + path, () => {
      http.get(path).subscribe(); const req = backend.expectOne(path);
      expect(req.request.headers.get('Authorization')).toBe('Bearer token-sintetico'); req.flush({});
    });
  }
  for (const path of [
    'https://ajeno.invalid/api/tickets', '//ajeno.invalid/api/tickets', '/api-falso/tickets',
    '/assets/foto.png', 'https://ajeno.invalid/api/conocimientos/1/materiales/2/archivo',
    '/api/../fuera', 'data:text/plain,contenido', 'file:materiales/uuid',
    'https://usuario:clave@ajeno.invalid/api/tickets',
  ]) {
    it('no envía JWT a ' + path, () => {
      http.get(path).subscribe(); const req = backend.expectOne(path);
      expect(req.request.headers.has('Authorization')).toBeFalse(); req.flush({});
    });
  }
  it('retira Authorization explícito en un destino externo', () => {
    const url = 'https://ajeno.invalid/api/tickets';
    http.get(url, { headers: new HttpHeaders({ Authorization: 'Bearer token-sintetico' }) }).subscribe();
    const req = backend.expectOne(url); expect(req.request.headers.has('Authorization')).toBeFalse(); req.flush({});
  });
  it('login y health exactos son públicos, sus imitaciones no', () => {
    for (const url of ['/api/auth/login?next=1', '/api/health']) {
      http.get(url).subscribe(); const req = backend.expectOne(url);
      expect(req.request.headers.has('Authorization')).toBeFalse(); req.flush({});
    }
    for (const url of ['/api/tickets?next=/api/auth/login', '/api/health/privado']) {
      expect(isPublicApiUrl(url)).toBeFalse(); http.get(url).subscribe();
      const req = backend.expectOne(url); expect(req.request.headers.has('Authorization')).toBeTrue(); req.flush({});
    }
  });
  it('autoriza solo el backend remoto configurado y su prefijo', () => {
    environment.production = true; environment.apiUrl = 'https://backend.example.invalid/dimabug';
    const url = 'https://backend.example.invalid/dimabug/api/tickets';
    http.get(url).subscribe(); const req = backend.expectOne(url);
    expect(req.request.headers.has('Authorization')).toBeTrue(); req.flush({});
    expect(backendApiPath('https://backend.example.invalid/api/tickets')).toBeNull();
    expect(backendApiPath('https://backend.example.invalid.evil.invalid/dimabug/api/tickets')).toBeNull();
    expect(backendApiPath('http://backend.example.invalid/dimabug/api/tickets')).toBeNull();
    expect(backendApiPath('https://user@backend.example.invalid/dimabug/api/tickets')).toBeNull();
    expect(backendApiPath('/api/tickets')).toBeNull();
  });
  it('un 401 externo no afecta la sesión de DimaBug', () => {
    const url = 'https://ajeno.invalid/api/auth/me';
    http.get(url).subscribe({ error: () => {} }); backend.expectOne(url).flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.me).not.toHaveBeenCalled(); expect(auth.logout).not.toHaveBeenCalled();
  });
  it('mantiene revisión de sesión y logout en rechazos reales', () => {
    http.get('/api/tickets').subscribe({ error: () => {} });
    backend.expectOne('/api/tickets').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.me).toHaveBeenCalledTimes(1);
    http.get('/api/auth/me').subscribe({ error: () => {} });
    backend.expectOne('/api/auth/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.logout).toHaveBeenCalledTimes(1);
  });
});
