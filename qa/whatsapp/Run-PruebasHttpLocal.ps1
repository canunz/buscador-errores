#requires -Version 7.4
[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$raizQa=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Import-Module (Join-Path $raizQa 'qa/Qa.Common.psm1')
$javaQa=(Get-Command java.exe -ErrorAction Stop).Source
$mavenBoot=Get-ChildItem (Join-Path $env:USERPROFILE '.m2/wrapper/dists/apache-maven-3.9.16*') -Filter 'plexus-classworlds*.jar' -Recurse | Select-Object -First 1
if(!$mavenBoot){throw 'Falta Maven local. No se descargan dependencias automáticamente.'}
$mavenRaiz=$mavenBoot.Directory.Parent.FullName
$mockitoQa=Join-Path $env:USERPROFILE '.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
if(!(Test-Path -LiteralPath $mockitoQa)){throw 'Falta el agente Mockito local.'}
$backendQa=Join-Path $raizQa 'dima-bug-backend'
$salidaQa=Join-Path $raizQa 'qa/runtime/whatsapp-http-local'
$null=New-Item -ItemType Directory -Path $salidaQa -Force
$infoQa=New-QaProcessStartInfo -Executable $javaQa -WorkingDirectory $backendQa
foreach($argumento in @((('-Dmaven.home='+$mavenRaiz)),(('-Dmaven.multiModuleProjectDirectory='+$backendQa)),(('-Dclassworlds.conf='+(Join-Path $mavenRaiz 'bin/m2.conf'))),'-classpath',$mavenBoot.FullName,'org.codehaus.plexus.classworlds.launcher.Launcher','-o','-B','-Dstyle.color=never',('-DargLine=-javaagent:'+ $mockitoQa),'-Ddimabug.automatizacion.seguimiento-habilitado=false','-Ddimabug.auth.correo.habilitado=false','-Dtest=WebhookHttpLocalTest,ValidacionWebhookLocalTest,WebhookHttpPerfilTest,WhatsAppArquitecturaTest,WebhookSimuladoTest,OcrNodeProcesoTest,BusquedaPublicaCanalTest,ConsultarChatbotServiceTest,BusquedaAutoservicioTest,ChatbotBusquedaErroresHttpTest,AutoservicioPublicoHttpTest,ChatbotSecurityIntegrationTest','test')){$infoQa.ArgumentList.Add($argumento)}
$infoQa.RedirectStandardOutput=$true;$infoQa.RedirectStandardError=$true
$procesoQa=[Diagnostics.Process]::Start($infoQa)
$outQa=$procesoQa.StandardOutput.ReadToEndAsync();$errQa=$procesoQa.StandardError.ReadToEndAsync()
try{
 $procesoQa.WaitForExit()
 $textoQa=$outQa.GetAwaiter().GetResult()+$errQa.GetAwaiter().GetResult()
 [IO.File]::WriteAllText((Join-Path $salidaQa 'maven-test.log'),$textoQa)
 $textoQa -split "`n" | Where-Object { $_ -match 'Tests run:|BUILD SUCCESS|BUILD FAILURE|COMPILATION ERROR|cannot find symbol' } | Write-Output
 exit $procesoQa.ExitCode
}finally{$procesoQa.Dispose()}
