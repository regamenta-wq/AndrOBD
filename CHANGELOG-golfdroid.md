# GolfDroid Changelog

## v0.1.0-mvp — Faza 1 (2026-11-08)

- Fork fr3ts0n/AndrOBD (GPL-3.0), NOTICE s atribucijama (fr3ts0n/AndrOBD, Wal33D/dtc-database, Shreyamkmr/AgenticCar, Ircama/ELM327-emulator)
- DTC baza: Wal33D/dtc-database TXT → AndrOBD ResourceBundle `golfdroid_codes.properties` (9922 unique kodova: P/C/B/U generic + Volkswagen + Audi)
- MCP server modul `:mcpserver` (NanoHTTPD 2.3.1, port 8080, 6-char token):
  - `GET /health` — bez tokena, bez vozila podataka
  - `POST /tools/get_vehicle_info` — VIN, protokol (iz ElmProt, nikada hardkodiran), adapter, supported PIDs
  - `POST /tools/run_full_scan` — asinhroni sken, vraća `scan_id`
  - `POST /tools/get_scan_results` — status/dtc/live/elapsed_s, polling, 90s timeout
  - `POST /tools/get_diagnostics` — DTC lista, MIL, ready monitors
  - `POST /tools/clear_fault_codes` — 403 bez `confirm: true`
  - 401 bez/krivi token; 409 sken već u toku
- Integracija u app: `ObdBridge` (GolfObdBridge) preko CommService/ElmProt, server start na connect / stop na disconnect
- "AI dijagnostika" menu dugme (toast ako nije spojeno)
- Settings: "MCP server token" preference (tap → clipboard) za proot skill bootstrap
- Agent-skill `skill/agent-auto-dijagjosticar/SKILL.md` — izvještaj na bosanskom
- GitHub Actions: `golfdroid_apk.yml` (push + workflow_dispatch, JDK 17, APK artifact)
- ELM327 emulator e2e smoke test (`tools/test_e2e_sim.sh`)
- FAZA1-RUNBOOK.md — korisnički vodič
