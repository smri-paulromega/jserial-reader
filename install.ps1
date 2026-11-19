<#
.SYNOPSIS
    Puts readblob on your PATH, so it runs from any folder in cmd, PowerShell and Git Bash.

.DESCRIPTION
    Builds the jar if it is missing, adds this folder's bin directory to your *user* PATH
    (HKCU\Environment; no administrator rights needed, the system PATH is not touched) and sets
    JSERIAL_READER_HOME. Open a new terminal afterwards. Running it twice is harmless.

.PARAMETER Uninstall
    Removes the bin directory from your user PATH and deletes JSERIAL_READER_HOME.

.EXAMPLE
    .\install.cmd
    .\install.cmd -Uninstall
#>
param([switch]$Uninstall)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$bin = Join-Path $root 'bin'

if (-not $Uninstall -and
    -not (Test-Path (Join-Path $root 'lib\jserial-reader.jar')) -and
    -not (Test-Path (Join-Path $root 'build\jserial-reader.jar'))) {
    & (Join-Path $root 'build.cmd')
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

# Read the raw value: entries such as %USERPROFILE%\bin must stay unexpanded, and the value must be
# written back as REG_EXPAND_SZ. [Environment]::SetEnvironmentVariable would expand and flatten them.
$key = 'HKCU:\Environment'
$raw = (Get-Item $key).GetValue('Path', '', 'DoNotExpandEnvironmentNames')
$entries = @($raw -split ';' | Where-Object { $_ -ne '' -and $_.TrimEnd('\') -ne $bin.TrimEnd('\') })
if (-not $Uninstall) {
    $entries += $bin
}
New-ItemProperty -Path $key -Name 'Path' -Value ($entries -join ';') -PropertyType ExpandString -Force | Out-Null

# Setting a variable through .NET also broadcasts WM_SETTINGCHANGE, so new terminals see the new PATH.
if ($Uninstall) {
    [Environment]::SetEnvironmentVariable('JSERIAL_READER_HOME', $null, 'User')
    Write-Host "Removed $bin from your user PATH."
} else {
    [Environment]::SetEnvironmentVariable('JSERIAL_READER_HOME', $root, 'User')
    Write-Host "Added $bin to your user PATH."
    Write-Host "Open a new terminal and run: readblob --version"
}
