import { RespaldoDepartamentosLocalService } from './respaldo-departamentos-local.service';
describe('Respaldo de departamentos locales', () => {
  it('inspecciona sin borrar ni mezclar otros catálogos', () => {
    const local = { 'dimabug.departamentos.detalle.v1': '{"1":{"nombre":"Local"}}', 'dimabug.catalogos.v2': '{"departamentos":[{"id":-1}],"pruebas":[{"id":9}]}' };
    spyOn(localStorage, 'getItem').and.callFake(k => (local as Record<string,string>)[k] ?? null);
    spyOn(sessionStorage, 'getItem').and.returnValue(null); const borrar = spyOn(localStorage, 'removeItem'); const escribir = spyOn(localStorage, 'setItem');
    const datos = new RespaldoDepartamentosLocalService().inspeccionar(); expect(datos.length).toBe(2); expect(JSON.stringify(datos)).not.toContain('pruebas');
    expect(borrar).not.toHaveBeenCalled(); expect(escribir).not.toHaveBeenCalled();
  });
  it('conserva datos ilegibles para revisión sin importarlos', () => {
    spyOn(localStorage, 'getItem').and.returnValue(null); spyOn(sessionStorage, 'getItem').and.callFake(k => k.endsWith('detalle.v1') ? 'texto antiguo' : null);
    expect(new RespaldoDepartamentosLocalService().inspeccionar()[0].datos).toBe('texto antiguo');
  });
});
