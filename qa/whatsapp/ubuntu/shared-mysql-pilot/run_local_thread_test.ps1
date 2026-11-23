$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$context = Join-Path $repo 'qa/runtime/thread-identity-local'
New-Item -ItemType Directory -Force -Path $context | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'thread_check.py'), (Join-Path $PSScriptRoot 'test_thread_identity.py') -Destination $context
Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'local-thread-test') -File | Copy-Item -Destination $context
& docker build -q -t dimabug-thread-identity-local:qa $context
if ($LASTEXITCODE -ne 0) { throw 'LOCAL_TEST_BUILD_FAILED' }
# Capabilities are confined to this disposable container; no host mounts/network.
& docker run --rm --name dimabug-thread-identity-disposable --network none --memory 512m --memory-swap 512m --cpus 0.5 --pids-limit 96 --cap-add SYS_PTRACE --cap-add SYS_ADMIN dimabug-thread-identity-local:qa
if ($LASTEXITCODE -ne 0) { throw 'LOCAL_THREAD_TEST_FAILED' }
