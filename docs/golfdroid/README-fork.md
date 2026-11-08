# GolfDroid

Fork [fr3ts0n/AndrOBD](https://github.com/fr3ts0n/AndrOBD) (GPL-3.0) za
agent-driven OBD-II dijagnostiku — primarno Golf IV 1.9 TDI (K-line / ISO 9141-2).

## Šta GolfDroid dodaje u odnosu na upstream

- **DTC baza merge** — `Wal33D/dtc-database` (28.220+ kodova) konvertovana
  u AndrOBD `EcuCodeList` resource format (`library/src/main/res/values/golfdroid_dtc.xml`).
- **MCP server modul** (`:mcpserver`) — NanoHTTPD JSON server na :8080
  sa 6-char tokenom: health, vehicle info, asinhroni puni sken (scan_id +
  polling), dijagnostika, brisanje DTC-a (samo uz `confirm: true`).
- **Agent-skill** (`skill/agent-auto-dijagjosticar`) — OpenClaude skill za
  proot okruženje: health check → sken → izvještaj na bosanskom.

## Build

```bash
./gradlew assembleDebug
```

CI: `.github/workflows/golfdroid_apk.yml` (push na main + workflow_dispatch).

## Licenca

GPL-3.0 — vidi [LICENSE](../LICENSE) i [NOTICE](../NOTICE) za atribucije
upstream projekata.
