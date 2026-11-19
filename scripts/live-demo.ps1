param([ValidateSet('Start','Request','Burst','Status','Stop')][string]$Action = 'Status')
$ErrorActionPreference = 'Stop'
$demoBase = 'http://127.0.0.1:8080/api/live'
switch ($Action) {
    'Start' { Invoke-RestMethod "$demoBase/start" -Method Post | ConvertTo-Json -Depth 5 }
    'Request' { Invoke-RestMethod "$demoBase/probe" | ConvertTo-Json }
    'Burst' {
        # Twelve bounded requests to this computer; no scanning or external target.
        1..12 | ForEach-Object {
            $event = Invoke-RestMethod "$demoBase/probe"
            Write-Host ("Request {0}: {1} -> {2}, window={3}, incident={4}" -f $_, $event.sourceIp, $event.destinationIp, $event.requestsInWindow, $event.incidentId)
            Start-Sleep -Milliseconds 150
        }
    }
    'Status' { Invoke-RestMethod "$demoBase/status" | ConvertTo-Json -Depth 5 }
    'Stop' { Invoke-RestMethod "$demoBase/stop" -Method Post | ConvertTo-Json -Depth 5 }
}
