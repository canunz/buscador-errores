import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { ConocimientoFormComponent } from './conocimiento-form.component';
import { ClasificacionService } from '../../core/services/clasificacion.service';
import { ConocimientoService } from '../../core/services/conocimiento.service';

describe('Formulario de conocimiento y encabezado', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => null } } } },
      { provide: ConocimientoService, useValue: {} },
      { provide: ClasificacionService, useValue: {
        snapshotHardware: () => [], snapshotFrecuencias: () => ({ items: [], disponible: true }),
        listarHardware: () => of([{ id: 4, nombre: 'POS real' }]),
        listarFrecuencias: () => of({ items: [{ id: 2, nombre: 'Diario' }], disponible: true }),
        precargarClasificacion: () => undefined,
      } },
    ] });
  });
  it('presenta las opciones de los catálogos consultados', () => {
    const fixture = TestBed.createComponent(ConocimientoFormComponent); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('select[formControlName="hardwareId"]').textContent).toContain('POS real');
    expect(fixture.nativeElement.querySelector('select[formControlName="frecuenciaId"]').textContent).toContain('Diario');
  });
  for (const ancho of [360, 1024]) {
    it(`texto informativo y Volver no se superponen con contenedor de ${ancho}px`, () => {
      const fixture = TestBed.createComponent(ConocimientoFormComponent); fixture.detectChanges();
      const host: HTMLElement = fixture.nativeElement;
      host.style.display = 'block'; host.style.width = `${ancho}px`;
      const texto = host.querySelector('.franja > div > span')!.getBoundingClientRect();
      const volver = host.querySelector('.btn-ghost')!.getBoundingClientRect();
      expect(texto.width).toBeGreaterThan(0);
      const cruza = texto.left < volver.right && texto.right > volver.left && texto.top < volver.bottom && texto.bottom > volver.top;
      expect(cruza).toBeFalse();
    });
  }
});
