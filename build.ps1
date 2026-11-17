$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $jdkBin = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { '' }
    $javac = if ($jdkBin) { Join-Path $jdkBin 'javac' } else { 'javac' }
    $jar = if ($jdkBin) { Join-Path $jdkBin 'jar' } else { 'jar' }
    New-Item -ItemType Directory -Force -Path 'build/classes' | Out-Null
    $sources = @(Get-ChildItem -LiteralPath 'src/main/java' -Filter '*.java' -Recurse | ForEach-Object { $_.FullName })
    & $javac --release 17 -encoding UTF-8 -Xlint:all -Werror -d build/classes @sources
    if ($LASTEXITCODE -ne 0) { throw 'Falha na compilacao.' }
    & $jar --create --file build/udp.jar --main-class br.edu.udp.Main -C build/classes .
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao montar o JAR.' }
    Write-Host 'Gerado: build/udp.jar'
} finally { Pop-Location }
