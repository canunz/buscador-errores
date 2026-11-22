import { dimensionesImagenOcr, prepararImagenOcr } from './imagen-ocr';

describe('Validación de imagen OCR', () => {
  function png(ancho:number,alto:number) {
    const b=new Uint8Array(33);b.set([137,80,78,71,13,10,26,10]);
    const v=new DataView(b.buffer);v.setUint32(8,13);b.set([73,72,68,82],12);v.setUint32(16,ancho);v.setUint32(20,alto);return b;
  }
  it('lee dimensiones PNG reales sin confiar en el nombre',()=>expect(dimensionesImagenOcr(png(800,600),'image/png')).toEqual({ancho:800,alto:600}));
  it('rechaza MIME suplantado y SVG',()=>{
    expect(()=>dimensionesImagenOcr(png(10,10),'image/jpeg')).toThrowError();
    expect(()=>dimensionesImagenOcr(new TextEncoder().encode('<svg/>'),'image/png')).toThrowError();
  });
  it('rechaza demasiados píxeles antes de decodificar',()=>expect(()=>dimensionesImagenOcr(png(6000,4000),'image/png')).toThrowError(/12 megapíxeles/));
  it('rechaza archivos vacíos y mayores a 5 MB',async()=>{
    await expectAsync(prepararImagenOcr(new File([],'vacio.png',{type:'image/png'}))).toBeRejectedWithError(/5 MB/);
    await expectAsync(prepararImagenOcr(new File([new Uint8Array(5*1024*1024+1)],'grande.png',{type:'image/png'}))).toBeRejectedWithError(/5 MB/);
  });
  it('rechaza archivo no permitido y PNG corrupto',async()=>{
    await expectAsync(prepararImagenOcr(new File(['PDF'],'foto.pdf',{type:'application/pdf'}))).toBeRejectedWithError(/PNG/);
    await expectAsync(prepararImagenOcr(new File([png(50,50)],'corrupto.png',{type:'image/png'}))).toBeRejectedWithError(/dañada/);
  });
  it('valida JPEG y WebP estático y rechaza WebP animado',()=>{
    const jpg=new Uint8Array([255,216,255,192,0,8,8,0,60,0,80,1]);
    expect(dimensionesImagenOcr(jpg,'image/jpeg')).toEqual({ancho:80,alto:60});
    const webp=new Uint8Array(30);webp.set(new TextEncoder().encode('RIFF'),0);webp.set(new TextEncoder().encode('WEBPVP8X'),8);webp[24]=79;webp[27]=59;
    expect(dimensionesImagenOcr(webp,'image/webp')).toEqual({ancho:80,alto:60});webp[20]=2;
    expect(()=>dimensionesImagenOcr(webp,'image/webp')).toThrowError(/animación/);
  });
});
