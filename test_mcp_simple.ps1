# Простой тест MCP сервера без кириллицы
$uri = "http://localhost:8000/mcp"
$headers = @{
    "Content-Type" = "application/json"
}

# Тест вызова инструмента с английским текстом
$callBody = @{
    jsonrpc = "2.0"
    id = "3"
    method = "tools/call"
    params = @{
        name = "ask_1c_ai"
        arguments = @{
            question = "How to create a catalog in 1C?"
        }
    }
} | ConvertTo-Json -Depth 10

Write-Host "Testing tool call with English text..."
try {
    $callResponse = Invoke-RestMethod -Uri $uri -Method POST -Headers $headers -Body $callBody
    Write-Host "Call response: $($callResponse | ConvertTo-Json -Depth 10)"
} catch {
    Write-Host "Error: $($_.Exception.Message)"
    Write-Host "Response: $($_.Exception.Response)"
}
