import { Injectable, InjectionToken, inject, OnDestroy } from '@angular/core';
import { LIMITES_OCR, ResultadoOcr, RespuestaWorkerOcr } from '../models/ocr.model';
import { prepararImagenOcr } from '../material/imagen-ocr';
import { normalizarTextoOcr } from '../ocr-compartido/texto';

export const CREAR_WORKER_OCR = new InjectionToken<() => Worker>('CREAR_WORKER_OCR', {
  providedIn:'root', factory: () => () => new Worker(new URL('./ocr.worker', import.meta.url), {type:'module'}),
});
export const PREPARAR_IMAGEN_OCR = new InjectionToken('PREPARAR_IMAGEN_OCR', {
  providedIn:'root', factory: () => prepararImagenOcr,
});
export class OcrCancelado extends Error {}

@Injectable()
export class OcrService implements OnDestroy {
  private readonly crear = inject(CREAR_WORKER_OCR);
  private readonly preparar = inject(PREPARAR_IMAGEN_OCR);
  private worker?: Worker;
  private rechazar?: (error: Error) => void;
  private timer?: ReturnType<typeof setTimeout>;

  reconocer(archivo: File, progreso: (valor: number) => void): Promise<ResultadoOcr> {
    this.cancelar();
    return new Promise<ResultadoOcr>((resolve,reject) => {
      const vigente = () => this.rechazar === rechazar;
      const rechazar = (error: Error) => { if (!vigente()) return; this.limpiar(true); reject(error); };
      this.rechazar = rechazar;
      this.timer = setTimeout(() => rechazar(new Error('El reconocimiento tardó demasiado. Recorta la imagen o intenta nuevamente.')), LIMITES_OCR.plazoMs);
      void this.preparar(archivo).then(imagen => {
        if (!vigente()) return;
        try {
          this.worker ??= this.crear();
          this.worker.onmessage = ({data}: MessageEvent<RespuestaWorkerOcr>) => {
            if (!vigente()) return;
            if (data.tipo === 'progreso') { progreso(Math.max(0,Math.min(100,data.progreso))); return; }
            if (data.tipo === 'error') { rechazar(new Error('No fue posible reconocer el texto. Intenta con otra imagen o escribe tu consulta.')); return; }
            let texto: string;
            try { texto = normalizarTextoOcr(data.texto); }
            catch (error) { rechazar(error as Error); return; }
            this.limpiar(false); resolve({texto,confianza:data.confianza});
          };
          this.worker.onerror = event => { event.preventDefault(); rechazar(new Error('No fue posible iniciar el reconocimiento. Comprueba la conexión o escribe tu consulta.')); };
          this.worker.onmessageerror = () => rechazar(new Error('No fue posible procesar la imagen. Intenta nuevamente.'));
          this.worker.postMessage({imagen,recursos:new URL('ocr/v7/',document.baseURI).href});
          progreso(0);
        } catch { rechazar(new Error('Este navegador no permite iniciar el reconocimiento. Puedes escribir tu consulta.')); }
      }).catch(error => rechazar(error instanceof Error ? error : new Error('No fue posible leer la imagen.')));
    });
  }
  cancelar(): void { this.rechazar?.(new OcrCancelado()); }
  liberar(): void { this.cancelar(); this.limpiar(true); }
  private limpiar(terminar: boolean): void {
    clearTimeout(this.timer); this.timer = undefined; this.rechazar = undefined;
    if (this.worker) {
      this.worker.onmessage = null; this.worker.onerror = null; this.worker.onmessageerror = null;
      if (terminar) { this.worker.terminate(); this.worker = undefined; }
    }
  }
  ngOnDestroy(): void { this.liberar(); }
}
