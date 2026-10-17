import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { HardwareComponent } from './hardware.component';
import { AuthService } from '../../core/services/auth.service';
import { ClasificacionService } from '../../core/services/clasificacion.service';

describe('QA-010 acceso a administrar Hardware', () => {
  let admin = true;
  beforeEach(() => {
    admin = true;
    TestBed.configureTestingModule({ providers: [provideRouter([]),
      { provide: AuthService, useValue: { isAdmin: () => admin } },
      { provide: ClasificacionService, useValue: { hardwareEnSesion: () => [], listarHardwareDetalle: () => of([]) } },
    ] });
  });
  it('administrador ve acción separada y explicación de asociaciones', () => {
    const f = TestBed.createComponent(HardwareComponent); f.detectChanges();
    const enlace = f.nativeElement.querySelector('.hw-admin-link');
    expect(enlace.getAttribute('href')).toBe('/admin/catalogo/hardware');
    expect(enlace.textContent).toContain('Administrar Hardware');
    expect(f.nativeElement.querySelector('.hw-admin-actions').textContent).toContain('asociar Sistemas');
  });
  it('técnico conserva consulta y no ve administración', () => {
    admin = false; const f = TestBed.createComponent(HardwareComponent); f.detectChanges();
    expect(f.nativeElement.querySelector('.hw-admin-link')).toBeNull();
    expect(f.nativeElement.querySelector('h1').textContent).toContain('Hardware');
  });
});
