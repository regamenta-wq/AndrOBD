# GolfDroid Faza 1 — Runbook

Korisnički vodič od nultog do izvještaja na bosanskom.

## Šta je potrebno

- Android telefon / TV box (SDSTB02, Android 11) sa ELM327 adapterom (Bluetooth ili WiFi)
- GolfDroid APK (build sa GitHub Actions, artifact `golfdroid-debug`)
- ELM327 v1.5+ adapter koji podržava ISO 9141-2 (Golf IV 2001 = K-line / ISO 9141-2)
- proot-Debian okruženje na telefonu (Termux) — za agent-skill

## 1. Build APK

CI radi na svakom pushu na `master` i može se pokrenuti ručno:

```bash
gh workflow run golfdroid_apk.yml
```

Preuzmi `golfdroid-debug` APK artifact.

## 2. Instaliraj app

```bash
adb connect 192.168.0.10:5555   # TV box SDSTB02
adb install -r golfdroid-debug.apk
```

## 3. Poveži adapter

- Bluetooth: pariraj ELM327 u Android BT postavkama
- U appu: odaberi médijum (BT/WiFi/USB) → "Connect"
- Status mora pokazati "Connected" → MCP server sada sluša na :8080

## 4. Dobi token za proot agenta

U appu: **Settings → "MCP server token"** → tap kopira token u clipboard. Generira se prvi put (6 znakova). Zadrži taj token.

## 5. Konfiguriraj proot skill

Na telefonu u proot-Debianu:

```bash
mkdir -p ~/.golfdroid
echo '{"url":"http://192.168.0.10:8080","token":"<6-char token>"}' > ~/.golfdroid/config.json
```

Ako radiš lokalno u prootu bez telefona: `url` = `http://127.0.0.1:8080` (simulator mode).

## 6. Pokreni dijagnostiku (agent-skill)

U OpenClaude chatu pošalji: **"uradi dijagnostiku"** (ili "provjeri auto").

Skill procedure:
1. health check (`GET /health`) — provjeri da je app spojena
2. vehicle info — protokol mora biti ISO 9141-2 za Golf IV
3. `run_full_scan` → `scan_id`
4. polling `get_scan_results` svake 3s, max 90s
5. izvještaj na bosanskom: svaki DTC kod + kritičnost + uzrok + koraci + hitnost

**Brisanje kodova** (`clear_fault_codes`) se NIKAD ne radi bez doslovne korisničke potvrde "Da, obriši kodove".

## 7. Development bez auta (ELM327 emulator)

```bash
# u prootu
pip install elm327-emulator
python3 -m elm327_emulator --tcp 35000   # simulator sluša na :35000

# u appu: Settings → adapter WiFi, host <proot-IP>, port 35000 → Connect

# e2e smoke test
export GOLFDROID_TOKEN=<token iz app Settings>
bash tools/test_e2e_sim.sh <TV-box-IP>:8080
```

Očekivani rezultat: PASS health, PASS vehicle_info, PASS scan, PASS clear_403.

## Troubleshooting

| Simptom | Rješenje |
|---|---|
| App se ne spoji na adapter | Provjeri da je ELM327 pariiran; v1.5+; ne clone adapter |
| MCP server ne odgovori :8080 | Provjeri da je app u "Connected" stanju (server se startuje na connect) |
| 401 na svim pozivima | Token je kriv — generiraj novi u app Settings |
| `get_scan_results` = "unknown" | Skena ne postoji ili je istekla (>90s) — pokreni `run_full_scan` ponovo |
| 403 na brisanje kodova | Potrebno `{"confirm":true}` u tijelu zahtjeva |

## Licenca

GPL-3.0 (fork AndrOBD). Upstream atribucije u `NOTICE`.
