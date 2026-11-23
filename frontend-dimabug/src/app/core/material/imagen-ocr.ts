import { LIMITES_OCR } from '../models/ocr.model';
import { dimensionesImagenOcr } from '../ocr-compartido/imagen-header';
export { dimensionesImagenOcr } from '../ocr-compartido/imagen-header';

export async function prepararImagenOcr(archivo: File): Promise<Blob> {
  if (!['image/png','image/jpeg','image/webp'].includes(archivo.type))
    throw new Error('Selecciona una imagen PNG, JPEG o WebP.');
  if (!archivo.size || archivo.size > LIMITES_OCR.bytes)
    throw new Error('Selecciona una imagen de hasta 5 MB que no esté vacía.');
  dimensionesImagenOcr(new Uint8Array(await archivo.arrayBuffer()), archivo.type);
  let bitmap: ImageBitmap;
  try { bitmap = await createImageBitmap(archivo); }
  catch { throw new Error('No fue posible leer la imagen. Puede estar dañada; prueba con otra captura.'); }
  const canvas = document.createElement('canvas');
  try {
    if (!bitmap.width || !bitmap.height || bitmap.width * bitmap.height > LIMITES_OCR.pixeles)
      throw new Error('La imagen supera las dimensiones permitidas.');
    const escala = Math.min(1, LIMITES_OCR.lado / Math.max(bitmap.width, bitmap.height));
    canvas.width = Math.max(1, Math.round(bitmap.width * escala));
    canvas.height = Math.max(1, Math.round(bitmap.height * escala));
    const ctx = canvas.getContext('2d');
    if (!ctx) throw new Error('Este navegador no permite preparar la imagen.');
    ctx.fillStyle = '#fff'; ctx.fillRect(0,0,canvas.width,canvas.height);
    ctx.drawImage(bitmap,0,0,canvas.width,canvas.height);
    return await new Promise<Blob>((resolve,reject) => canvas.toBlob(blob =>
      blob ? resolve(blob) : reject(new Error('No fue posible preparar la imagen.')), 'image/png'));
  } finally { bitmap.close(); canvas.width = 0; canvas.height = 0; }
}
