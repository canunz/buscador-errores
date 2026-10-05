import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { TipoMaterial } from '../models/conocimiento.model';

export const MAX_MATERIAL_BYTES = 10 * 1024 * 1024;
export const MIME_PDF = 'application/pdf';
export const MIME_IMAGEN = ['image/png', 'image/jpeg', 'image/webp'] as const;
export const MIME_VIDEO = ['video/mp4', 'video/webm', 'video/quicktime'] as const;

export type OrigenMaterial = 'archivo' | 'enlace';

export const TIPOS_UPLOAD_LOCAL: readonly TipoMaterial[] = ['PDF', 'IMAGEN', 'VIDEO'];

export function admiteUploadLocal(tipo: TipoMaterial): boolean {
  return tipo === 'PDF' || tipo === 'IMAGEN' || tipo === 'VIDEO';
}

export function muestraUrlMaterial(tipo: TipoMaterial): boolean {
  return tipo === 'ENLACE' || tipo === 'VIDEO';
}

export function acceptArchivoMaterial(tipo: TipoMaterial): string {
  if (tipo === 'PDF') {
    return 'application/pdf';
  }
  if (tipo === 'IMAGEN') {
    return 'image/png,image/jpeg,image/webp';
  }
  return 'video/mp4,video/webm,video/quicktime';
}

export function origenDesdeAdjunto(tipo: TipoMaterial, archivo: File | null | undefined): OrigenMaterial {
  return archivo && admiteUploadLocal(tipo) ? 'archivo' : 'enlace';
}

export function esUrlDescargaAutenticada(url: string | null | undefined): boolean {
  if (!url) {
    return false;
  }
  if (url.startsWith('file:materiales/')) {
    return false;
  }
  return /\/api\/(?:conocimientos\/\d+\/materiales|procedimientos\/\d+\/pasos\/\d+\/materiales)\/\d+\/archivo(?:\?|$)/.test(
    url,
  );
}

export function mimeCompatibleConTipo(tipo: TipoMaterial, mime: string | undefined): boolean {
  if (!mime) {
    return false;
  }
  if (tipo === 'PDF') {
    return mime === MIME_PDF;
  }
  if (tipo === 'IMAGEN') {
    return (MIME_IMAGEN as readonly string[]).includes(mime);
  }
  if (tipo === 'VIDEO') {
    return (MIME_VIDEO as readonly string[]).includes(mime);
  }
  return false;
}

export function validarMaterialFormulario(input: {
  nombre: string;
  tipo: TipoMaterial;
  origen: OrigenMaterial;
  url?: string;
  archivo?: File | null;
}): string | null {
  const nombre = (input.nombre || '').trim();
  if (!nombre) {
    return 'El nombre es obligatorio.';
  }
  if (nombre.length > 150) {
    return 'El nombre no puede superar 150 caracteres.';
  }
  const archivo = input.archivo;
  const url = (input.url || '').trim();

  if (input.tipo === 'ENLACE') {
    return url ? null : 'La URL es obligatoria.';
  }

  if (input.tipo === 'VIDEO') {
    if (archivo) {
      return validarArchivoMaterial(input.tipo, archivo);
    }
    return url ? null : 'Indique una URL o adjunte un video.';
  }

  if (!archivo) {
    return 'Adjunte un archivo.';
  }
  return validarArchivoMaterial(input.tipo, archivo);
}

function validarArchivoMaterial(tipo: TipoMaterial, archivo: File): string | null {
  if (archivo.size > MAX_MATERIAL_BYTES) {
    return 'El archivo no puede superar 10 MiB.';
  }
  if (!mimeCompatibleConTipo(tipo, archivo.type)) {
    if (tipo === 'PDF') {
      return 'El PDF debe ser application/pdf.';
    }
    if (tipo === 'IMAGEN') {
      return 'La imagen debe ser PNG, JPEG o WEBP.';
    }
    return 'El video debe ser MP4, WEBM o QuickTime.';
  }
  return null;
}

export function crearFormDataMaterial(nombre: string, tipo: TipoMaterial, archivo: File): FormData {
  const data = new FormData();
  data.append('nombre', nombre.trim());
  data.append('tipo', tipo);
  data.append('archivo', archivo, archivo.name);
  return data;
}

export function nombreDescarga(
  disposition: string | null,
  fallback: string,
  tipo: TipoMaterial,
): string {
  const match = disposition?.match(/filename\*?=(?:UTF-8'')?"?([^";]+)"?/i);
  if (match?.[1]) {
    return decodeURIComponent(match[1]);
  }
  const base = fallback.replace(/[\\/:*?"<>|]+/g, '_').trim() || 'material';
  if (/\.(pdf|png|jpe?g|webp)$/i.test(base)) {
    return base;
  }
  return tipo === 'PDF' ? `${base}.pdf` : `${base}.bin`;
}

export function iniciarDescargaBlob(blob: Blob, filename: string): void {
  const objectUrl = URL.createObjectURL(blob);
  try {
    const link = document.createElement('a');
    link.href = objectUrl;
    link.download = filename;
    link.rel = 'noopener';
    document.body.appendChild(link);
    link.click();
    link.remove();
  } finally {
    URL.revokeObjectURL(objectUrl);
  }
}

export function descargarMaterialAutenticado(
  http: HttpClient,
  url: string,
  nombre: string,
  tipo: TipoMaterial,
): Observable<void> {
  if (esUrlDescargaAutenticada(url) === false || url.startsWith('file:')) {
    throw new Error('Este material no tiene una descarga autenticada.');
  }
  return http.get(url, { responseType: 'blob', observe: 'response' }).pipe(
    map((res: HttpResponse<Blob>) => {
      if (!res.body) {
        throw new Error('No fue posible descargar el archivo.');
      }
      iniciarDescargaBlob(res.body, nombreDescarga(res.headers.get('Content-Disposition'), nombre, tipo));
    }),
  );
}
