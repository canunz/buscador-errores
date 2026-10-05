import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TipoMaterial } from '../../core/models/conocimiento.model';
import {
  OrigenMaterial,
  acceptArchivoMaterial,
  admiteUploadLocal,
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

  get muestraUrl(): boolean {
    return muestraUrlMaterial(this.tipo);
  }

  get admiteAdjunto(): boolean {
    return !this.editando && admiteUploadLocal(this.tipo);
  }

  get accept(): string {
    return acceptArchivoMaterial(this.tipo);
  }

  get pistaAdjunto(): string {
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
    const error = validarMaterialFormulario(valor);
    if (error) {
      this.error = error;
      return;
    }
    this.error = '';
    this.guardar.emit(valor);
  }
}
