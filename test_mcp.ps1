# Тест MCP сервера
$uri = "http://localhost:8000/mcp"
$headers = @{
    "Content-Type" = "application/json"
}

# Тест инициализации
$initBody = @{
    jsonrpc = "2.0"
    id = "1"
    method = "initialize"
    params = @{
        protocolVersion = "2025-06-18"
        capabilities = @{
            tools = $true
            prompts = $true
            resources = $true
            logging = $false
        }
        clientInfo = @{
            name = "cursor-vscode"
            version = "1.0.0"
        }
    }
} | ConvertTo-Json -Depth 10

Write-Host "Testing MCP initialization..."
$initResponse = Invoke-RestMethod -Uri $uri -Method POST -Headers $headers -Body $initBody
Write-Host "Init response: $($initResponse | ConvertTo-Json -Depth 10)"

# Тест списка инструментов
$toolsBody = @{
    jsonrpc = "2.0"
    id = "2"
    method = "tools/list"
    params = @{}
} | ConvertTo-Json -Depth 10

Write-Host "Testing tools list..."
$toolsResponse = Invoke-RestMethod -Uri $uri -Method POST -Headers $headers -Body $toolsBody
Write-Host "Tools response: $($toolsResponse | ConvertTo-Json -Depth 10)"

# Тест вызова инструмента
$callBody = @{
    jsonrpc = "2.0"
    id = "3"
    method = "tools/call"
    params = @{
        name = "ask_1c_ai"
        arguments = @{
            question = "Как создать справочник в 1С?"
        }
    }
} | ConvertTo-Json -Depth 10

Write-Host "Testing tool call..."
$callResponse = Invoke-RestMethod -Uri $uri -Method POST -Headers $headers -Body $callBody
Write-Host "Call response: $($callResponse | ConvertTo-Json -Depth 10)"
