#requires -Version 7.4
[CmdletBinding()]param([Parameter(Mandatory)][string]$Mailbox,[Parameter(Mandatory)][ValidatePattern('^[1-9][0-9]*$')][string]$RecordId)
$ErrorActionPreference='Stop'
Import-Module (Join-Path $PSScriptRoot 'Qa.Otp.psm1')
Import-Module (Join-Path $PSScriptRoot 'Qa.Otp.Crypto.psm1')
Import-Module (Join-Path $PSScriptRoot '../Qa.Common.psm1') -DisableNameChecking
Import-Module (Join-Path $PSScriptRoot '../backend-tls/Qa.Custody.psm1')
$secure=$null;$key=$null;$plain=$null;$form=$null;$timer=$null;$delivery=$null
try{
    if(-not $IsWindows){throw 'QA_WINDOWS_REQUIRED'}
    $meta=Assert-QaOtpMailbox $Mailbox
    $file=Join-Path $Mailbox ($RecordId+'.otp');Assert-QaCustodyPath $file
    if((Get-Item -LiteralPath $file).Length -gt 4096){throw 'QA_DELIVERY_SIZE'}
    $bytes=[IO.File]::ReadAllBytes($file)
    $secure=Read-QaOtpKey;$key=[Convert]::FromBase64String((ConvertFrom-QaSecureString $secure))
    $delivery=Unprotect-QaOtpEnvelope -Bytes $bytes -Key $key -RunId $meta.run_id -RecordId $RecordId
    $expiry=$delivery.Expires
    Add-Type -AssemblyName System.Windows.Forms
    $form=[Windows.Forms.Form]::new();$form.Text='DimaBug QA — código local';$form.Width=420;$form.Height=210;$form.TopMost=$true
    $label=[Windows.Forms.Label]::new();$label.Text=$delivery.Email;$label.Left=20;$label.Top=20;$label.Width=370;$form.Controls.Add($label)
    $box=[Windows.Forms.TextBox]::new();$box.Left=20;$box.Top=60;$box.Width=350;$box.ReadOnly=$true;$box.ShortcutsEnabled=$false;$box.UseSystemPasswordChar=$true;$box.Text=ConvertFrom-QaSecureString $delivery.Code;$form.Controls.Add($box)
    $reveal=[Windows.Forms.Button]::new();$reveal.Text='Mantener para ver';$reveal.Left=20;$reveal.Top=100;$reveal.Width=180
    $reveal.Add_MouseDown({$box.UseSystemPasswordChar=$false});$reveal.Add_MouseUp({$box.UseSystemPasswordChar=$true});$reveal.Add_MouseLeave({$box.UseSystemPasswordChar=$true});$form.Controls.Add($reveal)
    $deadline=[DateTimeOffset]::UtcNow.AddSeconds(30);if($expiry -lt $deadline){$deadline=$expiry}
    $timer=[Windows.Forms.Timer]::new();$timer.Interval=250;$timer.Add_Tick({if([DateTimeOffset]::UtcNow -ge $deadline){$form.Close()}});$timer.Start()
    $null=$form.ShowDialog()
    $box.Text=''
    # Delivery is read-once. Server-side code consumption still occurs only via the real verifier.
    Remove-Item -LiteralPath $file
}catch{
    # No provider error, plaintext, body, token, recipient or code reaches the console/transcript.
    '{"estado":"QA_LOCAL_CODE_NOT_AVAILABLE","details_suppressed":true}'
    exit 1
}finally{
    if($timer){$timer.Stop();$timer.Dispose()};if($form){$form.Dispose()};if($secure){$secure.Dispose()}
    if($delivery){$delivery.Code.Dispose()}
    foreach($array in @($key,$plain)){if($array -is [byte[]]){[Array]::Clear($array,0,$array.Length)}}
    $fields=$null
}
