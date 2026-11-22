'use strict';
const ts = require('../../node_modules/typescript');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '../..');
const core = path.join(root, 'src/app/core/ocr-compartido');
function files(dir) {
  return fs.readdirSync(dir, {withFileTypes:true}).flatMap(entry => entry.isDirectory()
    ? files(path.join(dir, entry.name)) : entry.name.endsWith('.ts') && !entry.name.endsWith('.spec.ts') ? [path.join(dir,entry.name)] : []);
}
function build() {
  const coreOnly = ts.createProgram(files(core), {
    target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS,lib:['lib.es2022.d.ts'],
    types:[],strict:true,noEmit:true,
  });
  if (ts.getPreEmitDiagnostics(coreOnly).length)
    throw new Error('El nucleo debe compilar sin tipos DOM, Node ni Angular.');
  const program = ts.createProgram([...files(core), ...files(path.join(__dirname, 'src'))], {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, moduleResolution: ts.ModuleResolutionKind.Node10,
    rootDir: root, outDir: path.join(__dirname,'dist'), lib:['lib.es2022.d.ts'],
    typeRoots:[path.join(root,'node_modules/@types')], types:['node'], strict:true,
    esModuleInterop:true, skipLibCheck:true, noEmitOnError:true,
  });
  const diagnostics = ts.getPreEmitDiagnostics(program);
  if (diagnostics.length) throw new Error(ts.formatDiagnosticsWithColorAndContext(diagnostics, {
    getCanonicalFileName: p => p, getCurrentDirectory: () => root, getNewLine: () => '\n',
  }));
  if (program.emit().emitSkipped) throw new Error('No se pudo compilar el prototipo.');
  // Copiar modelos ya instalados; ninguna descarga durante reconocimiento.
  const idiomas = path.join(__dirname,'dist/idiomas');
  fs.mkdirSync(idiomas,{recursive:true});
  for (const code of ['eng','spa']) {
    const source = path.join(root,'node_modules/@tesseract.js-data',code,'4.0.0_best_int',code+'.traineddata.gz');
    fs.copyFileSync(source,path.join(idiomas,code+'.traineddata.gz'));
  }
}
module.exports = { build };
if (require.main === module) build();
