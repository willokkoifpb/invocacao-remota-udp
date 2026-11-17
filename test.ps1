$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    & ./build.ps1
    $jdkBin = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { '' }
    $javac = if ($jdkBin) { Join-Path $jdkBin 'javac' } else { 'javac' }
    $java = if ($jdkBin) { Join-Path $jdkBin 'java' } else { 'java' }
    New-Item -ItemType Directory -Force -Path 'build/test-classes' | Out-Null
    $sources = @(Get-ChildItem -LiteralPath 'src/test/java' -Filter '*.java' -Recurse | ForEach-Object { $_.FullName })
    & $javac --release 17 -encoding UTF-8 -d build/test-classes -cp build/classes @sources
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar testes.' }
    & $java -cp 'build/classes;build/test-classes' br.edu.udp.ProtocolTest
    if ($LASTEXITCODE -ne 0) { throw 'Falha nos testes.' }
} finally { Pop-Location }
