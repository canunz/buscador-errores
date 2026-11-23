'use strict';
const {build}=require('../build.cjs');
const {spawnSync}=require('node:child_process');
const path=require('node:path');
build();
const env={};
for(const name of ['SystemRoot','WINDIR','SystemDrive','TEMP','TMP']) if(process.env[name])env[name]=process.env[name];
const result=spawnSync(process.execPath,['--test','--test-concurrency=1',path.join(__dirname,'ocr.test.cjs')],{
  env,stdio:'inherit',windowsHide:true,
});
process.exitCode=result.status ?? 1;
