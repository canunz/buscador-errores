#requires -Version 7.0
Set-StrictMode -Version Latest

function Get-QaRoot {
    [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
}

function New-QaProcessStartInfo {
    param(
        [Parameter(Mandatory)][string]$Executable,
        [Parameter(Mandatory)][string]$WorkingDirectory,
        [hashtable]$OsEnvironment
    )
    $allowed = @('SystemRoot','WINDIR','SystemDrive','ComSpec','PATHEXT','USERPROFILE','LOCALAPPDATA','APPDATA','ProgramFiles','ProgramFiles(x86)')
    if ($null -eq $OsEnvironment) {
        $OsEnvironment = @{}
        foreach ($name in $allowed) { $OsEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
    }
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $Executable
    $info.WorkingDirectory = $WorkingDirectory
    $info.UseShellExecute = $false
    $info.Environment.Clear()
    foreach ($name in $allowed) {
        if ($OsEnvironment.ContainsKey($name) -and $OsEnvironment[$name]) { $info.Environment[$name] = [string]$OsEnvironment[$name] }
    }
    $info.Environment['PATH'] = ((Split-Path -Parent $Executable), (Join-Path $env:SystemRoot 'System32'), $env:SystemRoot) -join ';'
    $info.Environment['TEMP'] = Join-Path (Get-QaRoot) 'qa/runtime/tmp'
    $info.Environment['TMP'] = $info.Environment['TEMP']
    $info.Environment['NO_PROXY'] = '127.0.0.1,localhost'
    $info.Environment['NG_CLI_ANALYTICS'] = 'false'
    $info.Environment['PYTHONDONTWRITEBYTECODE'] = '1'
    $info.Environment['CI'] = 'true'
    return $info
}

function Assert-QaManifest {
    $python = (Get-Command python.exe -ErrorAction Stop).Source
    $info = New-QaProcessStartInfo -Executable $python -WorkingDirectory (Get-QaRoot)
    $info.Environment['QA_GIT_EXECUTABLE'] = (Get-Command git.exe -ErrorAction Stop).Source
    foreach ($arg in @('-I','-B',(Join-Path $PSScriptRoot 'qa_manifest.py'),'--check')) { $info.ArgumentList.Add($arg) }
    $check = [Diagnostics.Process]::Start($info)
    try { $check.WaitForExit(); if ($check.ExitCode -ne 0) { throw 'El candidato cambió o no tiene manifiesto válido; no se inicia QA.' } }
    finally { $check.Dispose() }
}

function Assert-QaPortFree {
    param([Parameter(Mandatory)][int]$Port)
    $listeners = [Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()
    if (@($listeners | Where-Object Port -eq $Port).Count -ne 0) { throw "El puerto QA $Port está ocupado; no se detiene ningún proceso." }
}

function ConvertFrom-QaSecureString {
    param([Parameter(Mandatory)][Security.SecureString]$Value)
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Value)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

Export-ModuleMember -Function Get-QaRoot,New-QaProcessStartInfo,Assert-QaManifest,Assert-QaPortFree,ConvertFrom-QaSecureString
