"""Verifica los archivos originales de la entrega sin modificarlos."""
from pathlib import Path
import hashlib
import json

root = Path(__file__).resolve().parent.parent
manifest = json.loads((root / "source-manifest.json").read_text(encoding="utf-8"))
failures = []
for entry in manifest["files"]:
    file = root / entry["path"]
    if not file.is_file() or hashlib.sha256(file.read_bytes()).hexdigest() != entry["sha256"]:
        failures.append(entry["path"])
if failures:
    raise SystemExit("Fuentes distintas de la entrega final: " + ", ".join(failures))
print(f"{len(manifest['files'])} archivos originales íntegros.")
