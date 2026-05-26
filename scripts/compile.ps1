$Root = Split-Path -Parent $PSScriptRoot
New-Item -ItemType Directory -Force "$Root\out" | Out-Null
Get-ChildItem -Recurse "$Root\src\main\java" -Filter *.java | ForEach-Object { $_.FullName } | Set-Content "$Root\sources.txt"
javac --release 21 -d "$Root\out" "@$Root\sources.txt"
Write-Host "Compilation successful. Classes in $Root\out"
