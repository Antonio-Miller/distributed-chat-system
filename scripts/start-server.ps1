$Root = Split-Path -Parent $PSScriptRoot
$Port = if ($args.Count -ge 1) { $args[0] } else { "12345" }
& "$PSScriptRoot\compile.ps1"
java -cp "$Root\out" chat.server.ChatServer $Port
