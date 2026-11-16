import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ProcedimientoService } from './procedimiento.service';
describe('QA014 ProcedimientoService: verificación sin caché ni reenvío', () => {
 let api: ProcedimientoService; let http: HttpTestingController;
 beforeEach(() => { TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }); api = TestBed.inject(ProcedimientoService); http = TestBed.inject(HttpTestingController); });
 afterEach(() => http.verify());
 it('consulta forzada no reutiliza un GET que comenzó antes del guardado', () => { api.listarPasos(1).subscribe(); api.listarPasos(1,true).subscribe(); const consultas = http.match('/api/procedimientos/1/pasos'); expect(consultas.length).toBe(2); consultas.forEach(r => r.flush([])); });
 it('preserva status 0 para distinguir respuesta perdida al crear', () => { let estado: number | undefined; api.crear({ nombre: 'Guía', descripcion: null }).subscribe({ error: e => estado = e.status }); http.expectOne('/api/procedimientos').error(new ProgressEvent('error')); expect(estado).toBe(0); });
 it('error conocido conserva status y mensaje sin retry automático', () => { let estado: number | undefined; let mensaje = ''; api.crearPaso(1, { orden: 2, instruccion: 'B', esCritico: false }).subscribe({ error: e => { estado = e.status; mensaje = e.message; } }); http.expectOne('/api/procedimientos/1/pasos').flush({ message: 'Orden ocupado' }, { status: 409, statusText: 'Conflict' }); expect(estado).toBe(409); expect(mensaje).toBe('Orden ocupado'); http.expectNone('/api/procedimientos/1/pasos'); });
});
