import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import {
  ESTADO_INFO,
  EstadoTicket,
  PRIORIDAD_INFO,
  TicketResumen,
  colorTextoEtiqueta,
  fechaCorta,
  fechaRelativa,
} from '../../core/models/ticket.model';
import { TicketService } from '../../core/services/ticket.service';

interface PestanaEstado {
  estado: EstadoTicket | null;
  label: string;
}

@Component({
  selector: 'app-mis-tickets',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './mis-tickets.component.html',
  styleUrls: ['./tickets-comun.css', './mis-tickets.component.css'],
})
export class MisTicketsComponent implements OnInit {
  private readonly tickets = inject(TicketService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly busqueda$ = new Subject<string>();

  readonly estadoInfo = ESTADO_INFO;
  readonly prioridadInfo = PRIORIDAD_INFO;
  readonly colorTexto = colorTextoEtiqueta;
  readonly relativa = fechaRelativa;
  readonly corta = fechaCorta;
  readonly pestanas: PestanaEstado[] = [
    { estado: null, label: 'Todos' },
    { estado: 'ABIERTO', label: 'Abiertos' },
    { estado: 'EN_PROGRESO', label: 'En progreso' },
    { estado: 'EN_ESPERA', label: 'En espera' },
    { estado: 'RESUELTO', label: 'Resueltos' },
    { estado: 'CERRADO', label: 'Cerrados' },
    { estado: 'CANCELADO', label: 'Cancelados' },
  ];

  items: TicketResumen[] = [];
  estado: EstadoTicket | null = null;
  texto = '';
  pagina = 0;
  readonly tamano = 10;
  total = 0;
  totalPaginas = 0;
  cargando = true;
  error = '';
  porConfirmar = 0;
  private todos: TicketResumen[] = [];

  ngOnInit(): void {
    // Filtro que llega desde el panel de Inicio (?estado=...).
    const estadoParam = this.route.snapshot.queryParamMap.get('estado') as EstadoTicket | null;
    if (estadoParam && this.pestanas.some((p) => p.estado === estadoParam)) this.estado = estadoParam;

    this.busqueda$
      .pipe(debounceTime(350), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.pagina = 0;
        this.aplicar();
      });
    this.cargar();
  }

  buscar(texto: string): void {
    this.busqueda$.next(texto.trim());
  }

  elegirEstado(estado: EstadoTicket | null): void {
    if (this.estado === estado) return;
    this.estado = estado;
    this.pagina = 0;
    this.aplicar();
  }

  irAPagina(pagina: number): void {
    if (pagina < 0 || pagina >= this.totalPaginas) return;
    this.pagina = pagina;
    this.aplicar();
  }

  abrir(ticket: TicketResumen): void {
    this.router.navigate(['/tickets', ticket.id]);
  }

  cargar(): void {
    this.cargando = this.items.length === 0;
    this.error = '';
    this.tickets.todos().subscribe({
      next: (lista) => {
        this.todos = lista;
        this.porConfirmar = lista.filter((t) => t.estado === 'RESUELTO').length;
        this.aplicar();
      },
      error: (err: Error) => {
        if (this.items.length) return;
        this.error = err.message;
        this.cargando = false;
      },
    });
  }

  private aplicar(): void {
    const texto = this.texto.trim().toLowerCase();
    const filtrados = this.todos.filter((t) => {
      if (this.estado && t.estado !== this.estado) return false;
      if (!texto) return true;
      return `${t.codigo} ${t.titulo}`.toLowerCase().includes(texto);
    });
    this.total = filtrados.length;
    this.totalPaginas = Math.max(1, Math.ceil(filtrados.length / this.tamano) || 1);
    if (this.pagina >= this.totalPaginas) this.pagina = Math.max(0, this.totalPaginas - 1);
    this.items = filtrados.slice(this.pagina * this.tamano, this.pagina * this.tamano + this.tamano);
    this.cargando = false;
  }
}
