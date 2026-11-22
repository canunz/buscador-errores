import { LIMITES_OCR } from './limites';

/** Inspecciona firmas y dimensiones antes de pedir al navegador que decodifique la imagen. */
export function dimensionesImagenOcr(bytes: Uint8Array, mime: string): { ancho: number; alto: number } {
  const v = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  let ancho = 0, alto = 0;
  const texto = (a: number, b: number) => String.fromCharCode(...bytes.subarray(a, b));
  if (mime === 'image/png' && bytes.length >= 33 &&
      [137,80,78,71,13,10,26,10].every((b,i) => bytes[i] === b) &&
      v.getUint32(8) === 13 && texto(12,16) === 'IHDR') {
    ancho = v.getUint32(16); alto = v.getUint32(20);
  } else if (mime === 'image/jpeg' && bytes[0] === 255 && bytes[1] === 216) {
    let p = 2;
    while (p + 4 <= bytes.length) {
      if (bytes[p++] !== 255) break;
      while (bytes[p] === 255) p++;
      const marcador = bytes[p++];
      if (marcador === 217 || marcador === 218) break;
      if (marcador === 1 || (marcador >= 208 && marcador <= 215)) continue;
      if (p + 2 > bytes.length) break;
      const longitud = v.getUint16(p);
      if (longitud < 2 || p + longitud > bytes.length) break;
      if ([192,193,194,195,197,198,199,201,202,203,205,206,207].includes(marcador) && longitud >= 8) {
        alto = v.getUint16(p + 3); ancho = v.getUint16(p + 5); break;
      }
      p += longitud;
    }
  } else if (mime === 'image/webp' && bytes.length >= 30 && texto(0,4) === 'RIFF' && texto(8,12) === 'WEBP') {
    const formato = texto(12,16);
    if (formato === 'VP8X') {
      if ((bytes[20] & 2) !== 0) throw new Error('Selecciona una imagen estática, sin animación.');
      ancho = 1 + bytes[24] + (bytes[25] << 8) + (bytes[26] << 16);
      alto = 1 + bytes[27] + (bytes[28] << 8) + (bytes[29] << 16);
    } else if (formato === 'VP8 ' && bytes[23] === 157 && bytes[24] === 1 && bytes[25] === 42) {
      ancho = v.getUint16(26, true) & 0x3fff; alto = v.getUint16(28, true) & 0x3fff;
    } else if (formato === 'VP8L' && bytes[20] === 47) {
      const bits = v.getUint32(21, true);
      ancho = 1 + (bits & 0x3fff); alto = 1 + ((bits >>> 14) & 0x3fff);
    }
  }
  if (!ancho || !alto) throw new Error('El archivo no es una imagen PNG, JPEG o WebP válida.');
  if (ancho * alto > LIMITES_OCR.pixeles) throw new Error('La imagen supera 12 megapíxeles. Recorta el mensaje de error y vuelve a intentarlo.');
  return { ancho, alto };
}
