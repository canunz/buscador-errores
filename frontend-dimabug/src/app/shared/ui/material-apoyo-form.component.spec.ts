import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MaterialApoyoFormComponent } from './material-apoyo-form.component';

describe('MaterialApoyoFormComponent', () => {
  let fixture: ComponentFixture<MaterialApoyoFormComponent>;
  let component: MaterialApoyoFormComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MaterialApoyoFormComponent],
    }).compileComponents();
    fixture = TestBed.createComponent(MaterialApoyoFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('en PDF habilita adjuntar y oculta URL', () => {
    component.cambiarTipo('PDF');
    fixture.detectChanges();
    const boton = fixture.nativeElement.querySelector('[data-testid="campo-archivo"]') as HTMLButtonElement;
    expect(boton.disabled).toBeFalse();
    expect(fixture.nativeElement.querySelector('[data-testid="campo-url"]')).toBeFalsy();
  });

  it('en VIDEO muestra URL y deja adjuntar habilitado', () => {
    component.cambiarTipo('VIDEO');
    fixture.detectChanges();
    const boton = fixture.nativeElement.querySelector('[data-testid="campo-archivo"]') as HTMLButtonElement;
    expect(fixture.nativeElement.querySelector('[data-testid="campo-url"]')).toBeTruthy();
    expect(boton.disabled).toBeFalse();
  });

  it('en ENLACE muestra solo URL y oculta adjuntar', () => {
    component.cambiarTipo('ENLACE');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="campo-url"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="campo-archivo"]')).toBeFalsy();
  });

  it('emite creación de enlace existente cuando el formulario es válido', () => {
    const spy = jasmine.createSpy('guardar');
    component.guardar.subscribe(spy);
    component.reset({ nombre: 'Manual', tipo: 'ENLACE', url: 'https://ejemplo.cl' });
    component.enviar();
    expect(spy).toHaveBeenCalledWith(
      jasmine.objectContaining({
        origen: 'enlace',
        nombre: 'Manual',
        tipo: 'ENLACE',
        url: 'https://ejemplo.cl',
      }),
    );
  });

  it('deshabilita envío mientras se envía', () => {
    component.enviando = true;
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="enviar"]').disabled).toBeTrue();
  });
});
