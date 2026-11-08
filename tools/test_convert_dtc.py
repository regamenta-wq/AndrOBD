#!/usr/bin/env python3
"""Tests for tools/convert_dtc.py."""
import pathlib
import subprocess
import sys
import tempfile

SRC = "/tmp/OBD-Droid/modules/dtc-database/data/source-data"


def test_convert_produces_valid_properties():
    tmp = pathlib.Path(tempfile.mkdtemp())
    out = tmp / "golfdroid_codes.properties"
    subprocess.run(
        ["python3", "tools/convert_dtc.py", "--src", SRC, "--out", str(out)],
        check=True,
    )
    text = out.read_text(encoding="utf-8")
    assert text.startswith("# GolfDroid DTC codes")
    # P0100 kanonski generic MAF kod mora postojati
    assert "P0100=" in text
    # opis P0100 nije prazan
    m = [l for l in text.splitlines() if l.startswith("P0100=")]
    assert m, "P0100 entry missing"
    assert len(m[0].split("=", 1)[1].strip()) > 3


def test_convert_min_code_count():
    tmp = pathlib.Path(tempfile.mkdtemp())
    out = tmp / "golfdroid_codes.properties"
    subprocess.run(
        ["python3", "tools/convert_dtc.py", "--src", SRC, "--out", str(out)],
        check=True,
    )
    text = out.read_text(encoding="utf-8")
    # 9.415 generic SAE kodova (spec) — dozvoljavamo 9000+ zbog dedup-a
    n = sum(1 for l in text.splitlines() if l.strip() and "=" in l and not l.startswith("#"))
    assert n >= 9000, f"only {n} codes"


def test_convert_line_format():
    tmp = pathlib.Path(tempfile.mkdtemp())
    out = tmp / "golfdroid_codes.properties"
    subprocess.run(
        ["python3", "tools/convert_dtc.py", "--src", SRC, "--out", str(out)],
        check=True,
    )
    text = out.read_text(encoding="utf-8")
    for line in text.splitlines():
        if line.startswith("#") or not line.strip():
            continue
        code, _, desc = line.partition("=")
        assert len(code) == 5, f"bad code length: {code!r}"
        assert code[0] in "PCUB" and all(c in "0123456789ABCDEF" for c in code[1:]), f"bad code: {code!r}"
        assert len(desc) > 0, f"empty desc for {code}"


if __name__ == "__main__":
    import pytest
    sys.exit(pytest.main([__file__, "-v"]))
