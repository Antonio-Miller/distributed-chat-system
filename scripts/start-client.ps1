$Root = Split-Path -Parent $PSScriptRoot
$Host_ = if ($args.Count -ge 1) { $args[0] } else { "localhost" }
$Port = if ($args.Count -ge 2) { $args[1] } else { "12345" }
& "$PSScriptRoot\compile.ps1"
java -cp "$Root\out" chat.client.ChatClient $Host_ $Port
