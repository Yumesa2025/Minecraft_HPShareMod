param(
    [string]$ServerRoot = $PSScriptRoot,
    [string]$JavaExecutable = 'java',
    [string]$JarFile = 'fabric-server-launch.jar',
    [string]$MinMemory = '1G',
    [string]$MaxMemory = '2G',
    [switch]$ProcessPendingResetOnly,
    [switch]$AcceptEula
)

$ErrorActionPreference = 'Stop'

# 콘솔을 UTF-8 로 맞춘다. Windows 콘솔은 기본이 CP949 인데 Java 와 이 스크립트는
# UTF-8 로 찍으므로, 맞춰 두지 않으면 한글이 전부 깨져 보인다.
try {
    chcp 65001 > $null
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    $OutputEncoding = [System.Text.Encoding]::UTF8
} catch {
    Write-Host '[SharedFate] 콘솔 인코딩을 UTF-8 로 바꾸지 못했습니다. 한글이 깨질 수 있습니다.'
}
$markerName = '.sharedfate-world-reset.pending'
$markerHeader = 'sharedfate-world-reset-v1'
$runStateName = 'sharedfate-run-state.json'
$eulaName = 'eula.txt'
$eulaUrl = 'https://www.minecraft.net/eula'

function Get-NormalizedFullPath {
    param([Parameter(Mandatory = $true)][string]$Path)
    return [System.IO.Path]::GetFullPath($Path).TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar)
}

# eula.txt 에 동의가 적혀 있는지 본다. 주석(#)은 건너뛰고 `eula=true` 한 줄만 찾는다.
function Test-EulaAccepted {
    param([Parameter(Mandatory = $true)][string]$Root)
    $eulaPath = Join-Path $Root $eulaName
    if (-not (Test-Path -LiteralPath $eulaPath -PathType Leaf)) {
        return $false
    }
    foreach ($line in @(Get-Content -LiteralPath $eulaPath -ErrorAction Stop)) {
        $trimmed = $line.Trim()
        if ($trimmed.StartsWith('#')) {
            continue
        }
        if ($trimmed -imatch '^eula\s*=\s*true$') {
            return $true
        }
    }
    return $false
}

<#
.SYNOPSIS
동의가 없으면 물어보고 eula.txt 를 만든다.

.DESCRIPTION
eula=true 는 설정값이 아니라 동의 서명이므로 배포 ZIP 에 미리 넣어 둘 수 없다. 그래서
서버를 처음 켤 때 여기서 한 번 묻는다. 이미 동의가 적혀 있으면 아무 말도 하지 않는다.

창이 없는 곳(작업 스케줄러·호스팅 자동 실행)에서는 물어볼 수 없으므로 -AcceptEula 로
미리 동의를 넘기게 하고, 그것도 없으면 무엇을 해야 하는지 적고 멈춘다.
#>
function Confirm-Eula {
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [switch]$PreAccepted
    )
    if (Test-EulaAccepted -Root $Root) {
        return
    }

    $eulaPath = Join-Path $Root $eulaName
    if (-not $PreAccepted) {
        if (-not [Environment]::UserInteractive) {
            throw "Minecraft EULA 에 동의해야 서버를 켤 수 있습니다. 이 창에서는 물어볼 수 " +
                "없으니 $eulaUrl 를 읽고 -AcceptEula 를 붙여 다시 실행하거나, " +
                "$eulaPath 에 eula=true 를 적으십시오."
        }
        Write-Host ''
        Write-Host '[SharedFate] Minecraft EULA 에 동의해야 서버를 켤 수 있습니다.' -ForegroundColor Yellow
        Write-Host "           $eulaUrl"
        Write-Host '           동의하시면 y 를, 그만두시려면 그 밖의 아무 글자를 입력하십시오.'
        $answer = Read-Host '동의하십니까? (y/N)'
        if ($answer.Trim() -inotmatch '^(y|yes|예)$') {
            throw 'EULA 에 동의하지 않아 서버를 켜지 않았습니다.'
        }
    }

    # BOM 없이 쓴다. 마인크래프트는 이 파일을 Properties 로 읽는데 BOM 이 앞에 붙으면
    # 첫 키 이름이 깨져 동의를 못 읽는다.
    $encoder = New-Object System.Text.UTF8Encoding($false)
    $contents = "# $eulaUrl" + [Environment]::NewLine + 'eula=true' + [Environment]::NewLine
    [System.IO.File]::WriteAllText($eulaPath, $contents, $encoder)
    Write-Host "[SharedFate] 동의를 $eulaName 에 기록했습니다." -ForegroundColor Green
}

