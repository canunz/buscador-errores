import { spawn } from 'node:child_process';
import { join } from 'node:path';
import { LIMITES_OCR } from '../../../src/app/core/ocr-compartido/limites';
import { dimensionesImagenOcr } from '../../../src/app/core/ocr-compartido/imagen-header';
import type { ExtraccionErrorOcr } from '../../../src/app/core/ocr-compartido/extraccion-error.model';

export type MimeOcr = 'image/png' | 'image/jpeg' | 'image/webp';
export interface ResultadoNodeOcr { texto: string; confianza: number; extraccion: ExtraccionErrorOcr; }
export type RespuestaProcesoOcr = { ok: true; resultado: ResultadoNodeOcr } | { ok: false; error: string };

/** No URLs ni rutas: el motor solo recibe bytes ya limitados, nunca instrucciones. */
export async function reconocerBytes(bytes: Uint8Array, mime: MimeOcr): Promise<ResultadoNodeOcr> {
  if (!['image/png','image/jpeg','image/webp'].includes(mime))
    throw new Error('Selecciona una imagen PNG, JPEG o WebP.');
  if (!bytes.byteLength || bytes.byteLength > LIMITES_OCR.bytes)
    throw new Error('Selecciona una imagen de hasta 5 MB que no esté vacía.');
  dimensionesImagenOcr(bytes,mime);
  // El proceso se puede terminar incluso durante decodificacion o inicializacion WASM.
  const env: NodeJS.ProcessEnv = {};
  for (const name of ['SystemRoot','WINDIR','SystemDrive','TEMP','TMP'])
    if (process.env[name]) env[name] = process.env[name];
  const child = spawn(process.execPath, ['--require',join(__dirname,'sin-red.js'),join(__dirname,'worker.js')], {
    env, serialization:'advanced', stdio:['ignore','ignore','ignore','ipc'],
    windowsHide:true,
  });
  return new Promise((resolve,reject) => {
    let terminado = false;
    const finalizar = (error?: Error, resultado?: ResultadoNodeOcr): void => {
      if (terminado) return;
      terminado = true; clearTimeout(timer); child.removeAllListeners(); child.kill();
      if (error) reject(error); else resolve(resultado!);
    };
    const timer = setTimeout(() => finalizar(new Error('El reconocimiento tardó demasiado. Recorta la imagen o intenta nuevamente.')), LIMITES_OCR.plazoMs);
    child.once('error', () => finalizar(new Error('No fue posible iniciar el reconocimiento.')));
    child.once('exit', () => finalizar(new Error('No fue posible reconocer el texto. Intenta con otra imagen o escribe tu consulta.')));
    child.once('message', (value: RespuestaProcesoOcr) => {
      if (value.ok) finalizar(undefined,value.resultado);
      else finalizar(new Error(value.error));
    });
    child.send({bytes:Buffer.from(bytes),mime}, error => {
      if (error) finalizar(new Error('No fue posible procesar la imagen. Intenta nuevamente.'));
    });
  });
}
