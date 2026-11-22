import sharp from 'sharp';
import { access } from 'node:fs/promises';
import { join } from 'node:path';
import { createWorker, type Worker as MotorOcr } from 'tesseract.js';
import { LIMITES_OCR } from '../../../src/app/core/ocr-compartido/limites';
import { dimensionesImagenOcr } from '../../../src/app/core/ocr-compartido/imagen-header';
import { normalizarTextoOcr } from '../../../src/app/core/ocr-compartido/texto';
import { ExtractorErrorOcr } from '../../../src/app/core/ocr-compartido/extractor-error';
import type { MimeOcr, RespuestaProcesoOcr } from './adapter';

process.once('message', async (request: {bytes: Buffer; mime: MimeOcr}) => {
  let motor: MotorOcr | undefined;
  let response: RespuestaProcesoOcr = {ok:false,error:'No fue posible reconocer el texto. Intenta con otra imagen o escribe tu consulta.'};
  let phase: 'imagen' | 'ocr' = 'imagen';
  try {
    if (!Buffer.isBuffer(request.bytes) || !request.bytes.length || request.bytes.length > LIMITES_OCR.bytes)
      throw new Error('Entrada no permitida');
    dimensionesImagenOcr(request.bytes,request.mime);
    const image = sharp(request.bytes,{limitInputPixels:LIMITES_OCR.pixeles,failOn:'warning',sequentialRead:true});
    const metadata = await image.metadata();
    if (!metadata.width || !metadata.height || metadata.width * metadata.height > LIMITES_OCR.pixeles ||
        (metadata.pages ?? 1) > 1) throw new Error('Imagen no estatica o dimensiones no permitidas');
    const scale = Math.min(1,LIMITES_OCR.lado/Math.max(metadata.width,metadata.height));
    const prepared = await image.autoOrient().flatten({background:'#ffffff'})
      .resize(Math.max(1,Math.round(metadata.width*scale)),Math.max(1,Math.round(metadata.height*scale)),{fit:'inside',withoutEnlargement:true})
      .png().toBuffer();
    phase = 'ocr';
    // Copias de los idiomas YA instalados, preparadas por build.cjs. Nunca CDN.
    const langPath = join(__dirname,'../../../idiomas');
    await Promise.all(['eng','spa'].map(code => access(join(langPath,code+'.traineddata.gz'))));
    motor = await createWorker('eng+spa',1,{
      workerPath: require.resolve('tesseract.js/src/worker-script/node/index.js'),
      langPath,cacheMethod:'none',logger:()=>{},errorHandler:()=>{},
    },{load_system_dawg:'0',load_freq_dawg:'0'});
    const {data} = await motor.recognize(prepared,{}, {text:true});
    const texto = normalizarTextoOcr(data.text);
    response = {ok:true,resultado:{texto,confianza:data.confidence,extraccion:new ExtractorErrorOcr().extraer(texto)}};
  } catch (error) {
    const textoLargo = error instanceof Error && error.message === 'La imagen contiene demasiado texto. Recorta el mensaje de error.';
    response = {ok:false,error:textoLargo ? error.message : phase==='imagen'
      ? 'No fue posible leer la imagen. Puede estar dañada; prueba con otra captura.'
      : 'No fue posible reconocer el texto. Intenta con otra imagen o escribe tu consulta.'};
  } finally {
    try { await motor?.terminate(); } catch { /* No registrar contenido ni errores del motor. */ }
    process.send?.(response, () => process.disconnect());
  }
});