function Read-ValidatedRunState {
    param([Parameter(Mandatory = $true)][string]$Root)
    $statePath = Join-Path $Root $runStateName
    if (-not (Test-Path -LiteralPath $statePath)) {
        return [pscustomobject]@{
            runNumber = 1
            status = 'playing'
            winningTeam = ''
        }
    }
    if (-not (Test-Path -LiteralPath $statePath -PathType Leaf)) {
        throw "회차 상태가 파일이 아닙니다: $statePath"
    }
    try {
        $state = Get-Content -LiteralPath $statePath -Raw -Encoding UTF8 | ConvertFrom-Json
    } catch {
        throw "회차 상태 JSON이 손상되었습니다: $statePath"
    }
    $runNumber = 0
    if (-not [int]::TryParse([string]$state.runNumber, [ref]$runNumber) -or $runNumber -lt 1) {
        throw "회차 번호가 올바르지 않습니다: $($state.runNumber)"
    }
    $status = [string]$state.status
    if ($status -cne 'playing' -and $status -cne 'victory') {
        throw "회차 상태 값이 올바르지 않습니다: $status"
    }
    return [pscustomobject]@{
        runNumber = $runNumber
        status = $status
        winningTeam = [string]$state.winningTeam
    }
}

function Save-RunState {
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)]$State
    )
    $statePath = Join-Path $Root $runStateName
    $temporary = "$statePath.tmp"
    $json = $State | ConvertTo-Json
    $encoder = New-Object System.Text.UTF8Encoding($true)
    [System.IO.File]::WriteAllText($temporary, $json + [Environment]::NewLine, $encoder)
    Move-Item -LiteralPath $temporary -Destination $statePath -Force -ErrorAction Stop
}

