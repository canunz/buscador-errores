import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TipoMaterial } from '../../core/models/conocimiento.model';
import {
  OrigenMaterial,
  acceptArchivoMaterial,
  admiteUploadLocal,
  esUrlDescargaAutenticada,
  muestraUrlMaterial,
  origenDesdeAdjunto,
  validarMaterialFormulario,
} from '../../core/material/material-archivo';

export interface MaterialApoyoFormValue {
  nombre: string;
  tipo: TipoMaterial;
  origen: OrigenMaterial;
  url: string;
  archivo: File | null;
}

@Component({
  selector: 'app-material-apoyo-form',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './material-apoyo-form.component.html',
  styleUrl: './material-apoyo-form.component.css',
})
export class MaterialApoyoFormComponent {
  @Input() enviando = false;
  @Input() editando = false;
  @Input() tipos: TipoMaterial[] = ['IMAGEN', 'PDF', 'VIDEO', 'ENLACE'];
  @Output() guardar = new EventEmitter<MaterialApoyoFormValue>();
  @Output() cancelar = new EventEmitter<void>();

  nombre = '';
  tipo: TipoMaterial = 'PDF';
  url = '';
  archivo: File | null = null;
  error = '';
  private urlExterna = false;
  private archivoActual = false;

  get muestraUrl(): boolean {
    return muestraUrlMaterial(this.tipo) || (this.editando && this.urlExterna && !this.archivo);
  }

  get admiteAdjunto(): boolean {
    return admiteUploadLocal(this.tipo);
  }

  get archivoObligatorio(): boolean {
    if (this.tipo === 'VIDEO') {
      return false;
    }
    return !this.editando || this.archivoActual;
  }

  get tituloAdjunto(): string {
    return this.editando ? 'Reemplazar archivo' : 'Adjuntar archivo';
  }

  get accept(): string {
    return acceptArchivoMaterial(this.tipo);
  }

  get pistaAdjunto(): string {
    if (this.editando && !this.archivoActual) {
      return 'Opcional: si eliges uno, reemplaza al enlace actual.';
    }
    if (this.editando) {
      return 'Elige el nuevo archivo; el actual se reemplaza al guardar.';
    }
    if (this.tipo === 'PDF') {
      return 'PDF · máximo 10 MiB';
    }
    if (this.tipo === 'IMAGEN') {
      return 'PNG, JPEG o WEBP · máximo 10 MiB';
    }
    return 'MP4, WEBM o MOV · máximo 10 MiB';
  }

  reset(inicial?: Partial<MaterialApoyoFormValue>): void {
    this.nombre = inicial?.nombre ?? '';
    this.tipo = inicial?.tipo ?? 'PDF';
    this.url = inicial?.url ?? '';
    this.archivo = inicial?.archivo ?? null;
    this.error = '';
    this.archivoActual = esUrlDescargaAutenticada(this.url);
    this.urlExterna = !!this.url && !this.archivoActual;
    if (this.archivoActual) {
      this.url = '';
    }
    if (!admiteUploadLocal(this.tipo)) {
      this.archivo = null;
    }
  }

  etiquetaTipo(tipo: TipoMaterial): string {
    if (tipo === 'IMAGEN') return 'Imagen';
    if (tipo === 'PDF') return 'PDF';
    if (tipo === 'VIDEO') return 'Video';
    return 'Enlace';
  }

  cambiarTipo(tipo: TipoMaterial): void {
    this.tipo = tipo;
    this.error = '';
    if (!admiteUploadLocal(tipo)) {
      this.archivo = null;
    }
  }

  elegirArchivo(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.archivo = input.files?.[0] ?? null;
  }

  enviar(): void {
    const valor: MaterialApoyoFormValue = {
      nombre: this.nombre,
      tipo: this.tipo,
      origen: origenDesdeAdjunto(this.tipo, this.archivo),
      url: this.url,
      archivo: this.archivo,
    };
    const error = this.editando && !this.archivo ? this.validarEdicionSinArchivo() : validarMaterialFormulario(valor);
    if (error) {
      this.error = error;
      return;
    }
    this.error = '';
    this.guardar.emit(valor);
  }

  private validarEdicionSinArchivo(): string | null {
    const nombre = this.nombre.trim();
    if (!nombre) {
      return 'El nombre es obligatorio.';
    }
    if (nombre.length > 150) {
      return 'El nombre no puede superar 150 caracteres.';
    }
    if (this.archivoActual && this.tipo !== 'ENLACE') {
      return 'Adjunte el nuevo archivo para reemplazar el actual.';
    }
    if (this.muestraUrl && !this.url.trim()) {
      return this.tipo === 'VIDEO' ? 'Indique una URL o adjunte un video.' : 'La URL es obligatoria.';
    }
    return null;
  }
}
