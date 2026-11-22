import { reconocerBytes } from './adapter';
import { leerFixtureAutorizado } from './fixtures';

async function main(): Promise<void> {
  const [name,...extra] = process.argv.slice(2);
  if (!name || extra.length) throw new Error('Indica un único fixture autorizado: error-legible.png, error-ambiguo.png, error-borroso.png o sin-texto.png.');
  const result = await reconocerBytes(await leerFixtureAutorizado(name),'image/png');
  // Solo metadatos; el prototipo no registra texto OCR ni nombres de archivo.
  console.log(JSON.stringify({estado:result.texto?'reconocido':'sin_texto',caracteres:result.texto.length,
    candidatos:result.extraccion.candidatos.length,avisos:result.extraccion.candidatos.reduce((n,c)=>n+c.avisos.length,0),
    confianza:result.confianza,contenido_registrado:false}));
}
void main().catch(() => {
  console.error(JSON.stringify({estado:'error',mensaje:'No se pudo procesar el fixture autorizado. Revisa formato, integridad y recursos locales.',contenido_registrado:false}));
  process.exitCode=1;
});
