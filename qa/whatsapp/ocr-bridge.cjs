"use strict";
// Protocolo privado por pipes. stdout es IPC capturado, nunca log de aplicación.
const path=require('node:path');
const root=path.resolve(__dirname,'../..');
const dist=path.join(root,'frontend-dimabug/tools/ocr-node/dist/tools/ocr-node/src');
require(path.join(dist,'sin-red.js'));
const {reconocerBytes}=require(path.join(dist,'adapter.js'));
const {leerFixtureAutorizado}=require(path.join(dist,'fixtures.js'));
const allowed=Object.freeze({
 'qa-media-legible':'error-legible.png', 'qa-media-ambiguo':'error-ambiguo.png',
 'qa-media-borroso':'error-borroso.png', 'qa-media-vacia':'sin-texto.png'
});
const MAX_STDIN=5*1024*1024;
// Imagen real descargada por Java desde Meta: llega por stdin, nunca como ruta ni URL.
function leerStdin(){
 return new Promise((ok,fallo)=>{
  const partes=[];let total=0;
  process.stdin.on('data',b=>{total+=b.length;if(total>MAX_STDIN){fallo(Error());process.stdin.destroy();return;}partes.push(b);});
  process.stdin.on('end',()=>ok(Buffer.concat(partes)));
  process.stdin.on('error',fallo);
 });
}
function mimeDe(b){
 if(b.length>8&&b[0]===0x89&&b[1]===0x50&&b[2]===0x4e&&b[3]===0x47)return 'image/png';
 if(b.length>3&&b[0]===0xff&&b[1]===0xd8&&b[2]===0xff)return 'image/jpeg';
 if(b.length>12&&b.toString('ascii',0,4)==='RIFF'&&b.toString('ascii',8,12)==='WEBP')return 'image/webp';
 throw Error();
}
(async()=>{
 const [id,...extra]=process.argv.slice(2);
 if(extra.length) throw Error();
 let bytes,mime='image/png';
 if(id==='--stdin'){bytes=await leerStdin();mime=mimeDe(bytes);}
 else{
  if(!Object.hasOwn(allowed,id) && id!=='qa-media-corrupta') throw Error();
  bytes=id==='qa-media-corrupta'?Buffer.from('PNG corrupto sintetico'):await leerFixtureAutorizado(allowed[id]);
 }
 const result=await reconocerBytes(bytes,mime);
 process.stdout.write(JSON.stringify({candidatos:result.extraccion.candidatos.map(c=>({texto:c.texto,avisos:c.avisos}))}));
})().catch(()=>{process.stdout.write(JSON.stringify({error:'OCR_NO_DISPONIBLE'}));process.exitCode=1;});
