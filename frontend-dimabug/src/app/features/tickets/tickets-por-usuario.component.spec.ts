import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { TicketsPorUsuarioComponent } from './tickets-por-usuario.component';
import { TicketService } from '../../core/services/ticket.service';

describe('Integración del panel de Catalina', () => {
  let api: jasmine.SpyObj<TicketService>;
  beforeEach(() => {
    api = jasmine.createSpyObj<TicketService>('TicketService', ['porUsuario']);
    TestBed.configureTestingModule({ imports: [TicketsPorUsuarioComponent], providers: [{ provide: TicketService, useValue: api }] });
  });
  it('conserva ranking, totales y expansión de todos los usuarios', () => {
    api.porUsuario.and.returnValue(of(Array.from({length: 8}, (_, i) => ({usuarioId:i+1,nombre:'Persona QA '+i,email:'qa'+i+'@example.invalid',total:8-i,activos:1,resueltos:7-i}))));
    const fixture = TestBed.createComponent(TicketsPorUsuarioComponent);
    fixture.detectChanges();
    const c = fixture.componentInstance;
    expect(api.porUsuario).toHaveBeenCalledTimes(1);
    expect(c.visibles().length).toBe(6);
    expect(c.totalTickets()).toBe(36);
    expect(c.totalActivos()).toBe(8);
    expect(c.totalResueltos()).toBe(28);
    expect(c.porcentajeResueltos()).toBe(78);
    const boton = fixture.nativeElement.querySelector('.tpu-ver-mas') as HTMLButtonElement;
    expect(boton).not.toBeNull();
    boton.click(); fixture.detectChanges();
    expect(c.visibles().length).toBe(8);
    expect(boton.textContent).toContain('Ver menos');
  });
  it('permite reintentar después de un error sin perder el panel', () => {
    api.porUsuario.and.returnValue(throwError(() => new Error('No disponible')));
    const fixture = TestBed.createComponent(TicketsPorUsuarioComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.error()).toBe('No disponible');
    expect(fixture.componentInstance.cargando()).toBeFalse();
    api.porUsuario.and.returnValue(of([]));
    fixture.componentInstance.cargar(); fixture.detectChanges();
    expect(api.porUsuario).toHaveBeenCalledTimes(2);
    expect(fixture.componentInstance.error()).toBe('');
    expect(fixture.componentInstance.totalTickets()).toBe(0);
  });
});