function Invoke-ValidatedWorldReset {
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)][string]$Marker
    )

    $resolvedRoot = Get-NormalizedFullPath -Path $Root
    $resolvedMarker = Get-NormalizedFullPath -Path $Marker
    $expectedMarker = Get-NormalizedFullPath -Path (Join-Path $resolvedRoot $markerName)
    if (-not [string]::Equals($resolvedMarker, $expectedMarker,
            [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "허용되지 않은 초기화 표식 경로입니다: $resolvedMarker"
    }
    if (-not (Test-Path -LiteralPath $resolvedMarker -PathType Leaf)) {
        throw "월드 초기화 표식이 없습니다: $resolvedMarker"
    }

    $lines = @(Get-Content -LiteralPath $resolvedMarker -Encoding UTF8)
    if ($lines.Count -ne 2 -or $lines[0] -cne $markerHeader) {
        throw "월드 초기화 표식 형식이 올바르지 않습니다: $resolvedMarker"
    }
    $pathRoot = [System.IO.Path]::GetPathRoot($lines[1])
    if (-not [System.IO.Path]::IsPathRooted($lines[1]) -or
            [string]::IsNullOrWhiteSpace($pathRoot) -or
            -not $pathRoot.EndsWith([System.IO.Path]::DirectorySeparatorChar)) {
        throw "월드 경로는 절대 경로여야 합니다: $($lines[1])"
    }

    $worldTarget = Get-NormalizedFullPath -Path $lines[1]
    $worldParent = [System.IO.Directory]::GetParent($worldTarget)
    if ($null -eq $worldParent -or
            -not [string]::Equals($worldParent.FullName, $resolvedRoot,
                [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "월드 폴더는 서버 루트 바로 아래여야 합니다: $worldTarget"
    }
    if ([string]::Equals($worldTarget, $resolvedRoot,
            [System.StringComparison]::OrdinalIgnoreCase)) {
        throw '서버 루트 자체는 절대 삭제할 수 없습니다.'
    }
    if (-not (Test-Path -LiteralPath $worldTarget -PathType Container)) {
        throw "삭제할 월드 폴더가 없습니다: $worldTarget"
    }

    $targetItem = Get-Item -LiteralPath $worldTarget -Force
    if (-not $targetItem.PSIsContainer) {
        throw "월드 대상이 폴더가 아닙니다: $worldTarget"
    }
    if (($targetItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "링크·정션 월드는 자동 삭제하지 않습니다: $worldTarget"
    }

    Write-Host "[SharedFate] 검증된 월드 한 폴더를 초기화합니다: $worldTarget" -ForegroundColor Yellow
    Remove-Item -LiteralPath $worldTarget -Recurse -Force -ErrorAction Stop
    if (Test-Path -LiteralPath $worldTarget) {
        throw "월드 폴더 삭제가 완료되지 않았습니다: $worldTarget"
    }
    $runState = Read-ValidatedRunState -Root $resolvedRoot
    if ($runState.runNumber -lt [int]::MaxValue) {
        $runState.runNumber++
    }
    $runState.status = 'playing'
    $runState.winningTeam = ''
    Save-RunState -Root $resolvedRoot -State $runState
    Remove-Item -LiteralPath $resolvedMarker -Force -ErrorAction Stop
    Write-Host "[SharedFate] 월드 초기화 완료. $($runState.runNumber)회차 새 월드를 생성합니다." -ForegroundColor Green
}

$resolvedServerRoot = Get-NormalizedFullPath -Path $ServerRoot
if (-not (Test-Path -LiteralPath $resolvedServerRoot -PathType Container)) {
    throw "서버 폴더가 없습니다: $resolvedServerRoot"
}
$markerPath = Join-Path $resolvedServerRoot $markerName
$initialRunState = Read-ValidatedRunState -Root $resolvedServerRoot
if (-not (Test-Path -LiteralPath (Join-Path $resolvedServerRoot $runStateName))) {
    Save-RunState -Root $resolvedServerRoot -State $initialRunState
}

if ($ProcessPendingResetOnly) {
    Invoke-ValidatedWorldReset -Root $resolvedServerRoot -Marker $markerPath
    exit 0
}

if (Test-Path -LiteralPath $markerPath) {
    throw "이전 실행의 월드 초기화 표식이 남아 있습니다. 자동 재시도를 막았습니다: $markerPath"
}

$resolvedJar = Get-NormalizedFullPath -Path (Join-Path $resolvedServerRoot $JarFile)
$jarParent = [System.IO.Directory]::GetParent($resolvedJar)
if ($null -eq $jarParent -or
        -not [string]::Equals($jarParent.FullName, $resolvedServerRoot,
            [System.StringComparison]::OrdinalIgnoreCase) -or
        -not (Test-Path -LiteralPath $resolvedJar -PathType Leaf)) {
    throw "서버 실행 JAR가 서버 루트 바로 아래에 없습니다: $resolvedJar"
}

Confirm-Eula -Root $resolvedServerRoot -PreAccepted:$AcceptEula

Set-Location -LiteralPath $resolvedServerRoot
while ($true) {
    Write-Host '[SharedFate] Minecraft 서버를 시작합니다.' -ForegroundColor Cyan
    # Java 가 콘솔로 내보내는 글자도 UTF-8 로 고정한다. stdout/stderr 인코딩을 지정하지
    # 않으면 Windows 에서는 콘솔 코드페이지를 따라가 위에서 맞춰 둔 것과 어긋난다.
    & $JavaExecutable "-Xms$MinMemory" "-Xmx$MaxMemory" `
        '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' `
        '-jar' $resolvedJar 'nogui'
    $serverExitCode = $LASTEXITCODE

    if (-not (Test-Path -LiteralPath $markerPath)) {
        Write-Host "[SharedFate] 서버가 종료되었습니다. exit=$serverExitCode"
        exit $serverExitCode
    }

    Invoke-ValidatedWorldReset -Root $resolvedServerRoot -Marker $markerPath
    Start-Sleep -Seconds 2
}
