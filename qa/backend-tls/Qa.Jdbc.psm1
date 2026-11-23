#requires -Version 7.4
Set-StrictMode -Version Latest

function New-QaBackendStartInfo {
    param([Parameter(Mandatory)][string]$BundleFile,[switch]$EnableQaOtp,[string]$OtpMailbox)
    # Preparation utility only. Caller must own an independently approved guardian/unlock window.
    Import-Module (Join-Path $PSScriptRoot '../Qa.Common.psm1') -DisableNameChecking
    Import-Module (Join-Path $PSScriptRoot 'Qa.Custody.psm1')
    $root = Get-QaRoot
    Assert-QaManifest
    $manifest = Get-Content -LiteralPath (Join-Path $root 'qa/candidate.json') -Raw | ConvertFrom-Json
    $expected = $manifest.artifacts.backend.bundle
    $file = [IO.Path]::GetFullPath($BundleFile)
    if ($file -cne [IO.Path]::GetFullPath((Join-Path $root $expected.path)) -or
        (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() -cne $expected.sha256) { throw 'QA_BUNDLE_NOT_REGISTERED' }
    $bundle = Get-Content -LiteralPath $file -Raw | ConvertFrom-Json
    $java = (Get-Command java.exe -ErrorAction Stop).Source
    $info = New-QaProcessStartInfo -Executable $java -WorkingDirectory (Split-Path -Parent $file)
    $db = Read-QaDatabasePassword
    $JwtSecret = $null
    $password=$null; $jwt=$null
    try {
        $JwtSecret = Read-QaJwtSecret
        $password = ConvertFrom-QaSecureString $db
        $jwt = ConvertFrom-QaSecureString $JwtSecret
        if ([Text.Encoding]::UTF8.GetByteCount($jwt) -lt 32) { throw 'QA_JWT_MINIMUM_32_BYTES' }
        $info.Environment['QA_DB_PASSWORD']=$password
        $info.Environment['QA_JWT_SECRET']=$jwt
        $info.Environment['QA_DATA_DIR']=Join-Path $root 'qa/runtime'
        $store=[IO.Path]::GetFullPath((Join-Path $root $bundle.files.truststore.path))
        $uri=([Uri]$store).AbsoluteUri
        $info.Environment['QA_TLS_TRUSTSTORE_URI']=$uri
        $backend=Join-Path $root $bundle.files.backend.path
        $helper=Join-Path $root $bundle.files.helper.path
        $main='cl.casol.backend.BackendApplication'
        if($EnableQaOtp){
            if('qa_otp' -notin $bundle.files.PSObject.Properties.Name){throw 'QA_OTP_PLUGIN_NOT_REGISTERED'}
            Import-Module (Join-Path $PSScriptRoot '../auth/Qa.Otp.psm1')
            $meta=Assert-QaOtpMailbox $OtpMailbox
            $otpKey=Read-QaOtpKey
            try{$info.Environment['QA_OTP_KEY']=ConvertFrom-QaSecureString $otpKey}finally{$otpKey.Dispose()}
            $info.Environment['QA_OTP_DIRECTORY']=[IO.Path]::GetFullPath($OtpMailbox)
            $info.Environment['QA_OTP_RUN_ID']=$meta.run_id
            $helper=$helper+','+(Join-Path $root $bundle.files.qa_otp.path)
            $main='dimabug.qa.otp.QaApplication'
        }
        $loader=([Uri][IO.Path]::GetFullPath((Join-Path $root $bundle.files.loader.path))).AbsoluteUri
        $profile=([Uri][IO.Path]::GetFullPath((Join-Path $root 'dima-bug-backend/src/main/resources/application-qa-local.properties'))).AbsoluteUri
        foreach($arg in @('-Duser.timezone=UTC',"-Dqa.tls.storeUri=$uri","-Dqa.tls.storeSha256=$($bundle.files.truststore.sha256)",
            "-Dloader.path=$helper","-Dloader.main=$main","-Dloader.config.location=$loader",
            "-Dloader.home=$(Split-Path -Parent $file)",'-cp',$backend,'org.springframework.boot.loader.launch.PropertiesLauncher',
            '--spring.profiles.active=qa-local',"--spring.config.location=$profile")) { $info.ArgumentList.Add($arg) }
        if($EnableQaOtp){$info.ArgumentList.Add('--dimabug.qa.otp.enabled=true')}
        return $info
    } catch {
        Clear-QaBackendStartInfo $info
        throw 'QA_BACKEND_PROCESS_INFO_NOT_CONFIRMED'
    } finally {
        $db.Dispose();if($JwtSecret){$JwtSecret.Dispose()};$password=$null;$jwt=$null
        # Result intentionally holds secrets until its owner starts/disposes it. NEVER format or log it.
    }
}
function Clear-QaBackendStartInfo {
    param([Parameter(Mandatory)][Diagnostics.ProcessStartInfo]$Info)
    foreach($name in @('QA_DB_PASSWORD','QA_JWT_SECRET','QA_OTP_KEY')) { $null=$Info.Environment.Remove($name) }
}
Export-ModuleMember -Function New-QaBackendStartInfo,Clear-QaBackendStartInfo
