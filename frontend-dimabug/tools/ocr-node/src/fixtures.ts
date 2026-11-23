import { open, lstat, realpath } from 'node:fs/promises';
import { basename, join, resolve } from 'node:path';
import { createHash } from 'node:crypto';
import { LIMITES_OCR } from '../../../src/app/core/ocr-compartido/limites';
import { FIXTURES_AUTORIZADOS } from '../../../src/app/core/ocr-compartido/pruebas/imagenes';

/** El prototipo solo abre fixtures sinteticos conocidos por nombre y SHA-256. */
export async function leerFixtureAutorizado(nombre: string): Promise<Buffer> {
  if (basename(nombre)!==nombre || !Object.hasOwn(FIXTURES_AUTORIZADOS,nombre))
    throw new Error('Selecciona exclusivamente una imagen sintética autorizada.');
  // Layout fijado por build.cjs: dist/tools/ocr-node/src, nunca cwd del usuario.
  const frontend = resolve(__dirname,'../../../../../..');
  const folder = await realpath(join(frontend,'src/test-assets/ocr'));
  const file = join(folder,nombre);
  const stat = await lstat(file);
  if (!stat.isFile() || stat.isSymbolicLink() || !stat.size || stat.size>LIMITES_OCR.bytes || await realpath(file)!==file)
    throw new Error('La imagen de prueba no cumple los controles locales.');
  const handle = await open(file,'r');
  try {
    const bytes=Buffer.alloc(LIMITES_OCR.bytes+1);
    const {bytesRead}=await handle.read(bytes,0,bytes.length,0);
    if (!bytesRead || bytesRead>LIMITES_OCR.bytes) throw new Error('Imagen fuera de límites.');
    const data=bytes.subarray(0,bytesRead);
    if(createHash('sha256').update(data).digest('hex')!==FIXTURES_AUTORIZADOS[nombre].sha256)
      throw new Error('La imagen no coincide con el fixture autorizado.');
    return data;
  } finally { await handle.close(); }
}
