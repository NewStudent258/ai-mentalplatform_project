# ============================================================================
#  Creates desktop shortcuts for the mental health AI platform launcher.
#
#  Run via install-shortcuts.bat, or directly:
#    powershell -ExecutionPolicy Bypass -File install-shortcuts.ps1
# ============================================================================

$ErrorActionPreference = 'Stop'

$root   = Split-Path -Parent $MyInvocation.MyCommand.Path
$target = Join-Path $root 'start.bat'

if (-not (Test-Path $target)) {
    Write-Host "  [X] Cannot find start.bat at: $target"
    exit 1
}

$desktop = [Environment]::GetFolderPath('Desktop')
$shell   = New-Object -ComObject WScript.Shell
$icons   = Join-Path $env:SystemRoot 'System32\shell32.dll'
$cmdExe  = Join-Path $env:SystemRoot 'System32\cmd.exe'

function New-Shortcut {
    param(
        [string]$Name,
        [string]$BatArgs,
        [string]$Description,
        [int]$IconIndex
    )
    $path = Join-Path $desktop "$Name.lnk"
    $lnk  = $shell.CreateShortcut($path)

    # Point at cmd.exe rather than start.bat directly, so we can:
    #   1. keep the console window open when it finishes (MH_KEEP_OPEN=1),
    #      which lets the user read the startup report;
    #   2. pass a mode argument (e.g. "stop") through to the batch file.
    #
    # Note the quoting: cmd.exe needs /c "<whole command>". The batch path must
    # be quoted because the project folder may contain spaces, and the mode
    # argument must stay OUTSIDE those quotes.
    $inner = if ([string]::IsNullOrWhiteSpace($BatArgs)) {
        "set MH_KEEP_OPEN=1&& `"$target`""
    } else {
        "set MH_KEEP_OPEN=1&& `"$target`" $BatArgs"
    }
    $lnk.TargetPath       = $cmdExe
    $lnk.Arguments        = "/c `"$inner`""
    $lnk.WorkingDirectory = $root
    $lnk.Description      = $Description
    $lnk.IconLocation     = "$icons,$IconIndex"
    $lnk.Save()
    Write-Host "  [OK] $path"
    Write-Host "       $cmdExe /c `"$inner`""
}

Write-Host ""
Write-Host "  Creating desktop shortcuts..."
Write-Host "    Target: $target"
Write-Host ""

New-Shortcut -Name 'Start Mental Health AI' `
             -BatArgs '' `
             -Description 'Start the mental health AI platform (backend + frontend)' `
             -IconIndex 137

New-Shortcut -Name 'Stop Mental Health AI' `
             -BatArgs 'stop' `
             -Description 'Stop the mental health AI platform' `
             -IconIndex 131

Write-Host ""
Write-Host "  ============================================================"
Write-Host "    Done. Two shortcuts were placed on your Desktop:"
Write-Host "      - Start Mental Health AI"
Write-Host "      - Stop Mental Health AI"
Write-Host ""
Write-Host "    Double-click ""Start ..."" to launch the project."
Write-Host "    Double-click ""Stop ...""  to shut it down."
Write-Host "  ============================================================"
Write-Host ""
