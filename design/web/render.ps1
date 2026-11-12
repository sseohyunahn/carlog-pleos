# CarLog 시안 PNG 다시 만들기 (Windows PowerShell)
# 사용: design\web 폴더에서  .\render.ps1            -> 모든 화면
#       .\render.ps1 -Only 01-board                 -> 한 화면만
# Chrome(또는 Edge)의 headless 모드로 screens\*.html 을 ..\png\ 에 저장해요. 글꼴(Asta Sans)을 받으려면 인터넷이 필요해요.
param([string]$Only = "")

$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$out = Join-Path (Split-Path -Parent $here) "png"
New-Item -ItemType Directory -Force $out | Out-Null

$browser = @(
  "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
  "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
  "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe",
  "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe"
) | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $browser) { throw "Chrome 또는 Edge를 찾지 못했어요." }

# 화면 크기: half = 지도 옆 1:1 분할 창(1060x1256), full = 앱 영역 전체(2120x1256), display = 차량 화면 전체(2560x1440)
$variants = @(
  @{ theme = "night"; frame = "half"; w = 1060; h = 1256 },
  @{ theme = "day";   frame = "half"; w = 1060; h = 1256 },
  @{ theme = "night"; frame = "full"; w = 2120; h = 1256 }
)

$pages = Get-ChildItem (Join-Path $here "screens") -Filter "*.html" | Sort-Object Name
foreach ($p in $pages) {
  $name = [IO.Path]::GetFileNameWithoutExtension($p.Name)
  if ($Only -and $name -ne $Only) { continue }
  $list = $variants
  if ($name -like "00-*") { $list = @(@{ theme = "night"; frame = "display"; w = 2560; h = 1440 }) }
  if ($name -eq "00-foundations") { $list = @(@{ theme = "night"; frame = "sheet"; w = 2560; h = 1600 }) }
  foreach ($v in $list) {
    $png = Join-Path $out ("{0}_{1}_{2}.png" -f $name, $v.theme, $v.frame)
    $url = "file:///" + ($p.FullName -replace '\\', '/') + "?theme=$($v.theme)&frame=$($v.frame)"
    $ErrorActionPreference = "Continue"   # Chrome writes progress to stderr
    & $browser --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 `
      --virtual-time-budget=6000 "--window-size=$($v.w),$($v.h)" "--screenshot=$png" $url 2>&1 | Out-Null
    $ErrorActionPreference = "Stop"
    if (-not (Test-Path $png)) { throw "PNG를 만들지 못했어요: $png" }
    Write-Host "saved $png"
  }
}
