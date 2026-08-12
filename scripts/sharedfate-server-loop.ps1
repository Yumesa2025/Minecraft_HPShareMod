param(
    [string]$ServerRoot = $PSScriptRoot,
    [string]$JavaExecutable = 'java',
    [string]$JarFile = 'fabric-server-launch.jar',
    [string]$MinMemory = '1G',
    [string]$MaxMemory = '2G',
    [switch]$ProcessPendingResetOnly
)

$ErrorActionPreference = 'Stop'
$markerName = '.sharedfate-world-reset.pending'
$markerHeader = 'sharedfate-world-reset-v1'
$runStateName = 'sharedfate-run-state.json'

function Get-NormalizedFullPath {
    param([Parameter(Mandatory = $true)][string]$Path)
    return [System.IO.Path]::GetFullPath($Path).TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar)
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

Set-Location -LiteralPath $resolvedServerRoot
while ($true) {
    Write-Host '[SharedFate] Minecraft 서버를 시작합니다.' -ForegroundColor Cyan
    & $JavaExecutable "-Xms$MinMemory" "-Xmx$MaxMemory" '-jar' $resolvedJar 'nogui'
    $serverExitCode = $LASTEXITCODE

    if (-not (Test-Path -LiteralPath $markerPath)) {
        Write-Host "[SharedFate] 서버가 종료되었습니다. exit=$serverExitCode"
        exit $serverExitCode
    }

    Invoke-ValidatedWorldReset -Root $resolvedServerRoot -Marker $markerPath
    Start-Sleep -Seconds 2
}
