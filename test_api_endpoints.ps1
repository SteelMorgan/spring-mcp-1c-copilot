# Тест различных API endpoints для 1С:Напарник
$baseUrl = "https://code.1c.ai"
$token = "mKGMkWpAZiHkChI3OkmcYKT_P4agV6p4NaSZmcdvUJ8"
$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
}

# Тест различных endpoints
$endpoints = @(
    "/api/v1/chat",
    "/api/v1/conversations", 
    "/api/v1/sessions",
    "/api/v1/messages",
    "/api/chat",
    "/api/conversations",
    "/chat",
    "/conversations"
)

foreach ($endpoint in $endpoints) {
    $url = "$baseUrl$endpoint"
    Write-Host "Testing: $url"
    
    try {
        $response = Invoke-RestMethod -Uri $url -Method GET -Headers $headers -ErrorAction Stop
        Write-Host "SUCCESS: $endpoint - $($response | ConvertTo-Json -Depth 2)"
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        Write-Host "FAILED: $endpoint - Status: $statusCode - $($_.Exception.Message)"
    }
    Write-Host "---"
}
