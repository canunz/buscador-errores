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
  it('limita el título a 150 tanto en control como en input', () => {
    const fixture = TestBed.createComponent(ConocimientoFormComponent); fixture.detectChanges();
    const control = fixture.componentInstance.form.controls.titulo;
    control.setValue('T'.repeat(150)); expect(control.valid).toBeTrue();
    control.setValue('T'.repeat(151)); expect(control.hasError('maxlength')).toBeTrue();
    expect(fixture.nativeElement.querySelector('[formControlName="titulo"]').maxLength).toBe(150);
  });

  it('comentario opcional acepta 500 y rechaza 501 caracteres', () => {
    const f = TestBed.createComponent(ConocimientoFormComponent); f.detectChanges();
    const c = f.componentInstance.form.controls.comentario;
    c.setValue(''); expect(c.valid).toBeTrue();
    c.setValue('C'.repeat(500)); expect(c.valid).toBeTrue();
    c.setValue('C'.repeat(501)); expect(c.hasError('maxlength')).toBeTrue();
    expect(f.nativeElement.querySelector('[formControlName="comentario"]').maxLength).toBe(500);
  });
});
