# Live terminal demonstration

Open http://127.0.0.1:5173 and select Start Here. The Live terminal monitor panel refreshes every two seconds.

This feature observes actual HTTP connections to `/api/live/probe` on the NETRA backend. It records socket source/destination IPs, ports, timestamps and database flow IDs. It does not sniff other applications, Wi-Fi traffic, arbitrary IPs, or packet payloads. Packet/byte counts are left unknown. Local requests correctly show the loopback IP 127.0.0.1.

## Startup after a reboot

Run the database check once from PowerShell:

```powershell
cd C:\Users\DELL\NETRA
powershell -ExecutionPolicy Bypass -File .\scripts\setup-postgres.ps1
```

Keep the backend running in one terminal:

```powershell
cd C:\Users\DELL\NETRA\backend
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25'
.\mvnw.cmd spring-boot:run
```

Keep the frontend running in a second terminal:

```powershell
cd C:\Users\DELL\NETRA\frontend
npm.cmd run dev
```

Do not start a second backend/frontend if the ports are already in use. Python is not required for this monitor.

## Run the demonstration yourself in a third terminal

```powershell
cd C:\Users\DELL\NETRA
powershell -ExecutionPolicy Bypass -File .\scripts\live-demo.ps1 Start
powershell -ExecutionPolicy Bypass -File .\scripts\live-demo.ps1 Request
```

One event appears in the live table. A single request does not trigger an alert. You can also send it directly:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/live/probe
```

Then send twelve bounded requests to this same computer:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\live-demo.ps1 Burst
```

Rule: ten requests from the same IP within ten seconds. The tenth request creates one MEDIUM demo incident with the source, destination, session and request count. The rule is deliberately small for classroom demonstrations and does not prove malicious activity. Further requests from that IP in this session do not create duplicate incidents.

Open Incident Queue, click Sync if needed, and look for `Live demo request burst from 127.0.0.1`. Its Audit Trail contains a creation record. Existing forecast/CSV incidents remain and are unrelated to the live session.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\live-demo.ps1 Status
powershell -ExecutionPolicy Bypass -File .\scripts\live-demo.ps1 Stop
```

Sessions expire after 30 minutes or on backend restart. Start resets the live session display and per-source alert suppression; it does not delete saved flows or incidents. The display retains the latest 100 events. Persistence failures return errors rather than displaying fabricated success.

Historical forecast incidents created before the audit fix may have no creation record. New forecast incidents include creation evidence and suppress repeated open alerts of the same title for ten minutes.
