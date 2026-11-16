import { Component, DestroyRef, HostListener, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { LowerCasePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable, Subject, catchError, debounceTime, distinctUntilChanged, map, of, switchMap } from 'rxjs';
import { esAdministrador, esStaff, iniciales } from '../../core/models/usuario.model';
import {
  ConocimientoSugerido,
  ESTADO_INFO,
  Etiqueta,
  EstadoTicket,
  PRIORIDADES_TICKET,
  PRIORIDAD_INFO,
  PrioridadTicket,
  AdjuntoTicket,
  Referencia,
  TicketDetalle,
  colorTextoEtiqueta,
  esFinal,
  fechaLarga,
  fechaRelativa,
} from '../../core/models/ticket.model';
import { AuthService } from '../../core/services/auth.service';
import { EtiquetaService } from '../../core/services/etiqueta.service';
import { TicketAvisosService } from '../../core/services/ticket-avisos.service';
import { GaleriaAdjuntosComponent } from './adjuntos/galeria-adjuntos.component';
import { SelectorAdjuntosComponent } from './adjuntos/selector-adjuntos.component';
import { LoadingModalComponent } from '../../shared/ui/loading-modal.component';
import { TicketService } from '../../core/services/ticket.service';
import { UsuarioService } from '../../core/services/usuario.service';

type Dialogo = 'resolver' | 'espera' | 'cancelar' | 'reabrir' | 'cerrar' | 'conocimiento' | 'prioridad' | null;

@Component({
  selector: 'app-detalle-ticket',
  standalone: true,
  imports: [FormsModule, RouterLink, LowerCasePipe, SelectorAdjuntosComponent, GaleriaAdjuntosComponent, LoadingModalComponent],
  templateUrl: './detalle-ticket.component.html',
  styleUrls: ['./tickets-comun.css', './detalle-ticket.component.css'],
})
export class DetalleTicketComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly tickets = inject(TicketService);
  private readonly avisos = inject(TicketAvisosService);
  private readonly etiquetasApi = inject(EtiquetaService);
  private readonly usuariosApi = inject(UsuarioService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly busquedaConocimiento$ = new Subject<string>();

  readonly estadoInfo = ESTADO_INFO;
  readonly prioridadInfo = PRIORIDAD_INFO;
  readonly colorTexto = colorTextoEtiqueta;
  readonly relativa = fechaRelativa;
  readonly larga = fechaLarga;
  readonly iniciales = iniciales;
  readonly maxComentario = 1000;

  readonly esStaff = this.auth.isStaff();
  readonly esAdmin = this.auth.isAdmin();
  /** Soporte atiende; el administrador solo supervisa (ve todo, sin acciones). */
  readonly esSoporte = this.esStaff && !this.esAdmin;
  readonly miId = this.auth.usuario()?.usuarioId ?? null;
  readonly volverA = this.esStaff ? '/tickets/bandeja' : '/tickets';

  ticket: TicketDetalle | null = null;
  cargando = true;
  /** La cabecera puede salir de la lista; descripción, mensajes e historial solo con el detalle real. */
  cuerpoListo = false;
  procesando = false;
  error = '';
  aviso = '';

  // Comentarios
  textoComentario = '';
  comentarioInterno = false;

  // Diálogos
  dialogo: Dialogo = null;
  motivo = '';
  solucion = '';
  textoConocimiento = '';
  sugerenciasConocimiento: ConocimientoSugerido[] = [];
  conocimientoElegido: ConocimientoSugerido | null = null;

  // Asignación (admin)
  tecnicos: Referencia[] = [];
  tecnicoElegido: number | null = null;

  // Etiquetas
  editandoEtiquetas = false;
  catalogoEtiquetas: Etiqueta[] = [];
  nuevaEtiqueta = '';
  creandoEtiqueta = false;
  /** Colores que se reparten solos a las etiquetas nuevas. */
  private readonly paleta = ['#1E4FD6', '#16A34A', '#DB2777', '#0D9488', '#7C3AED', '#F59E0B', '#0EA5E9', '#E53935', '#475569', '#F4511E'];
  etiquetasElegidas = new Set<number>();

  ngOnInit(): void {
    if (this.route.snapshot.queryParamMap.get('creado')) {
      this.aviso = 'Ticket creado. Soporte lo revisará y te responderá por aquí.';
    }
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.dialogo = null;
      this.cargar(Number(params.get('id')));
    });

    this.busquedaConocimiento$
      .pipe(
        debounceTime(350),
        distinctUntilChanged(),
        switchMap((texto) => (texto.length < 3 ? of([]) : this.tickets.sugerencias(texto).pipe(catchError(() => of([]))))),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((lista) => (this.sugerenciasConocimiento = lista));

    if (this.esAdmin) {
      // Técnicos de soporte activos (sin administradores) para asignar o reasignar.
      this.usuariosApi.listar().subscribe({
        next: (usuarios) =>
          (this.tecnicos = usuarios
            .filter((u) => u.usuarioEstado !== false && esStaff(u) && !esAdministrador(u))
            .map((u) => ({ id: u.usuarioId, nombre: u.usuarioNombre }))),
        error: () => undefined,
      });
    }
  }

  // ------------------------------------------------------------ reglas de la vista (mismas del backend)

  get estado(): EstadoTicket | null {
    return this.ticket?.estado ?? null;
  }

  get finalizado(): boolean {
    return !!this.ticket && esFinal(this.ticket.estado);
  }

  get esSolicitante(): boolean {
    return !!this.ticket && this.ticket.solicitante?.id === this.miId;
  }

  get puedeGestionarComoSolicitante(): boolean {
    return this.esSolicitante;
  }

  get puedeTomar(): boolean {
    const activos: EstadoTicket[] = ['ABIERTO', 'EN_PROGRESO', 'EN_ESPERA'];
    return this.esSoporte && !!this.estado && activos.includes(this.estado) && this.ticket?.asignado?.id !== this.miId;
  }

  /** El administrador reparte el trabajo: asigna o reasigna tickets activos a un técnico. */
  get puedeAsignarOtro(): boolean {
    const activos: EstadoTicket[] = ['ABIERTO', 'EN_PROGRESO', 'EN_ESPERA'];
    return this.esAdmin && !!this.estado && activos.includes(this.estado);
  }

  get puedeCrearConocimiento(): boolean {
    const permitidos: EstadoTicket[] = ['EN_PROGRESO', 'EN_ESPERA', 'RESUELTO'];
    return this.esSoporte && !!this.estado && permitidos.includes(this.estado) && !this.ticket?.conocimiento;
  }

  get hayAcciones(): boolean {
    return (
      this.puedeAsignarOtro ||
      this.puedeCrearConocimiento ||
      (this.esSoporte && !this.esMio && this.estado === 'EN_PROGRESO') ||
      (this.esSoporte && !this.esMio && this.estado === 'EN_ESPERA') ||
      (this.puedeGestionarComoSolicitante && this.estado === 'ABIERTO') ||
      false
    );
  }

  /** Etapas del ticket explicadas en palabras simples (vista del solicitante). */
  get etapas(): { titulo: string; detalle: string; estado: 'hecha' | 'actual' | 'espera' | 'pendiente' }[] {
    const e = this.estado;
    const orden: Record<string, number> = { ABIERTO: 0, EN_PROGRESO: 1, EN_ESPERA: 1, RESUELTO: 2, CERRADO: 3, CANCELADO: 0 };
    const paso = e ? orden[e] : 0;
    const base = [
      { titulo: 'Recibido', detalle: 'Soporte recibió tu ticket' },
      { titulo: 'En revisión', detalle: e === 'EN_ESPERA' ? 'Esperan tu respuesta' : 'Un técnico lo está viendo' },
      { titulo: 'Solucionado', detalle: 'Te toca confirmar' },
      { titulo: 'Cerrado', detalle: 'Todo listo' },
    ];
    return base.map((etapa, i) => ({
      ...etapa,
      estado:
        e === 'CERRADO' || i < paso
          ? 'hecha'
          : i === paso
            ? e === 'EN_ESPERA'
              ? 'espera'
              : 'actual'
            : 'pendiente',
    }));
  }

  /** Qué pasa ahora con el ticket y qué tiene que hacer el solicitante. */
  get queSigue(): { titulo: string; texto: string; tono: string } | null {
    const t = this.ticket;
    if (!t) return null;
    switch (t.estado) {
      case 'ABIERTO':
        return {
          titulo: 'Tu ticket está en la fila de soporte',
          texto: 'Un técnico lo tomará pronto. No necesitas hacer nada más; te avisaremos en la campana cuando haya novedades.',
          tono: 'azul',
        };
      case 'EN_PROGRESO':
        return {
          titulo: `${t.asignado?.nombre || 'Un técnico'} está trabajando en tu problema`,
          texto: 'Si tienes un dato nuevo (una foto, un mensaje de error), escríbelo en la conversación de abajo.',
          tono: 'violeta',
        };
      case 'EN_ESPERA': {
        const motivo = [...t.historial].reverse().find((h) => h.estadoNuevo === 'EN_ESPERA')?.motivo;
        return {
          titulo: 'Soporte necesita tu respuesta',
          texto: motivo
            ? `Te pidieron: «${motivo}». Respóndelo en la conversación de abajo para que puedan seguir.`
            : 'Revisa la conversación de abajo y responde para que puedan seguir.',
          tono: 'ambar',
        };
      }
      case 'CERRADO':
        return { titulo: 'Ticket cerrado', texto: 'El problema quedó solucionado. Si vuelve a pasar, crea un ticket nuevo.', tono: 'gris' };
      case 'CANCELADO':
        return { titulo: 'Ticket cancelado', texto: 'Este ticket ya no se atenderá. Si lo necesitas, puedes crear uno nuevo.', tono: 'rojo' };
      default:
        return null;
    }
  }

  /** El usuario solo puede abrir conocimientos publicados. */
  get conocimientoNavegable(): boolean {
    return !!this.ticket?.conocimiento && (this.esStaff || this.ticket.conocimiento.estado === 'PUBLICADO');
  }

  get ubicacion(): string {
    const t = this.ticket;
    return t ? [t.hardware?.nombre, t.sistema?.nombre, t.modulo?.nombre].filter(Boolean).join(' › ') : '';
  }

  // ------------------------------------------------------------ acciones

  tomar(): void {
    if (!this.ticket || !this.miId) return;
    this.ejecutar(this.tickets.asignar(this.ticket.id, this.miId), 'Te asignaste este ticket.');
  }

  asignarTecnico(): void {
    if (!this.ticket || !this.tecnicoElegido) return;
    this.ejecutar(this.tickets.asignar(this.ticket.id, this.tecnicoElegido), 'Ticket asignado.');
    this.tecnicoElegido = null;
  }

  retomar(): void {
    if (!this.ticket) return;
    this.ejecutar(this.tickets.cambiarEstado(this.ticket.id, 'EN_PROGRESO'), 'El ticket volvió a En progreso.');
  }

  /** Archivos que se adjuntan en el mensaje o en el diálogo abierto. */
  archivosComentario: File[] = [];
  archivosDialogo: File[] = [];
  /** Texto del aviso de espera mientras se suben archivos (vacío = no se muestra). */
  subiendo = '';

  /** Archivos subidos junto a la descripción del ticket. */
  get adjuntosTicket(): AdjuntoTicket[] {
    return this.ticket?.adjuntos.filter((a) => !a.comentarioId) ?? [];
  }

  adjuntosDe(comentarioId: number): AdjuntoTicket[] {
    return this.ticket?.adjuntos.filter((a) => a.comentarioId === comentarioId) ?? [];
  }

  /** El ticket está en mis manos y en curso: muestro el panel de "siguiente paso". */
  get esMio(): boolean {
    return !!this.ticket && this.ticket.asignado?.id === this.miId;
  }

  /** Qué le toca hacer ahora a soporte con este ticket. */
  get siguientePaso(): { tono: string; titulo: string; texto: string } | null {
    const t = this.ticket;
    if (!t || !this.esStaff || this.finalizado) return null;
    const quien = t.asignado?.nombre || 'otro técnico';
    if (this.esAdmin) {
      const textos: Record<string, string> = {
        ABIERTO: 'Soporte aún no lo toma.',
        EN_PROGRESO: `Lo está atendiendo ${quien}.`,
        EN_ESPERA: `${quien} espera una respuesta del usuario.`,
        RESUELTO: 'Resuelto: falta que el usuario confirme.',
      };
      const accion = ['ABIERTO', 'EN_PROGRESO', 'EN_ESPERA'].includes(t.estado)
        ? ` Si hace falta, ${t.asignado ? 'reasígnalo' : 'asígnalo'} a un técnico desde el panel de la derecha.`
        : '';
      return { tono: 'gris', titulo: 'Modo supervisión', texto: `${textos[t.estado] ?? ''}${accion}` };
    }
    switch (t.estado) {
      case 'ABIERTO':
        return { tono: 'azul', titulo: 'Este ticket espera un técnico', texto: 'Tómalo para empezar. Pasará a «En progreso» y el usuario recibirá un aviso.' };
      case 'EN_PROGRESO':
        return this.esMio
          ? { tono: 'violeta', titulo: 'Estás atendiendo este ticket', texto: 'Cuando lo soluciones, márcalo como resuelto. Si te falta un dato, pídeselo al usuario.' }
          : { tono: 'violeta', titulo: `Lo está atendiendo ${quien}`, texto: 'Puedes dejar una nota interna o asignártelo si vas a continuar tú.' };
      case 'EN_ESPERA': {
        const motivo = [...t.historial].reverse().find((h) => h.estadoNuevo === 'EN_ESPERA')?.motivo;
        return {
          tono: 'ambar',
          titulo: 'Esperando respuesta del usuario',
          texto: motivo ? `Se le pidió: «${motivo}». Cuando responda, retoma el ticket.` : 'Cuando responda, retoma el ticket para seguir.',
        };
      }
      case 'RESUELTO':
        return this.esMio
          ? { tono: 'verde', titulo: 'Resuelto: falta la confirmación del usuario', texto: 'Si el usuario no responde, puedes cerrarlo tú.' }
          : { tono: 'verde', titulo: 'Resuelto: falta la confirmación del usuario', texto: 'El usuario debe confirmar si quedó solucionado o reabrirlo.' };
      default:
        return null;
    }
  }

  /** Al volver a la pestaña se trae el estado real del ticket (otro usuario pudo cambiarlo). */
  @HostListener('window:focus')
  alVolver(): void {
    if (!this.ticket || this.procesando) return;
    this.tickets.obtener(this.ticket.id).subscribe({ next: (t) => this.aplicar(t), error: () => undefined });
  }

  @HostListener('document:keydown.escape')
  alEscape(): void {
    this.cerrarDialogo();
  }

  /** Pone el ticket en pantalla y cierra un diálogo que ya no corresponde a su estado. */
  private aplicar(ticket: TicketDetalle): void {
    this.ticket = ticket;
    this.avisos.marcarVisto(ticket);
    const valeEn: Partial<Record<Exclude<Dialogo, null>, EstadoTicket[]>> = {
      resolver: ['EN_PROGRESO'],
      espera: ['EN_PROGRESO'],
      cancelar: ['ABIERTO'],
      reabrir: ['RESUELTO'],
      cerrar: ['RESUELTO'],
      prioridad: ['ABIERTO', 'EN_PROGRESO', 'EN_ESPERA', 'RESUELTO'],
    };
    const estados = this.dialogo ? valeEn[this.dialogo] : undefined;
    if (estados && !estados.includes(ticket.estado) && !this.procesando) {
      this.dialogo = null;
    }
  }

  // ------------------------------------------------------------ prioridad

  readonly prioridades = PRIORIDADES_TICKET;
  prioridadElegida: PrioridadTicket | null = null;

  /** Soporte y el administrador pueden corregir la prioridad mientras el ticket siga activo. */
  get puedeCambiarPrioridad(): boolean {
    return this.esStaff && !this.finalizado;
  }

  /** Cambios de prioridad en el historial: "Prioridad: MEDIA -> CRITICA. motivo". */
  cambioPrioridad(h: { estadoAnterior: EstadoTicket | null; estadoNuevo: EstadoTicket; motivo: string | null }):
    { de: string; a: string; motivo: string } | null {
    if (!h.estadoAnterior || h.estadoAnterior !== h.estadoNuevo || !h.motivo?.startsWith('Prioridad: ')) return null;
    const m = /^Prioridad: (\w+) -> (\w+)(?:\. (.*))?$/s.exec(h.motivo);
    if (!m) return null;
    const nombre = (p: string) => (this.prioridadInfo[p as PrioridadTicket]?.label ?? p).toLowerCase();
    return { de: nombre(m[1]), a: nombre(m[2]), motivo: m[3] ?? '' };
  }

  enfocarMensaje(): void {
    document.querySelector<HTMLTextAreaElement>('textarea[name="comentario"]')?.focus();
  }

  /** Publica un mensaje y le adjunta los archivos. Devuelve el ticket ya actualizado. */
  private comentarConArchivos(
    ticketId: number,
    texto: string,
    interno: boolean,
    archivos: File[],
    alPublicar?: () => void,
  ): Observable<TicketDetalle> {
    return this.tickets.comentar(ticketId, texto, interno).pipe(
      switchMap((detalle) => {
        // El mensaje ya quedó publicado: aunque un archivo falle, no se debe volver a enviar el texto.
        alPublicar?.();
        if (!archivos.length) return of(detalle);
        const mios = detalle.comentarios.filter((c) => c.autor?.id === this.miId);
        const ultimo = mios.reduce((max, c) => (c.id > max ? c.id : max), 0);
        return this.tickets.subirAdjuntos(ticketId, archivos, ultimo || null).pipe(map((final) => final ?? detalle));
      }),
    );
  }

  abrirDialogo(tipo: Exclude<Dialogo, null>): void {
    this.dialogo = tipo;
    this.error = '';
    this.motivo = '';
    this.archivosDialogo = [];
    if (tipo === 'prioridad') this.prioridadElegida = null;
    if (tipo === 'resolver' && this.ticket) {
      this.solucion = this.ticket.solucion ?? '';
      this.conocimientoElegido = null;
      this.textoConocimiento = this.ticket.titulo;
      this.busquedaConocimiento$.next(this.textoConocimiento.trim());
    }
  }

  cerrarDialogo(): void {
    if (!this.procesando) this.dialogo = null;
  }

  buscarConocimiento(texto: string): void {
    this.busquedaConocimiento$.next(texto.trim());
  }

  confirmarDialogo(): void {
    const t = this.ticket;
    if (!t || this.procesando) return;
    switch (this.dialogo) {
      case 'resolver':
        if (!this.solucion.trim()) return;
        this.subiendo = this.archivosDialogo.length ? 'Subiendo evidencia' : '';
        // Las evidencias se publican en la conversación antes de marcarlo resuelto.
        this.ejecutar(
          (this.archivosDialogo.length
            ? this.comentarConArchivos(t.id, 'Evidencia de la solución aplicada.', false, this.archivosDialogo)
            : of(t)
          ).pipe(switchMap(() => this.tickets.resolver(t.id, this.solucion, this.conocimientoElegido?.id ?? null))),
          'Ticket resuelto. El solicitante podrá confirmar la solución.',
        );
        break;
      case 'espera': {
        // Primero se publican el mensaje y los archivos; recién después cambia el estado.
        // Así, si un archivo falla, el ticket no queda a medias y se puede reintentar.
        this.subiendo = this.archivosDialogo.length ? 'Enviando archivos al usuario' : '';
        const pasarAEspera = () =>
          t.estado === 'EN_ESPERA'
            ? this.tickets.obtener(t.id)
            : this.tickets.cambiarEstado(t.id, 'EN_ESPERA', this.motivo);
        this.ejecutar(
          (this.archivosDialogo.length
            ? this.comentarConArchivos(
                t.id,
                this.motivo.trim() || 'Te dejo estos archivos para que los revises.',
                false,
                this.archivosDialogo,
              )
            : of(t)
          ).pipe(switchMap(() => pasarAEspera())),
          'Ticket en espera. Le avisamos al usuario que necesitas su respuesta.',
        );
        break;
      }
      case 'cancelar':
        this.ejecutar(this.tickets.cambiarEstado(t.id, 'CANCELADO', this.motivo), 'Ticket cancelado.');
        break;
      case 'reabrir':
        if (!this.motivo.trim()) return;
        this.ejecutar(this.tickets.cambiarEstado(t.id, 'EN_PROGRESO', this.motivo), 'Ticket reabierto. Soporte lo revisará de nuevo.');
        break;
      case 'cerrar':
        this.ejecutar(this.tickets.cambiarEstado(t.id, 'CERRADO', this.motivo), '¡Gracias! El ticket quedó cerrado.');
        break;
      case 'prioridad': {
        const nueva = this.prioridadElegida;
        if (!nueva || nueva === t.prioridad) return;
        this.ejecutar(
          this.tickets.cambiarPrioridad(t.id, nueva, this.motivo),
          `Prioridad cambiada a ${this.prioridadInfo[nueva].label.toLowerCase()}.`,
        );
        break;
      }
      case 'conocimiento':
        this.ejecutar(
          this.tickets.crearConocimiento(t.id),
          'Se creó un conocimiento en borrador con los datos del ticket. Un administrador lo publicará.',
        );
        break;
    }
  }

  enviarComentario(): void {
    const archivos = this.archivosComentario;
    const texto = this.textoComentario.trim() || (archivos.length ? 'Adjunto archivos.' : '');
    if (!this.ticket || !texto || this.procesando) return;
    this.subiendo = archivos.length ? (archivos.length === 1 ? 'Enviando mensaje y archivo' : `Enviando mensaje y ${archivos.length} archivos`) : '';
    this.ejecutar(
      this.comentarConArchivos(this.ticket.id, texto, this.esStaff && this.comentarioInterno, archivos, () => {
        this.textoComentario = '';
      }),
      archivos.length ? 'Mensaje y archivos enviados.' : '',
      () => {
        this.textoComentario = '';
        this.comentarioInterno = false;
        this.archivosComentario = [];
      },
    );
  }

  abrirEditorEtiquetas(): void {
    if (!this.ticket) return;
    this.etiquetasElegidas = new Set(this.ticket.etiquetas.map((e) => e.id));
    this.editandoEtiquetas = true;
    this.etiquetasApi.listar().subscribe({
      next: (activas) => {
        // Se muestran las activas y también las que el ticket ya tenía aunque estén desactivadas.
        const actuales = this.ticket?.etiquetas.filter((e) => !activas.some((a) => a.id === e.id)) ?? [];
        this.catalogoEtiquetas = [...activas, ...actuales];
      },
      error: (err: Error) => (this.error = err.message),
    });
  }

  alternarEtiqueta(id: number): void {
    if (this.etiquetasElegidas.has(id)) {
      this.etiquetasElegidas.delete(id);
    } else {
      this.etiquetasElegidas.add(id);
    }
  }

  /** Soporte crea la etiqueta aquí mismo; el color se elige solo y queda marcada en el ticket. */
  crearEtiqueta(): void {
    const nombre = this.nuevaEtiqueta.trim();
    if (!nombre || this.creandoEtiqueta) return;
    const existente = this.catalogoEtiquetas.find((e) => e.nombre.toLowerCase() === nombre.toLowerCase());
    if (existente) {
      this.etiquetasElegidas.add(existente.id);
      this.nuevaEtiqueta = '';
      return;
    }
    const usados = new Set(this.catalogoEtiquetas.map((e) => e.color.toUpperCase()));
    const color = this.paleta.find((c) => !usados.has(c)) ?? this.paleta[this.catalogoEtiquetas.length % this.paleta.length];
    this.creandoEtiqueta = true;
    this.error = '';
    this.etiquetasApi.crear({ nombre, color }).subscribe({
      next: (creada) => {
        this.catalogoEtiquetas = [...this.catalogoEtiquetas, creada];
        this.etiquetasElegidas.add(creada.id);
        this.nuevaEtiqueta = '';
        this.creandoEtiqueta = false;
      },
      error: (err: Error) => {
        this.error = err.message;
        this.creandoEtiqueta = false;
      },
    });
  }

  guardarEtiquetas(): void {
    if (!this.ticket) return;
    this.ejecutar(this.tickets.etiquetar(this.ticket.id, [...this.etiquetasElegidas]), 'Etiquetas actualizadas.', () => {
      this.editandoEtiquetas = false;
    });
  }

  colorHistorial(estado: EstadoTicket): string {
    return this.estadoInfo[estado].clase;
  }

  private cargar(id: number): void {
    if (!id) {
      this.error = 'El ticket solicitado no es válido.';
      this.cargando = false;
      return;
    }
    const completo = this.tickets.detalleEnCache(id);
    if (completo) {
      this.ticket = completo;
      this.cuerpoListo = true;
      this.cargando = false;
    } else {
      this.cuerpoListo = false;
      const conocido = this.tickets.todosEnCache()?.find((t) => t.id === id);
      if (conocido) {
        this.ticket = {
          ...conocido,
          descripcion: '',
          hardware: null,
          sistema: null,
          modulo: null,
          conocimiento: null,
          solucion: null,
          fechaResolucion: null,
          fechaCierre: null,
          comentarios: [],
          historial: [],
          adjuntos: [],
        };
        this.cargando = false;
      } else if (this.ticket?.id !== id) {
        this.ticket = null;
        this.cargando = true;
      }
    }
    this.tickets.obtener(id).subscribe({
      next: (ticket) => {
        this.aplicar(ticket);
        this.cuerpoListo = true;
        this.cargando = false;
      },
      error: (err: Error) => {
        this.error = err.message;
        this.cargando = false;
      },
    });
  }

  private ejecutar(peticion: Observable<TicketDetalle>, mensaje: string, alTerminar?: () => void): void {
    this.procesando = true;
    this.error = '';
    peticion.subscribe({
      next: (ticket) => {
        this.ticket = ticket;
        this.avisos.marcarVisto(ticket);
        this.procesando = false;
        this.subiendo = '';
        this.dialogo = null;
        if (mensaje) this.aviso = mensaje;
        alTerminar?.();
      },
      error: (err: Error) => {
        this.procesando = false;
        this.subiendo = '';
        this.error = err.message;
        // Si una parte alcanzó a guardarse (por ejemplo el mensaje), la pantalla se pone al día.
        if (this.ticket) {
          this.tickets.obtener(this.ticket.id).subscribe({
            next: (actual) => this.aplicar(actual),
            error: () => undefined,
          });
        }
      },
    });
  }
}
