import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { OrganizacionService } from './organizacion.service';
import { RespaldoPruebasLocalService } from './respaldo-pruebas-local.service';
describe('QA013 fuente HTTP y respaldo de Pruebas', () => {
 let org: OrganizacionService; let http: HttpTestingController;
 beforeEach(() => { TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }); org = TestBed.inject(OrganizacionService); http = TestBed.inject(HttpTestingController); });
 afterEach(() => http.verify());
 it('ignora flags locales y no borra ni escribe storage', () => { const lectura = spyOn(sessionStorage, 'getItem').and.returnValue('{"1":false}'); const escribir = spyOn(sessionStorage, 'setItem'); const borrar = spyOn(sessionStorage, 'removeItem');
   org.listarPruebasDetalle(true).subscribe(p => expect(p[0].activo).toBeTrue()); http.expectOne('/api/pruebas').flush([{ id: 1, descripcion: 'Ping', resultadoEsperado: 'OK' }]); expect(lectura).not.toHaveBeenCalled(); expect(escribir).not.toHaveBeenCalled(); expect(borrar).not.toHaveBeenCalled(); });
 it('un error HTTP no se reemplaza por catálogo local antiguo', () => { let error = ''; spyOn(sessionStorage, 'getItem').and.returnValue('[{"id":1}]'); org.listarPruebas().subscribe({ error: e => error = e.message }); http.expectOne('/api/pruebas').flush({ message: 'No disponible' }, { status: 503, statusText: 'Unavailable' }); expect(error).not.toBe(''); });
 it('invalidar fuerza nueva consulta', () => { org.listarPruebas().subscribe(); http.expectOne('/api/pruebas').flush([]); org.invalidarPruebas(); let resultado: unknown; org.listarPruebas().subscribe(p => resultado = p); http.expectOne('/api/pruebas').flush([]); expect(resultado).toEqual([]); });
 it('respalda listas, estados y ocultos sin otras colecciones', () => { spyOn(sessionStorage, 'getItem').and.callFake(k => k === 'dimabug.pruebas.activo.v1' ? '{"1":false}' : null); spyOn(localStorage, 'getItem').and.callFake(k => k === 'dimabug.catalogos.v2' ? '{"pruebas":[{"id":-1}],"departamentos":[{"id":2}]}' : null); const guardar = spyOn(localStorage, 'setItem'); const datos = new RespaldoPruebasLocalService().inspeccionar(); expect(datos.length).toBe(2); expect(JSON.stringify(datos)).not.toContain('departamentos'); expect(guardar).not.toHaveBeenCalled(); });
});
