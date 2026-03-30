param(
    [string]$BaseUrl = "http://localhost:8080",
    [switch]$StartApp
)

$ErrorActionPreference = "Stop"

function Write-Step($status, $name, $detail) {
    $pad = $status.PadRight(7)
    Write-Output "$pad $name - $detail"
}

function Invoke-Json {
    param(
        [string]$Method,
        [string]$Url,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )

    $jsonBody = $null
    if ($null -ne $Body) {
        $jsonBody = $Body | ConvertTo-Json -Depth 6
    }

    try {
        if ($null -ne $jsonBody) {
            $resp = Invoke-RestMethod -Method $Method -Uri $Url -Headers $Headers -ContentType "application/json" -Body $jsonBody
        } else {
            $resp = Invoke-RestMethod -Method $Method -Uri $Url -Headers $Headers
        }
        return @{ ok = $true; status = 200; body = $resp }
    } catch {
        $status = 0
        $body = $null
        if ($_.Exception.Response -ne $null) {
            try { $status = [int]$_.Exception.Response.StatusCode } catch {}
            try {
                $stream = $_.Exception.Response.GetResponseStream()
                if ($stream) {
                    $reader = New-Object System.IO.StreamReader($stream)
                    $raw = $reader.ReadToEnd()
                    $reader.Close()
                    if ($raw) {
                        try { $body = $raw | ConvertFrom-Json } catch { $body = $raw }
                    }
                }
            } catch {}
        }
        return @{ ok = $false; status = $status; body = $body }
    }
}

$proc = $null
if ($StartApp) {
    Write-Output "Starting app..."
    $out = "smoke_app.out.log"
    $err = "smoke_app.err.log"
    if (Test-Path $out) { Remove-Item $out -Force }
    if (Test-Path $err) { Remove-Item $err -Force }
    $proc = Start-Process -FilePath ".\mvnw.cmd" -ArgumentList "spring-boot:run" -RedirectStandardOutput $out -RedirectStandardError $err -PassThru

    $ready = $false
    for ($i = 0; $i -lt 90; $i++) {
        Start-Sleep -Seconds 1
        $probe = Invoke-Json -Method "GET" -Url "$BaseUrl/api/flights"
        if ($probe.ok) {
            $ready = $true
            break
        }
    }

    if (-not $ready) {
        Write-Step "FAIL" "startup" "App did not become ready"
        if ($proc -and -not $proc.HasExited) { Stop-Process -Id $proc.Id -Force }
        exit 2
    }
    Write-Step "OK" "startup" "App is responding at $BaseUrl"
}

$criticalFailed = $false

try {
    $flightsResp = Invoke-Json -Method "GET" -Url "$BaseUrl/api/flights"
    if (-not $flightsResp.ok) {
        Write-Step "FAIL" "flights" "GET /api/flights returned $($flightsResp.status)"
        $criticalFailed = $true
        throw "critical"
    }
    $flightCount = @($flightsResp.body).Count
    Write-Step "OK" "flights" "$flightCount flights available"

    if ($flightCount -lt 1) {
        Write-Step "FAIL" "seed-data" "No flights found for booking smoke"
        $criticalFailed = $true
        throw "critical"
    }

    $suffix = [Guid]::NewGuid().ToString("N").Substring(0, 8)
    $username = "smoke_$suffix"
    $email = "$username@flyte.local"
    $password = "pass12345"

    $registerResp = Invoke-Json -Method "POST" -Url "$BaseUrl/api/auth/register" -Body @{
        username = $username
        email = $email
        password = $password
        role = "PASSENGER"
    }
    if (-not $registerResp.ok) {
        Write-Step "FAIL" "register" "POST /api/auth/register returned $($registerResp.status)"
        $criticalFailed = $true
        throw "critical"
    }
    Write-Step "OK" "register" "Created user $username"

    $loginResp = Invoke-Json -Method "POST" -Url "$BaseUrl/api/auth/login" -Body @{
        username = $username
        password = $password
    }
    if (-not $loginResp.ok -or -not $loginResp.body.token) {
        Write-Step "FAIL" "login" "POST /api/auth/login returned $($loginResp.status)"
        $criticalFailed = $true
        throw "critical"
    }
    $token = $loginResp.body.token
    $headers = @{ Authorization = "Bearer $token" }
    Write-Step "OK" "login" "JWT issued for $username"

    $myBefore = Invoke-Json -Method "GET" -Url "$BaseUrl/api/bookings/my" -Headers $headers
    if (-not $myBefore.ok) {
        Write-Step "FAIL" "my-bookings" "GET /api/bookings/my returned $($myBefore.status)"
        $criticalFailed = $true
        throw "critical"
    }
    Write-Step "OK" "my-bookings" "Endpoint reachable"

    $flightNumber = @($flightsResp.body)[0].flightNumber
    $seat = "A$((Get-Random -Minimum 10 -Maximum 90))"
    $bookResp = Invoke-Json -Method "POST" -Url "$BaseUrl/api/bookings" -Headers $headers -Body @{
        flightNumber = $flightNumber
        passengerName = $username
        seatClass = "ECONOMY"
        seatNumber = $seat
    }
    if (-not $bookResp.ok -or -not $bookResp.body.id) {
        Write-Step "FAIL" "booking" "POST /api/bookings returned $($bookResp.status)"
        $criticalFailed = $true
        throw "critical"
    }
    $bookingId = $bookResp.body.id
    Write-Step "OK" "booking" "Created booking #$bookingId on $flightNumber seat $seat"

    # Optional checks - external gateway/network dependent
    $mpesaResp = Invoke-Json -Method "POST" -Url "$BaseUrl/api/payments/mpesa/pay" -Headers $headers -Body @{
        bookingId = $bookingId
        phoneNumber = "254712345678"
    }
    if ($mpesaResp.ok) {
        Write-Step "OK" "mpesa" "STK push endpoint accepted request"
    } else {
        Write-Step "WARN" "mpesa" "Returned $($mpesaResp.status) (external dependency check)"
    }

    $stripeResp = Invoke-Json -Method "POST" -Url "$BaseUrl/api/payments/stripe/intent/$bookingId" -Headers $headers
    if ($stripeResp.ok -and $stripeResp.body.clientSecret) {
        Write-Step "OK" "stripe-intent" "Client secret returned"
    } else {
        Write-Step "WARN" "stripe-intent" "Returned $($stripeResp.status) (external dependency check)"
    }
}
catch {
    if ($_ -ne "critical") {
        Write-Step "FAIL" "smoke" $_.Exception.Message
        $criticalFailed = $true
    }
}
finally {
    if ($proc -and -not $proc.HasExited) {
        Stop-Process -Id $proc.Id -Force
        Write-Output "Stopped app process."
    }
}

if ($criticalFailed) {
    Write-Output "RESULT: FAIL (critical checks)"
    exit 1
}

Write-Output "RESULT: PASS (critical checks)"
exit 0
