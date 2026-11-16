/// <reference lib="webworker" />
import { createWorker, type Worker as MotorOcr } from 'tesseract.js';

let motor: MotorOcr | undefined;
let ocupado = false;
addEventListener('message', async ({data}: MessageEvent<{ imagen: Blob; recursos: string }>) => {
  if (ocupado) return;
  ocupado = true;
  try {
    // Un coordinador propio permite terminar también la inicialización del motor al cancelar.
    motor ??= await createWorker('eng+spa', 1, {
      workerPath: data.recursos + 'worker.min.js',
      corePath: data.recursos + 'core', langPath: data.recursos + 'idiomas',
      workerBlobURL: false, cacheMethod: 'none',
      errorHandler: () => {},
      logger: m => postMessage({tipo:'progreso',progreso:
        m.status === 'recognizing text' ? 70 + m.progress * 30 : m.progress * 65}),
    }, {load_system_dawg:'0',load_freq_dawg:'0'});
    const {data: resultado} = await motor.recognize(data.imagen, {}, {text:true});
    postMessage({tipo:'resultado',texto:resultado.text,confianza:resultado.confidence});
  } catch { postMessage({tipo:'error'}); }
  finally { ocupado = false; }
});
