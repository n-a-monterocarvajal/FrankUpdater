param([switch]$CoreOnly)

# Use the installed JDK or Android Studio's bundled JDK without starting the IDE.
$ErrorActionPreference = 'Stop'
if (-not $env:GRADLE_USER_HOME) {
    $env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'
}
if (-not $env:JAVA_HOME) {
    $bundledJdk = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
    if (Test-Path -LiteralPath (Join-Path $bundledJdk 'bin\java.exe')) {
        $env:JAVA_HOME = $bundledJdk
    }
}
$gradleTasks = if ($CoreOnly) { @(':core:compatibility:test', ':core:archive:test') } else { @('lintDebug', 'test', 'assembleDebug') }
Push-Location (Join-Path $PSScriptRoot '..')
try {
    & .\gradlew.bat --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8' '-Pkotlin.compiler.execution.strategy=in-process' @gradleTasks
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed ($LASTEXITCODE)." }
} finally {
    Pop-Location
}
