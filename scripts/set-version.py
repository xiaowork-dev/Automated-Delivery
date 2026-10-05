"""Synchronize application versions before a validated release (standard library)."""
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(__file__).resolve().parents[1]
if len(sys.argv) != 2 or not re.fullmatch(r"\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?", sys.argv[1]):
    raise SystemExit("Usage: python scripts/set-version.py 1.0.1")
version = sys.argv[1]
namespace = "http://maven.apache.org/POM/4.0.0"
ET.register_namespace("", namespace)
ET.register_namespace("xsi", "http://www.w3.org/2001/XMLSchema-instance")
pom_path = root / "backend/pom.xml"
pom = ET.parse(pom_path)
project_version = pom.getroot().find(f"{{{namespace}}}version")
if project_version is None:
    raise SystemExit("Maven project version is missing")
project_version.text = version
json_files = []
for name in ("package.json", "package-lock.json"):
    path = root / "frontend" / name
    data = json.loads(path.read_text(encoding="utf-8"))
    data["version"] = version
    if name == "package-lock.json":
        data["packages"][""]["version"] = version
    json_files.append((path, data))
# Validate every input first, then write only version metadata.
ET.indent(pom, space="  ")
pom.write(pom_path, encoding="utf-8", xml_declaration=True)
with pom_path.open("a", encoding="utf-8") as stream:
    stream.write("\n")
for path, data in json_files:
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
(root / "VERSION").write_text(version + "\n", encoding="utf-8")
print(f"Version synchronized: {version}")
