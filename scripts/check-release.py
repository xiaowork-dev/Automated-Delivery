"""Refuse accidental secret/runtime files in the staged release. No secret output."""
import re
import subprocess
from pathlib import PurePosixPath

paths = subprocess.check_output(["git", "diff", "--cached", "--name-only", "--diff-filter=ACMR", "-z"]).decode("utf-8").split("\0")
patterns = (
    re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    re.compile(rb"\bgh[pousr]_[A-Za-z0-9]{30,}\b"),
    re.compile(rb"\bgithub_pat_[A-Za-z0-9_]{30,}\b"),
    re.compile(rb"\bAKIA[A-Z0-9]{16}\b"),
    re.compile(rb"\bxox[baprs]-[A-Za-z0-9-]{20,}\b"),
)
problems = []
for name in filter(None, paths):
    path = PurePosixPath(name)
    forbidden = any(part in {".runtime", "backups", "node_modules", "target", "logs"} for part in path.parts)
    forbidden |= path.name.startswith(".env") and path.name != ".env.example"
    forbidden |= path.suffix.lower() in {".pem", ".key", ".p12", ".pfx", ".gz"}
    if forbidden:
        problems.append(f"Runtime or private file staged: {name}")
        continue
    content = subprocess.check_output(["git", "show", f":{name}"])
    if any(pattern.search(content) for pattern in patterns):
        problems.append(f"Possible credential staged: {name}")
if problems:
    raise SystemExit("\n".join(problems))
print(f"Release file check passed ({len(list(filter(None, paths)))} files)")
