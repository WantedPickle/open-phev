param(
    [string]$SdkRoot = $env:ANDROID_HOME,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$BuildDir = (Join-Path $PSScriptRoot 'build'),
    [string]$KeyStore = '',
    [switch]$TestOnly
)
$ErrorActionPreference = 'Stop'
if (-not $JavaHome) { throw 'Set JAVA_HOME to JDK 17 or pass -JavaHome.' }
$env:JAVA_HOME = $JavaHome
$javac = Join-Path $JavaHome 'bin/javac.exe'
$java = Join-Path $JavaHome 'bin/java.exe'
function Check-Exit { if ($LASTEXITCODE -ne 0) { throw "Build command failed: exit $LASTEXITCODE" } }
New-Item -ItemType Directory -Force $BuildDir | Out-Null
$BuildDir = (Resolve-Path $BuildDir).Path
$testClasses = Join-Path $BuildDir 'test-classes'
New-Item -ItemType Directory -Force $testClasses | Out-Null
$src = Join-Path $PSScriptRoot 'app/src/main/java/org/openphev'
& $javac -encoding UTF-8 -d $testClasses "$src/Protocol.java" "$src/ClimateTimers.java" "$src/Diagnostics.java" "$src/NetworkGate.java" "$src/LiveStatus.java" "$src/TimerSession.java" "$src/ChargeTimers.java" "$src/ChargeSession.java" "$src/VehicleClient.java" "$src/SchedulePreview.java" "$src/TutorialFlow.java" "$PSScriptRoot/tests/org/openphev/ProtocolTest.java" "$PSScriptRoot/tests/org/openphev/LiveStatusTest.java" "$PSScriptRoot/tests/org/openphev/TimerSessionTest.java" "$PSScriptRoot/tests/org/openphev/ChargeTimersTest.java" "$PSScriptRoot/tests/org/openphev/SchedulePreviewTest.java" "$PSScriptRoot/tests/org/openphev/TutorialFlowTest.java"
Check-Exit
& $java -cp $testClasses org.openphev.ProtocolTest
Check-Exit
if ($TestOnly) { exit 0 }
if (-not $SdkRoot) { throw 'Set ANDROID_HOME or pass -SdkRoot. Install platforms;android-36 and build-tools;36.0.0.' }
$SdkRoot = (Resolve-Path $SdkRoot).Path
$bt = Join-Path $SdkRoot 'build-tools/36.0.0'
$androidJar = Join-Path $SdkRoot 'platforms/android-36/android.jar'
$classes = Join-Path $BuildDir 'classes'
$dex = Join-Path $BuildDir 'dex'
New-Item -ItemType Directory -Force $classes,$dex | Out-Null
$sources = Get-ChildItem "$src/*.java" | Select-Object -ExpandProperty FullName
& $javac -encoding UTF-8 -source 8 -target 8 -classpath $androidJar -d $classes @sources
Check-Exit
$classFiles = Get-ChildItem $classes -Filter '*.class' -Recurse | Select-Object -ExpandProperty FullName
& "$bt/d8.bat" --lib $androidJar --min-api 31 --output $dex @classFiles
Check-Exit
$unsigned = Join-Path $BuildDir 'unsigned.apk'
$resources = Join-Path $BuildDir 'resources.zip'
& "$bt/aapt2.exe" compile --dir "$PSScriptRoot/app/src/main/res" -o $resources
Check-Exit
& "$bt/aapt2.exe" link -o $unsigned --manifest "$PSScriptRoot/app/src/main/AndroidManifest.xml" -I $androidJar $resources
Check-Exit
Push-Location $dex
try { & "$JavaHome/bin/jar.exe" uf $unsigned classes.dex; Check-Exit } finally { Pop-Location }
$aligned = Join-Path $BuildDir 'aligned.apk'
& "$bt/zipalign.exe" -f -p 4 $unsigned $aligned
Check-Exit
if (-not $KeyStore) { $KeyStore = Join-Path $BuildDir 'local-alpha.keystore' }
if (-not (Test-Path $KeyStore)) {
    & "$JavaHome/bin/keytool.exe" -genkeypair -keystore $KeyStore -storepass android -keypass android -alias openphev -keyalg RSA -keysize 3072 -validity 3650 -dname 'CN=Open PHEV local alpha'
    Check-Exit
}
$apk = Join-Path $BuildDir 'open-phev-0.4.1-alpha.apk'
& "$bt/apksigner.bat" sign --ks $KeyStore --ks-key-alias openphev --ks-pass pass:android --key-pass pass:android --out $apk $aligned
Check-Exit
& "$bt/apksigner.bat" verify --verbose $apk
Check-Exit
Write-Output "Built: $apk"
Write-Output 'Local alpha signing only. Preserve the keystore privately for updates; do not commit it.'


