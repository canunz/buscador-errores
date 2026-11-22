import { ExtractorErrorOcrService } from './extractor-error-ocr.service';

describe('Extracción local de errores OCR',()=>{
  const servicio=new ExtractorErrorOcrService();
  it('conserva un mensaje limpio y el original exacto',()=>{
    const original='Error 0x0000011b';const r=servicio.extraer(original);
    expect(r.original).toBe(original);expect(r.candidatos[0].texto).toBe(original);
  });
  it('separa el error de importes y rutas ambiguas sin inventar palabras',()=>{
    const original='TOTAL VENTA $33.050\nDIAL A PAGAR ¢ D50\nError al escribir el archivo\n/dim/tbk/2025 01 20/voucher-v\nS| enta-original_49.txt | rr]\nFP Formal iento Monto\nReimprimir [Enter | Cerrar\nSaldo $33.050';
    const r=servicio.extraer(original);
    expect(r.original).toBe(original);expect(r.candidatos[0].texto).toBe('Error al escribir el archivo');
    expect(r.candidatos[0].avisos.length).toBeGreaterThan(0);
    expect(r.candidatos.some(c=>c.texto.includes('voucher-venta'))).toBeFalse();
  });
  it('une palabras conservando el guion y avisando de la incertidumbre',()=>{
    const c=servicio.extraer('Error en el docu-\nmento').candidatos[0];
    expect(c.texto).toBe('Error en el docu-mento');expect(c.avisos.length).toBeGreaterThan(0);
  });
  it('une una ruta dividida en un separador explícito',()=>{
    const c=servicio.extraer('Error al abrir archivo\nC:\\Windows\\\nSystem32\\foo.dll').candidatos[0];
    expect(c.texto).toBe('Error al abrir archivo C:\\Windows\\System32\\foo.dll');
    expect(c.lineaHasta).toBe(3);expect(c.avisos.length).toBeGreaterThan(0);
  });
  it('conserva códigos técnicos y une fragmentos explícitos',()=>{
    expect(servicio.extraer('Error 0x8007\n0005').candidatos[0].texto).toBe('Error 0x80070005');
    expect(servicio.extraer('Error ORA-\n00942').candidatos[0].texto).toBe('Error ORA-00942');
    expect(servicio.extraer('SQLSTATE 08001').candidatos[0].texto).toBe('SQLSTATE 08001');
    expect(servicio.extraer('HTTP 503').candidatos[0].texto).toBe('HTTP 503');
  });
  it('no mezcla varios errores ni desplaza mensajes distintos con alternativas',()=>{
    const r=servicio.extraer('Error en archivo /tmp/a.txt\nError en impresora /tmp/b.txt\nError de red /tmp/c.txt');
    expect(r.candidatos.length).toBe(3);expect(r.hayMas).toBeTrue();
    expect(r.candidatos.map(c=>c.lineaDesde)).toEqual([1,2,3]);
    expect(servicio.extraer('HTTP 503\nORA-00942').candidatos.length).toBe(2);
  });
  it('no propone consultas sin señales de error',()=>{
    const original='Total $33.050\nReimprimir Enter Cerrar';
    expect(servicio.extraer(original)).toEqual({original,candidatos:[],hayMas:false});
  });
  it('no sustituye caracteres dudosos ni recorta mensajes largos',()=>{
    expect(servicio.extraer('Error OX00000l1b').candidatos[0].texto).toBe('Error OX00000l1b');
    const largo='Error '+ 'x'.repeat(301);expect(servicio.extraer(largo).candidatos[0].texto).toBe(largo);
  });
});
