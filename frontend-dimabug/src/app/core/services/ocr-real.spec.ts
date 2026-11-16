import { TestBed } from '@angular/core/testing';
import { OcrService } from './ocr.service';

/** Motor y WebAssembly reales; solo recursos locales y capturas sintéticas de prueba. */
describe('OCR real en navegador, sin CDN',()=>{
  let service:OcrService;
  beforeEach(()=>{TestBed.configureTestingModule({providers:[OcrService]});service=TestBed.inject(OcrService);});
  afterEach(()=>service.ngOnDestroy());
  async function imagen(nombre:string):Promise<File>{
    const response=await fetch(new URL('test-ocr/'+nombre,document.baseURI));
    if(!response.ok)throw new Error('No se encontró la imagen sintética de prueba');
    return new File([await response.blob()],nombre,{type:'image/png'});
  }
  it('CP-OCR-001/002/003 reconoce un error legible, distingue vacío y procesa una imagen borrosa',async()=>{
    const legible=await service.reconocer(await imagen('error-legible.png'),()=>{});
    expect(legible.texto).toContain('0x0000011b');
    const vacio=await service.reconocer(await imagen('sin-texto.png'),()=>{});
    expect(vacio.texto).toBe('');
    const borroso=await service.reconocer(await imagen('error-borroso.png'),()=>{});
    expect(typeof borroso.texto).toBe('string');
    // El resultado borroso no se interpreta como una solución ni se envía automáticamente.
    expect(Number.isFinite(borroso.confianza)).toBeTrue();
    const ambiguo=await service.reconocer(await imagen('error-ambiguo.png'),()=>{});
    expect(ambiguo.texto).toContain('Error');
    console.info('OCR_QA_MUESTRAS_SINTETICAS',JSON.stringify({legible:legible.texto,sinTexto:vacio.texto,borroso:borroso.texto,ambiguo:ambiguo.texto}));
  },90_000);
});
