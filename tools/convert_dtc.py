#!/usr/bin/env python3
"""Convert Wal33D/dtc-database TXT source into AndrOBD ResourceBundle format.

Reads /tmp/OBD-Droid/modules/dtc-database/data/source-data/{p_codes,c_codes,b_codes,u_codes,volkswagen_codes,audi_codes}.txt
(lines like "P0001 - Fuel Volume Regulator Control Circuit/Open") and writes
library/src/main/java/com/fr3ts0n/ecu/prot/obd/res/golfdroid_codes.properties
with entries: <code>=<description>.

Usage:
    python3 tools/convert_dtc.py --src <dtc-database dir> --out <properties path>
"""
import argparse
import pathlib
import re
import sys

SRC_DEFAULT = "/tmp/OBD-Droid/modules/dtc-database/data/source-data"
OUT_DEFAULT = "/tmp/golfdroid/library/src/main/java/com/fr3ts0n/ecu/prot/obd/res/golfdroid_codes.properties"

# Source files to convert (generic SAE + VAG group relevant to Golf IV 1.9 TDI).
SOURCE_FILES = [
    "p_codes.txt",
    "c_codes.txt",
    "b_codes.txt",
    "u_codes.txt",
    "volkswagen_codes.txt",
    "audi_codes.txt",
]

LINE_RE = re.compile(r"^([A-Z][0-9A-F]{4})\s*-\s*(.+)$")


def convert(src_dir: pathlib.Path, out_path: pathlib.Path) -> int:
    entries = {}
    total = 0
    for fname in SOURCE_FILES:
        fpath = src_dir / fname
        if not fpath.exists():
            print(f"skip (missing): {fpath}", file=sys.stderr)
            continue
        with fpath.open("r", encoding="utf-8", errors="replace") as fh:
            for lineno, raw in enumerate(fh, 1):
                line = raw.rstrip("\n")
                if not line.strip() or line.startswith("#"):
                    continue
                m = LINE_RE.match(line)
                if not m:
                    print(f"skip (unparsed) {fpath}:{lineno}: {line[:60]!r}", file=sys.stderr)
                    continue
                code, desc = m.group(1), m.group(2).strip()
                # dedup: first source wins (generic SAE codes take precedence over make-specific)
                if code in entries:
                    continue
                entries[code] = desc
                total += 1

    out_path.parent.mkdir(parents=True, exist_ok=True)
    with out_path.open("w", encoding="utf-8") as fh:
        fh.write("# GolfDroid DTC codes — converted from Wal33D/dtc-database (MIT)\n")
        fh.write("# Format: <dtc code>=<description>\n")
        fh.write(f"# {total} unique codes from {SOURCE_FILES}\n")
        for code in sorted(entries):
            desc = entries[code]
            # Java .properties escaping: backslash, equals, colon, newlines
            escaped = desc.replace("\\", "\\\\").replace("!", "\\!")
            fh.write(f"{code}={escaped}\n")
    return total


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--src", default=SRC_DEFAULT, help="dtc-database source-data directory")
    ap.add_argument("--out", default=OUT_DEFAULT, help="output .properties path")
    args = ap.parse_args()
    n = convert(pathlib.Path(args.src), pathlib.Path(args.out))
    print(f"wrote {n} codes to {args.out}")


if __name__ == "__main__":
    main()
