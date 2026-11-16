import { ExtractorErrorOcr } from './extractor-error';
import { ExtractorErrorOcrService } from '../services/extractor-error-ocr.service';
import { CASOS_OCR } from './pruebas/casos';
import { normalizarTextoOcr } from './texto';

describe('Nucleo OCR compartido: equivalencia con la linea base', () => {
  for (const caso of CASOS_OCR) {
    it(caso.id + ': mantiene candidatos, avisos y posiciones', () => {
      expect(new ExtractorErrorOcr().extraer(caso.original)).toEqual(caso.esperado);
      expect(new ExtractorErrorOcrService().extraer(caso.original)).toEqual(caso.esperado);
    });
  }
  it('mantiene limpieza, vacio y limite del resultado OCR', () => {
    expect(normalizarTextoOcr(' \u0000Error 0x0000011b\u007f\n')).toBe('Error 0x0000011b');
    expect(normalizarTextoOcr(' \u0000 ')).toBe('');
    expect(normalizarTextoOcr('x'.repeat(20_000)).length).toBe(20_000);
    expect(() => normalizarTextoOcr('x'.repeat(20_001))).toThrowError('La imagen contiene demasiado texto. Recorta el mensaje de error.');
  });
});
