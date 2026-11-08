---
name: agent-auto-dijagjosticar
description: Radi punu OBD-II dijagnostiku vozila (Golf IV 1.9 TDI) kroz GolfDroid MCP server i daje izvještaj na bosanskom. Pozovi kad korisnik kaže "uradi dijagnostiku", "provjeri auto", "šta auto pokazuje".
---

# agent-auto dijagjosticar

## Kada se koristi

Kada korisnik traži dijagnostiku auta (Golf IV 1.9 TDI / bilo koje vozilo s ELM327 adapterom) kroz GolfDroid app + MCP server.

## Konfiguracija (proot okruženje)

- Config fajl: `~/.golfdroid/config.json`
- Sadržaj: `{"url": "http://<host>:8080", "token": "<6-char token>"}`
- `url` — LAN IP telefonoa sa GolfDroid appom (npr. `192.168.0.10`) ili `127.0.0.1` ako se radi lokalno u prootu.
- `token` — dohvaća se iz GolfDroid appa → Settings → "MCP server token" → tap kopira u clipboard. Generira se prvi put kad se klikne (TokenStore), zadržava se kroz restarte.
- Token **NIKAD ne šalji u chat u cijelosti** — maskiraj kao `ab***3`.

## Preduvjeti

1. GolfDroid app instalirana na telefonu/TV boxu.
2. ELM327 adapter spojio na auto (ili simulator u developmentu).
3. U appu: klikni "Connect" → status "Connected" → MCP server sluša na :8080.
4. Token upisan u `~/.golfdroid/config.json`.

## Procedura (obavezno, u ovom redoslijedu)

1. **Čitaj config.** Ako ne postoji — pitaj korisnika za host:port + token iz app Settings i upiši ih u `~/.golfdroid/config.json`.
2. **Health check:** `GET {url}/health`. Ako ne odgovori do 10s → poruka korisniku: "Otvori GolfDroid app i klikni Connect." — NE nastavi dalje.
3. **Vehicle info:** `POST {url}/tools/get_vehicle_info` sa tokenom. Potvrdi protokol (očekuje ISO 9141-2 za Golf IV 2001; ako je drugi, javi korisniku).
4. **Puni sken:** `POST {url}/tools/run_full_scan` → `scan_id`.
5. **Polling:** `POST {url}/tools/get_scan_results` sa `scan_id`, svake 3 sekunde, maksimalno 90 sekunda. Ako status "running" i prošlo >90s → javi timeout.
6. **Tumačenje DTC kodova:** za svaki kod iz `dtc` liste koristi GolfDroid DTC bazu (konvertovana iz Wal33D/dtc-database) + vlastito znanje VAG / 1.9 TDI specifika (AHF/ALH motori, K-line / ISO 9141-2).
7. **Izvještaj na bosanskom** — obavezno sadrži:
   - svaki DTC kod s opisom i kritičnost (crveno / žuto / zeleno),
   - vjerojatan uzrok (VAG kontekst),
   - preporučene korake,
   - procjenu hitnosti (može čekati / treba uskoro / ne vozi).
8. **Brisanje kodova:** `clear_fault_codes` se NIKAD ne poziva bez eksplicitne korisničke potvrde u chatu (doslovno "Da, obriši kodove"). Bez `confirm: true` server vraća 403.

## Primjer izvještaja (struktura)

```
Dijagnostika Golf IV 1.9 TDI (ISO 9141-2)

Pronađeno: 2 DTC koda

• P0300 — Random/Multiple Cylinder Misfire Detected
  Kritičnost: crvena | Hitnost: vožnja nije preporučena
  Uzrok: zapaljenske sveće / kamene / cijevi vacua (VAG 1.9 TDI specifik)
  Korak: provjeri zapaljenske svjeće i cijevi

• P0100 — Mass Air Flow Circuit Malfunction
  Kritičnost: žuta | Hitnost: može čekati
  Uzrok: MAF senzor ili zagađenje usisnog cijeva
  Korak: ocisti/zamijeni MAF senzor

Preporuka: ...
```

## Edge case-ovi

- `get_scan_results` sa nepoznatim/starijim `scan_id` → status "unknown" — javi da sken nije pronađen.
- Ako je sken već u toku (`run_full_scan` vraća 409) — javi da sken već traje i pokreni polling.
- Ako server vraća 401 — token je kriv/istekao; korisnik generira novi u app Settings.
