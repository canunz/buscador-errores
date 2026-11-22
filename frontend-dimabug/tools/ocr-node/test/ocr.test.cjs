'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const path=require('node:path');
const sharp=require('sharp');
const {spawnSync}=require('node:child_process');
const core='../dist/src/app/core/ocr-compartido/';
const {ExtractorErrorOcr}=require(core+'extractor-error.js');
const {normalizarTextoOcr}=require(core+'texto.js');
const {CASOS_OCR}=require(core+'pruebas/casos.js');
const {FIXTURES_AUTORIZADOS}=require(core+'pruebas/imagenes.js');
const {reconocerBytes}=require('../dist/tools/ocr-node/src/adapter.js');
const {leerFixtureAutorizado}=require('../dist/tools/ocr-node/src/fixtures.js');

for(const caso of CASOS_OCR) {
  test('equivalencia previa Angular/Node: '+caso.id,()=>{
    assert.deepEqual(new ExtractorErrorOcr().extraer(caso.original),caso.esperado);
  });
}
test('limpieza y limite de 20.000 caracteres compartidos',()=>{
  assert.equal(normalizarTextoOcr(' \x00Error\x7f\n'),'Error');
  assert.equal(normalizarTextoOcr(' \x00 '),'');
  assert.equal(normalizarTextoOcr('x'.repeat(20000)).length,20000);
  assert.throws(()=>normalizarTextoOcr('x'.repeat(20001)),/demasiado texto/);
});
test('CLI limita lectura a fixtures autorizados; rechaza rutas y URLs',async()=>{
  for(const name of ['../error-legible.png','https://example.invalid/a.png','desconocido.png','..\\error-legible.png'])
    await assert.rejects(leerFixtureAutorizado(name),/autorizada/);
});
test('rechaza formato, vacio, mas de 5 MiB y cabeceras suplantadas',async()=>{
  await assert.rejects(reconocerBytes(Buffer.from('pdf'),'application/pdf'),/PNG, JPEG o WebP/);
  await assert.rejects(reconocerBytes(Buffer.alloc(0),'image/png'),/5 MB/);
  await assert.rejects(reconocerBytes(Buffer.alloc(5*1024*1024+1),'image/png'),/5 MB/);
  await assert.rejects(reconocerBytes(Buffer.from('<svg/>'),'image/png'),/válida/);
});
function cabeceraPng(width,height){
  const b=Buffer.alloc(33);Buffer.from([137,80,78,71,13,10,26,10]).copy(b);
  b.writeUInt32BE(13,8);b.write('IHDR',12);b.writeUInt32BE(width,16);b.writeUInt32BE(height,20);return b;
}
test('rechaza mas de 12 megapixeles antes de decodificar',async()=>{
  await assert.rejects(reconocerBytes(cabeceraPng(6000,4000),'image/png'),/12 megapíxeles/);
});
test('rechaza PNG corrupto con cabecera valida sin filtrar error interno',async()=>{
  await assert.rejects(reconocerBytes(cabeceraPng(50,50),'image/png'),{
    message:'No fue posible leer la imagen. Puede estar dañada; prueba con otra captura.',
  });
});
for(const [name,fixture] of Object.entries(FIXTURES_AUTORIZADOS)) {
  test('OCR real y comparacion con Angular previo: '+name,{timeout:40000},async()=>{
    const result=await reconocerBytes(await leerFixtureAutorizado(name),'image/png');
    assert.equal(result.texto,fixture.textoAngularBaseline);
    assert.ok(Number.isFinite(result.confianza));
    assert.deepEqual(result.extraccion,new ExtractorErrorOcr().extraer(fixture.textoAngularBaseline));
    if(name==='error-ambiguo.png')assert.ok(!result.texto.includes('0x0000011b'));
  });
}
for(const mime of ['image/jpeg','image/webp']) {
  test('decodifica y reconoce '+mime+' sin guardar imagen',{timeout:40000},async()=>{
    const input=await leerFixtureAutorizado('error-legible.png');
    const bytes=await (mime==='image/jpeg'?sharp(input).jpeg({quality:100}):sharp(input).webp({lossless:true})).toBuffer();
    const result=await reconocerBytes(bytes,mime);
    assert.match(result.texto,/0x0000011b/);
  });
}
test('plazo de 30 s termina el proceso y rechaza la solicitud',async context=>{
  const input=await leerFixtureAutorizado('error-legible.png');
  context.mock.timers.enable({apis:['setTimeout']});
  const result=reconocerBytes(input,'image/png');
  const check=assert.rejects(result,/tardó demasiado/);
  context.mock.timers.tick(30001);
  await check;
  context.mock.timers.reset();
});
test('preload bloquea red sin realizar conexiones',()=>{
  const preloader=path.resolve(__dirname,'../dist/tools/ocr-node/src/sin-red.js');
  const script=`const assert=require('node:assert/strict');
    for(const name of ['node:http','node:https'])assert.throws(()=>require(name).get('https://example.invalid'),/no permite/);
    assert.throws(()=>require('node:net').connect(3306,'127.0.0.1'),/no permite/);
    assert.throws(()=>require('node:tls').connect(443),/no permite/);
    assert.throws(()=>require('node:dgram').createSocket('udp4'),/no permite/);
    fetch('https://example.invalid').then(()=>process.exitCode=1,e=>assert.match(e.message,/no permite/));`;
  const result=spawnSync(process.execPath,['--require',preloader,'-e',script],{encoding:'utf8',windowsHide:true});
  assert.equal(result.status,0);
});
