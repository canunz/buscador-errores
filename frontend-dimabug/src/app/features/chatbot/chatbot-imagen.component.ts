import { Component, ElementRef, EventEmitter, Input, OnDestroy, Output, ViewChild, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { EstadoOcr } from '../../core/models/ocr.model';
import { OcrCancelado, OcrService } from '../../core/services/ocr.service';
import { ExtractorErrorOcrService } from '../../core/services/extractor-error-ocr.service';
import { CandidatoErrorOcr } from '../../core/models/extraccion-error-ocr.model';

/**
 * Pantalla de chatbot imagen.
 * Carga los datos con su servicio y maneja lo que el usuario hace en la vista.
 */
@Component({
  selector:'app-chatbot-imagen', standalone:true, imports:[ReactiveFormsModule], providers:[OcrService],
  templateUrl:'./chatbot-imagen.component.html', styleUrl:'./chatbot-imagen.component.css',
})
export class ChatbotImagenComponent implements OnDestroy {
  private readonly ocr = inject(OcrService);
  private readonly extractor = inject(ExtractorErrorOcrService);
  original = ''; candidatos: CandidatoErrorOcr[] = []; seleccionado = ''; hayMas = false;
  get avisos(): string[] { return this.candidatos.find(c=>c.id===this.seleccionado)?.avisos ?? []; }
  elegir(candidato: CandidatoErrorOcr): void { this.seleccionado=candidato.id; this.texto.setValue(candidato.texto); }
  private reiniciarExtraccion(): void { this.original='';this.candidatos=[];this.seleccionado='';this.hayMas=false; }
  @Input() buscando = false;
  @Output() buscar = new EventEmitter<string>();
  @ViewChild('modal') modal?: ElementRef<HTMLDialogElement>;
  private confirmado = false;
  abrir(): void { this.confirmado = false; this.modal?.nativeElement.showModal(); }
  cerrar(): void { this.modal?.nativeElement.close(); this.limpiar(); }
  estado: EstadoOcr = 'inicial'; nombre = ''; error = ''; progreso = 0; confianza = 100;
  readonly texto = new FormControl('', {nonNullable:true,validators:[Validators.required,Validators.maxLength(300)]});
  private version = 0;
  get ocupado(): boolean { return this.estado === 'seleccionada' || this.estado === 'procesando'; }

  async seleccionar(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const archivo = input.files?.[0]; input.value = '';
    if (!archivo) return;
    const version = ++this.version;
    this.confirmado = false;
    this.ocr.cancelar(); this.nombre = archivo.name; this.error = ''; this.texto.reset();
    this.reiniciarExtraccion();
    this.estado = 'seleccionada'; this.progreso = 0;
    try {
      const resultado = await this.ocr.reconocer(archivo, p => {
        if (version !== this.version) return;
        this.estado = 'procesando'; this.progreso = Math.max(this.progreso,Math.round(p));
      });
      if (version !== this.version) return;
      this.confianza = resultado.confianza;
      const extraccion=this.extractor.extraer(resultado.texto);
      this.original=extraccion.original;this.candidatos=extraccion.candidatos;this.hayMas=extraccion.hayMas;
      if(this.candidatos.length) this.elegir(this.candidatos[0]);
      this.estado = resultado.texto ? 'reconocido' : 'sin_texto';
    } catch (e) {
      if (version !== this.version || e instanceof OcrCancelado) return;
      this.estado = 'error'; this.error = e instanceof Error ? e.message : 'No fue posible reconocer el texto.';
    }
  }
  confirmar(): void {
    const texto = this.texto.value.trim();
    if (this.confirmado || this.buscando || this.ocupado || !texto || texto.length > 300) return;
    this.confirmado = true;
    this.buscar.emit(texto);
  }
  limpiar(): void {
    this.version++; this.ocr.liberar(); this.estado = 'inicial'; this.nombre = ''; this.error = ''; this.texto.reset();
    this.reiniciarExtraccion();
  }
  ngOnDestroy(): void { this.version++; this.ocr.cancelar(); }
}
