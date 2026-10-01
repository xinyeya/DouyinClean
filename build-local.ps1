param(
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$Jdk = $env:JAVA_HOME,
    [string]$BuildTools = '36.0.0'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not $Jdk -or -not (Test-Path "$Jdk\bin\javac.exe")) { throw 'Set JAVA_HOME or pass -Jdk to a JDK 17+ installation.' }
$project = $PSScriptRoot
$buildPath = Join-Path $project 'app\build\manual'
$toolsPath = Join-Path $Sdk "build-tools\$BuildTools"
$androidJar = Join-Path $Sdk 'platforms\android-36\android.jar'
$env:JAVA_HOME = $Jdk
function Run-Checked([string]$program, [string[]]$arguments) {
    & $program @arguments
    if ($LASTEXITCODE -ne 0) { throw "Build command failed: $program (exit $LASTEXITCODE)" }
}
New-Item -ItemType Directory -Force "$buildPath\classes", "$buildPath\dex", "$buildPath\generated", "$project\.signing" | Out-Null
$api = Join-Path $project 'app\libs\xposed-api-82.jar'
foreach ($folder in @('classes','dex','generated')) {
    $cleanPath = [IO.Path]::GetFullPath((Join-Path $buildPath $folder))
    if (-not $cleanPath.StartsWith([IO.Path]::GetFullPath($project) + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Build path is outside project.' }
    if (Test-Path -LiteralPath $cleanPath) { Remove-Item -LiteralPath $cleanPath -Recurse -Force }
    New-Item -ItemType Directory -Path $cleanPath -Force | Out-Null
}
$manifest = (Get-Content "$project\app\src\main\AndroidManifest.xml" -Raw).Replace('<manifest xmlns:', '<manifest package="local.douyin.clean" android:versionCode="5" android:versionName="0.3.2" xmlns:').Replace('<uses-permission', '<uses-sdk android:minSdkVersion="29" android:targetSdkVersion="34" /><uses-permission')
$manifestPath = Join-Path $buildPath 'AndroidManifest.xml'
[IO.File]::WriteAllText($manifestPath, $manifest, [Text.UTF8Encoding]::new($false))
Run-Checked "$toolsPath\aapt.exe" @('package','-f','-M',$manifestPath,'-S',"$project\app\src\main\res",'-A',"$project\app\src\main\assets",'-I',$androidJar,'-J',"$buildPath\generated",'-F',"$buildPath\unsigned.apk")
$javaFiles = @(Get-ChildItem "$project\app\src\main\java" -Recurse -Filter '*.java' | ForEach-Object FullName)
Run-Checked "$Jdk\bin\javac.exe" (@('--release','8','-encoding','UTF-8','-cp',"$androidJar;$api",'-d',"$buildPath\classes") + $javaFiles)
Run-Checked "$Jdk\bin\jar.exe" @('cf',"$buildPath\classes.jar",'-C',"$buildPath\classes",'.')
Run-Checked "$toolsPath\d8.bat" @('--lib',$androidJar,'--classpath',$api,'--min-api','29','--output',"$buildPath\dex", "$buildPath\classes.jar")
Push-Location "$buildPath\dex"
try { Run-Checked "$toolsPath\aapt.exe" @('add',"$buildPath\unsigned.apk",'classes.dex') } finally { Pop-Location }
Run-Checked "$toolsPath\zipalign.exe" @('-p','-f','4',"$buildPath\unsigned.apk", "$buildPath\aligned.apk")
$key = Join-Path $project '.signing\local-test.jks'
if (-not (Test-Path $key)) {
    Run-Checked "$Jdk\bin\keytool.exe" @('-genkeypair','-keystore',$key,'-storepass','android','-keypass','android','-alias','androiddebugkey','-dname','CN=Local Test, O=Local, C=CN','-keyalg','RSA','-keysize','2048','-validity','10000')
}
$outputApk = Join-Path $buildPath 'DouyinClean-0.3.2.apk'
Run-Checked "$toolsPath\apksigner.bat" @('sign','--ks',$key,'--ks-pass','pass:android','--key-pass','pass:android','--out',$outputApk,"$buildPath\aligned.apk")
Run-Checked "$toolsPath\apksigner.bat" @('verify','--verbose',$outputApk)
Write-Host "APK: $outputApk"

