#!/usr/bin/env python3
"""
Static verification for the ResumeIQ repo.

The sandbox has no network, no Maven and no npm, so nothing here compiles code. Instead it
checks the class of mistakes that actually bites generated full-stack projects: files that
reference things that do not exist, names that disagree across the stack, and config that
does not parse.

Run it from anywhere; with no argument it verifies the repo this file lives in:
    python3 tools/verify_sources.py
    python3 tools/verify_sources.py "<other repo root>"

Exits 1 and prints every failure if anything is wrong, so it works in CI as-is.
"""
from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

import yaml

FAILURES: list[str] = []
WARNINGS: list[str] = []
CHECKS_RUN = 0
_SOURCE_CACHE: dict[Path, str] = {}
_CLEANED_CACHE: dict[tuple[Path, bool], str] = {}


def read_source(path: Path) -> str:
    """Read once. The repo lives on a mounted filesystem where re-reads dominate the runtime."""
    if path not in _SOURCE_CACHE:
        _SOURCE_CACHE[path] = path.read_text(encoding="utf-8")
    return _SOURCE_CACHE[path]


def read_cleaned(path: Path, keep_strings: bool = False) -> str:
    key = (path, keep_strings)
    if key not in _CLEANED_CACHE:
        _CLEANED_CACHE[key] = strip_literals(read_source(path), java=True, keep_strings=keep_strings)
    return _CLEANED_CACHE[key]


def check(name: str, ok: bool, detail: str = "") -> None:
    global CHECKS_RUN
    CHECKS_RUN += 1
    if not ok:
        FAILURES.append(f"{name}" + (f" — {detail}" if detail else ""))


def warn(message: str) -> None:
    WARNINGS.append(message)


# ---------------------------------------------------------------------------
# Lexical helpers: strip comments and string literals so delimiter counting and
# identifier scanning are not fooled by text inside quotes.
# ---------------------------------------------------------------------------

# Characters after which a `/` must begin a regex literal rather than a division, because none
# of them can end a value. Deliberately conservative — `)`, `]` and `}` are excluded because they
# do end values (`f() / 2`, `x[i] / 2`), and `<`/`>` are excluded because JSX text such as
# `<span>/api/health</span>` would otherwise be read as a regex. Erring towards "this is
# division" is the safe direction: the cost is the false positive this was written to fix, while
# erring the other way would swallow real code and could hide a genuine imbalance.
REGEX_PRECEDERS = set("(,=:[!&|?;{")


def regex_position(out: list[str]) -> bool:
    """True when a `/` at this point starts a regex literal."""
    tail = "".join(out[-60:]).rstrip()
    return not tail or tail[-1] in REGEX_PRECEDERS


def strip_literals(source: str, java: bool = False, keep_strings: bool = False) -> str:
    """Remove comments, and unless keep_strings is set, string bodies too."""
    out = []
    i = 0
    n = len(source)
    while i < n:
        ch = source[i]
        two = source[i:i + 2]
        if two == "//":
            while i < n and source[i] != "\n":
                i += 1
            continue
        if two == "/*":
            i += 2
            while i < n and source[i:i + 2] != "*/":
                i += 1
            i += 2
            continue
        if java and source[i:i + 3] == '"""':
            start = i
            i += 3
            while i < n and source[i:i + 3] != '"""':
                i += 1
            i += 3
            if keep_strings:
                out.append(source[start:i])
            else:
                out.append('""')
            continue
        if not java and ch == "/" and regex_position(out):
            # A JS regex literal. Its body can hold unbalanced delimiters (/[)]/), an
            # apostrophe (/doesn't/) or a slash-looking sequence, none of which the caller
            # should see. Consume to the closing unescaped '/', respecting escapes and a
            # character class (where '/' is literal), then drop the flags. This runs only for
            # JS/JSX; Java has no regex literals, so `/` there is always division or a comment
            # (both already handled above).
            i += 1
            in_class = False
            while i < n:
                c = source[i]
                if c == "\\":
                    i += 2
                    continue
                if c == "[":
                    in_class = True
                elif c == "]":
                    in_class = False
                elif c == "/" and not in_class:
                    i += 1
                    break
                elif c == "\n":
                    # A newline before the closing slash means this was not a regex after all
                    # (division, most likely). Bail without consuming it.
                    break
                i += 1
            while i < n and source[i].isalpha():  # trailing flags: g i m s u y d
                i += 1
            out.append('""')
            continue
        if ch in "\"'`":
            quote = ch
            start = i
            i += 1
            while i < n:
                if source[i] == "\\":
                    i += 2
                    continue
                if source[i] == quote:
                    i += 1
                    break
                # template literal interpolation keeps its braces meaningful
                if quote == "`" and source[i:i + 2] == "${":
                    depth = 0
                    i += 2
                    out.append("${")
                    while i < n:
                        if source[i] == "{":
                            depth += 1
                        elif source[i] == "}":
                            if depth == 0:
                                out.append("}")
                                i += 1
                                break
                            depth -= 1
                        out.append(source[i])
                        i += 1
                    continue
                i += 1
            out.append(source[start:i] if keep_strings else '""')
            continue
        out.append(ch)
        i += 1
    return "".join(out)


def balance_report(source: str, java: bool = False) -> str | None:
    cleaned = strip_literals(source, java=java)
    pairs = {"(": ")", "[": "]", "{": "}"}
    closing = {v: k for k, v in pairs.items()}
    stack = []
    line = 1
    for ch in cleaned:
        if ch == "\n":
            line += 1
        elif ch in pairs:
            stack.append((ch, line))
        elif ch in closing:
            if not stack:
                return f"unexpected '{ch}' at line {line}"
            opener, opened_at = stack.pop()
            if pairs[opener] != ch:
                return f"'{opener}' opened at line {opened_at} closed by '{ch}' at line {line}"
    if stack:
        opener, opened_at = stack[-1]
        return f"'{opener}' opened at line {opened_at} never closed"
    return None


# ---------------------------------------------------------------------------
# Config files
# ---------------------------------------------------------------------------

def verify_configs(root: Path) -> None:
    pom = root / "backend" / "pom.xml"
    check("pom.xml exists", pom.is_file())
    if pom.is_file():
        try:
            tree = ET.parse(pom)
            ns = {"m": "http://maven.apache.org/POM/4.0.0"}
            java_version = tree.find(".//m:properties/m:java.version", ns)
            check("pom.xml declares java.version", java_version is not None and java_version.text == "17",
                  f"found {java_version.text if java_version is not None else 'nothing'}")
            artifacts = {e.text for e in tree.findall(".//m:dependency/m:artifactId", ns)}
            for required in ("spring-boot-starter-web", "spring-boot-starter-validation",
                             "spring-boot-starter-data-jpa", "h2", "mysql-connector-j",
                             "spring-boot-starter-test"):
                check(f"pom.xml depends on {required}", required in artifacts)
        except ET.ParseError as exc:
            check("pom.xml parses", False, str(exc))

    for rel in ("backend/src/main/resources/application.yml",
                "backend/src/main/resources/application-dev.yml",
                "backend/src/main/resources/application-mysql.yml"):
        path = root / rel
        check(f"{rel} exists", path.is_file())
        if path.is_file():
            try:
                yaml.safe_load(path.read_text(encoding="utf-8"))
            except yaml.YAMLError as exc:
                check(f"{rel} parses", False, str(exc).splitlines()[0])

    pkg = root / "frontend" / "package.json"
    check("frontend/package.json exists", pkg.is_file())
    if pkg.is_file():
        try:
            data = json.loads(pkg.read_text(encoding="utf-8"))
            for script in ("dev", "build", "test", "lint"):
                check(f"package.json has '{script}' script", script in data.get("scripts", {}))
            return_deps = {**data.get("dependencies", {}), **data.get("devDependencies", {})}
            globals()["FRONTEND_DEPS"] = set(return_deps)
        except json.JSONDecodeError as exc:
            check("package.json parses", False, str(exc))
            globals()["FRONTEND_DEPS"] = set()


# ---------------------------------------------------------------------------
# Java
# ---------------------------------------------------------------------------

TYPE_DECL = re.compile(
    r"^\s*(?:public\s+|final\s+|abstract\s+)*@?(class|interface|enum|record)\s+(\w+)", re.MULTILINE)


def generated_member(target: str, member: str) -> bool:
    """True when the member is generated rather than written, so grepping for it would fail.

    Lombok writes accessors and builders at compile time and the compiler synthesises
    ``values()``/``valueOf()`` on every enum, so a call to one of those is correct even though the
    name appears nowhere in the source file.
    """
    if member in {"values", "valueOf"} and re.search(r"\benum\s+\w+", target):
        return True
    if member in {"builder", "toBuilder"} and "@Builder" in target:
        return True
    accessors = ("@Getter", "@Setter", "@Data", "@Value")
    if (member.startswith(("get", "is", "set"))
            and any(annotation in target for annotation in accessors)):
        return True
    return False


def verify_java(root: Path) -> None:
    java_files = sorted((root / "backend" / "src").rglob("*.java"))
    check("backend has Java sources", bool(java_files))

    declared: dict[str, Path] = {}
    for path in java_files:
        source = read_source(path)
        for _, name in TYPE_DECL.findall(source):
            declared.setdefault(name, path)

    for path in java_files:
        source = path.read_text(encoding="utf-8")
        rel = path.relative_to(root).as_posix()

        problem = balance_report(source, java=True)
        check(f"{rel} delimiters balance", problem is None, problem or "")

        match = re.search(r"^package\s+([\w.]+);", source, re.MULTILINE)
        check(f"{rel} declares a package", match is not None)
        if match:
            expected_dir = match.group(1).replace(".", "/")
            check(f"{rel} package matches its directory",
                  path.parent.as_posix().endswith(expected_dir),
                  f"package {match.group(1)}")

        primary = TYPE_DECL.search(source)
        if primary:
            check(f"{rel} primary type matches filename",
                  primary.group(2) == path.stem,
                  f"declares {primary.group(2)}")

        for imported in re.findall(r"^import\s+(?:static\s+)?(com\.resumeiq\.[\w.]+);", source, re.MULTILINE):
            simple = imported.split(".")[-1]
            # static imports and nested types resolve through their outer class
            candidates = [simple] + imported.split(".")
            check(f"{rel} import {imported} resolves",
                  any(c in declared for c in candidates),
                  "no such type in the project")

        cleaned = read_cleaned(path)
        for type_name, member in re.findall(r"\b([A-Z][A-Za-z0-9]*)\.([a-z]\w*)\s*\(", cleaned):
            if type_name not in declared or declared[type_name] == path:
                continue
            target = read_source(declared[type_name])
            if generated_member(target, member):
                continue
            check(f"{rel} calls {type_name}.{member}()",
                  re.search(rf"\b{re.escape(member)}\b", target) is not None,
                  f"{type_name} has no member named {member}")


# ---------------------------------------------------------------------------
# JavaScript / JSX
# ---------------------------------------------------------------------------

IMPORT_RE = re.compile(r"""import\s+(?P<clause>[\s\S]*?)\s+from\s+['"](?P<source>[^'"]+)['"]""")
SIDE_EFFECT_IMPORT_RE = re.compile(r"""^\s*import\s+['"](?P<source>[^'"]+)['"]""", re.MULTILINE)


def verify_frontend(root: Path) -> None:
    src = root / "frontend" / "src"
    files = sorted(list(src.rglob("*.js")) + list(src.rglob("*.jsx")))
    check("frontend has sources", bool(files))
    deps = globals().get("FRONTEND_DEPS", set())

    for path in files:
        source = path.read_text(encoding="utf-8")
        rel = path.relative_to(root).as_posix()

        problem = balance_report(source)
        check(f"{rel} delimiters balance", problem is None, problem or "")

        specs = [(m.group("clause"), m.group("source")) for m in IMPORT_RE.finditer(source)]
        specs += [("", m.group("source")) for m in SIDE_EFFECT_IMPORT_RE.finditer(source)]

        for clause, target in specs:
            if target.startswith("."):
                resolved = (path.parent / target).resolve()
                exists = resolved.is_file()
                if not exists:
                    for ext in (".js", ".jsx", "/index.js", "/index.jsx"):
                        if Path(str(resolved) + ext).is_file():
                            exists = True
                            break
                check(f"{rel} imports {target}", exists, "file not found")
                if exists and resolved.is_file():
                    verify_named_exports(rel, clause, target, resolved)
            elif not target.startswith(("/", "http")):
                package = target if not target.startswith("@") else "/".join(target.split("/")[:2])
                package = package.split("/")[0] if not target.startswith("@") else package
                known = package in deps or package in {"react", "react-dom"} or package.startswith("node:")
                if not known and package not in {"vitest"}:
                    check(f"{rel} imports package {package}", package in deps,
                          "not in package.json dependencies")


def verify_named_exports(rel: str, clause: str, target: str, resolved: Path) -> None:
    clause = clause.strip()
    if not clause:
        return
    target_source = resolved.read_text(encoding="utf-8")

    default_part = clause.split("{")[0].strip().rstrip(",").strip()
    if default_part and not default_part.startswith("*"):
        check(f"{rel} default-imports from {target}",
              "export default" in target_source, "module has no default export")

    named = re.search(r"\{([\s\S]*?)\}", clause)
    if named:
        for raw in named.group(1).split(","):
            name = raw.split(" as ")[0].strip()
            if not name:
                continue
            patterns = [
                rf"export\s+(?:const|let|var|function|class|async\s+function)\s+{re.escape(name)}\b",
                rf"export\s*\{{[^}}]*\b{re.escape(name)}\b",
            ]
            check(f"{rel} imports {{{name}}} from {target}",
                  any(re.search(p, target_source) for p in patterns),
                  "not exported there")


# ---------------------------------------------------------------------------
# Design tokens: a class like text-band-strong must exist in tailwind.config.js
# ---------------------------------------------------------------------------

TOKEN_CLASS_RE = re.compile(
    r"\b(?:text|bg|border|stroke|fill|ring|ring-offset|from|via|to|divide|placeholder|shadow|font|rounded|animate)-"
    r"([a-z]+(?:-[a-z0-9]+)*)")

KNOWN_TAILWIND_KEYS = {
    "white", "black", "transparent", "current", "inherit", "none", "full", "md", "lg", "sm", "xs",
    "xl", "2xl", "3xl", "spin", "pulse", "gradient-to-r", "gradient-to-l", "gradient-to-b",
    "gradient-to-t", "mono", "sans", "display", "medium", "semibold", "bold", "normal", "light",
}


def verify_tokens(root: Path) -> None:
    config_path = root / "frontend" / "tailwind.config.js"
    css_path = root / "frontend" / "src" / "index.css"
    check("tailwind.config.js exists", config_path.is_file())
    check("index.css exists", css_path.is_file())
    if not (config_path.is_file() and css_path.is_file()):
        return

    config = config_path.read_text(encoding="utf-8")
    css = css_path.read_text(encoding="utf-8")

    # Comments mention token names too; only real declarations count.
    config_code = strip_literals(config, keep_strings=True)
    css_code = re.sub(r"/\*[\s\S]*?\*/", "", css)

    def theme_block(selector: str) -> str:
        match = re.search(rf"{re.escape(selector)}\s*\{{([\s\S]*?)\n\}}", css_code)
        return match.group(1) if match else ""

    light = theme_block(":root")
    dark = theme_block(".dark")
    check("index.css defines a :root theme block", bool(light))
    check("index.css defines a .dark theme block", bool(dark))

    # Every token referenced by the config must be defined in both themes.
    config_vars = set(re.findall(r"var\((--[a-z0-9-]+)\)", config_code))
    check("tailwind.config.js references design tokens", bool(config_vars))
    for var in sorted(config_vars):
        check(f"token {var} defined for light theme", f"{var}:" in light)
        check(f"token {var} defined for dark theme", f"{var}:" in dark)

    # And a token defined in one theme but not the other is a dark-mode hole.
    for var in sorted(set(re.findall(r"(--[a-z0-9-]+):", light))):
        check(f"token {var} has a dark-theme value", f"{var}:" in dark)

    # Every colour-ish class used in the app must map to a key the config defines.
    families = {"band", "brand", "accent", "success", "warning", "danger", "ink", "surface", "line", "bg"}
    sources = list((root / "frontend" / "src").rglob("*.jsx")) + list((root / "frontend" / "src").rglob("*.js"))
    sources.append(css_path)
    for path in sources:
        text = path.read_text(encoding="utf-8")
        rel = path.relative_to(root).as_posix()
        for token in set(TOKEN_CLASS_RE.findall(text)):
            head = token.split("-")[0]
            if head not in families:
                continue
            key = token.split("/")[0]
            parts = key.split("-")
            needle = parts[1] if len(parts) > 1 else None
            if needle is None:
                check(f"{rel} uses colour family '{head}' defined in config",
                      f"{head}:" in config_code)
            else:
                check(f"{rel} uses {key} which the config defines",
                      re.search(rf"{head}:\s*\{{[^}}]*(?:'{needle}'|\"{needle}\"|\b{needle}\b)\s*:",
                                config_code, re.S) is not None or needle in KNOWN_TAILWIND_KEYS,
                      f"no '{needle}' key under '{head}'")


# ---------------------------------------------------------------------------
# Cross-stack: the frontend may only read fields the backend actually returns
# ---------------------------------------------------------------------------

def verify_contract(root: Path) -> None:
    record = root / "backend" / "src" / "main" / "java" / "com" / "resumeiq" / "health" / "HealthResponse.java"
    page = root / "frontend" / "src" / "pages" / "SystemCheck.jsx"
    check("HealthResponse.java exists", record.is_file())
    check("SystemCheck.jsx exists", page.is_file())
    if not (record.is_file() and page.is_file()):
        return

    body = record.read_text(encoding="utf-8")
    header = re.search(r"public\s+record\s+HealthResponse\s*\(([\s\S]*?)\)\s*\{", body)
    check("HealthResponse declares a record header", header is not None)
    if not header:
        return
    fields = {re.split(r"\s+", part.strip())[-1] for part in header.group(1).split(",") if part.strip()}

    used = set(re.findall(r"\bhealth\??\.(\w+)", page.read_text(encoding="utf-8")))
    for field in sorted(used):
        check(f"SystemCheck reads health.{field} which the API returns", field in fields,
              f"HealthResponse has {sorted(fields)}")

    # And the slice test must assert against the same field names.
    test = root / "backend" / "src" / "test" / "java" / "com" / "resumeiq" / "health" / "HealthControllerTest.java"
    if test.is_file():
        asserted = set(re.findall(r"jsonPath\(\"\$\.(\w+)", test.read_text(encoding="utf-8")))
        for field in sorted(asserted):
            check(f"HealthControllerTest asserts on health.{field}", field in fields)


# ---------------------------------------------------------------------------
# Real parsing for plain JS via node --check. JSX has no parser available
# offline, so those files rely on the delimiter check above.
# ---------------------------------------------------------------------------

def verify_js_syntax(root: Path) -> None:
    frontend = root / "frontend"
    if not frontend.is_dir():
        return
    try:
        subprocess.run(["node", "--version"], capture_output=True, check=True)
    except (OSError, subprocess.CalledProcessError):
        warn("node is unavailable, so JS files were not parsed")
        return

    is_esm_project = '"type": "module"' in (frontend / "package.json").read_text(encoding="utf-8")
    scratch = Path(tempfile.mkdtemp())

    candidates = sorted(frontend.glob("*.js")) + sorted(frontend.glob("*.cjs")) \
        + sorted((frontend / "src").rglob("*.js"))
    for path in candidates:
        rel = path.relative_to(root).as_posix()
        source = path.read_text(encoding="utf-8")

        # Extension and module syntax have to agree or the tool that loads the
        # file will fail at runtime rather than at build time.
        if path.suffix == ".js" and is_esm_project:
            check(f"{rel} uses ESM syntax as package.json declares",
                  "module.exports" not in source, "uses module.exports in an ESM project")
        if path.suffix == ".cjs":
            check(f"{rel} uses CommonJS syntax as its .cjs extension implies",
                  not re.search(r"^\s*export\s", source, re.MULTILINE), "uses export in a .cjs file")

        suffix = ".cjs" if path.suffix == ".cjs" else (".mjs" if is_esm_project else ".cjs")
        copy = scratch / (path.stem + suffix)
        copy.write_text(source, encoding="utf-8")
        result = subprocess.run(["node", "--check", str(copy)], capture_output=True, text=True)
        message = (result.stderr or "").strip().splitlines()
        check(f"{rel} parses", result.returncode == 0,
              next((line for line in message if "Error" in line), "syntax error"))

    shutil.rmtree(scratch, ignore_errors=True)


# ---------------------------------------------------------------------------
# Security invariants from the brief. These are the rules that must never
# regress, so they are checked on every phase rather than reviewed by eye.
# ---------------------------------------------------------------------------

SECRET_PATTERNS = [
    (r"sk-[A-Za-z0-9_\-]{16,}", "an API key literal"),
    (r"AI_API_KEY\s*[=:]\s*['\"]?[A-Za-z0-9_\-]{12,}", "a populated AI key"),
    (r"JWT_SECRET\s*[=:]\s*['\"]?[A-Za-z0-9_\-]{12,}", "a populated JWT secret"),
    (r"(?i)password\s*[=:]\s*['\"][^'\"$\s]{4,}['\"]", "a hardcoded password"),
]

# A value that tells the reader to replace it is documentation, not a secret.
PLACEHOLDER_RE = re.compile(r"(?i)replace|change-?me|your[-_]|example|xxx|<[^>]+>|\.\.\.")

TRACKED_EXTENSIONS = {".java", ".js", ".jsx", ".yml", ".yaml", ".json", ".xml", ".css", ".html",
                      ".md", ".example", ".cjs"}

# Directories that are generated or installed rather than written. They must be pruned during the
# walk, not filtered after it: node_modules alone is 18k files on a mounted filesystem, and
# descending into it took longer than every other check in this file combined.
SKIP_DIRS = {"node_modules", "target", "dist", "build", "coverage", "storage",
             ".git", ".idea", ".vscode", ".gradle", "__pycache__"}


def walk_files(base: Path) -> list[Path]:
    found: list[Path] = []
    for dirpath, dirnames, filenames in os.walk(base):
        dirnames[:] = [name for name in dirnames if name not in SKIP_DIRS]
        found.extend(Path(dirpath) / name for name in filenames)
    return sorted(found)


def tracked_files(root: Path) -> list[Path]:
    return [path for path in walk_files(root)
            if path.suffix in TRACKED_EXTENSIONS or path.name == ".env.example"]


def verify_security(root: Path) -> None:
    files = tracked_files(root)

    for path in files:
        rel = path.relative_to(root).as_posix()
        text = read_source(path)
        for pattern, description in SECRET_PATTERNS:
            hits = [m for m in re.finditer(pattern, text)
                    if not PLACEHOLDER_RE.search(m.group(0))]
            check(f"{rel} contains no committed secret", not hits,
                  f"looks like {description}: {hits[0].group(0)[:40] if hits else ''}")

    gitignore = root / ".gitignore"
    check(".gitignore exists", gitignore.is_file())
    if gitignore.is_file():
        rules = gitignore.read_text(encoding="utf-8")
        for needed in (".env", "target/", "node_modules", "storage"):
            check(f".gitignore excludes {needed}", needed in rules)
        check(".gitignore still allows .env.example", "!.env.example" in rules)

    example = root / ".env.example"
    check(".env.example exists", example.is_file())
    if example.is_file():
        lines = [l for l in example.read_text(encoding="utf-8").splitlines()
                 if l.strip() and not l.strip().startswith("#")]
        for key in ("AI_API_KEY", "JWT_SECRET"):
            entry = next((l for l in lines if l.startswith(key)), None)
            check(f".env.example lists {key}", entry is not None)
            if entry:
                value = entry.split("=", 1)[1].strip() if "=" in entry else ""
                check(f".env.example ships no usable {key}",
                      value in {"", '""'} or PLACEHOLDER_RE.search(value) is not None,
                      f"has a real-looking value: {value[:24]}")

    # The AI key is a backend-only concern; no frontend file may reference it.
    for path in walk_files(root / "frontend"):
        if path.suffix in {".js", ".jsx", ".html", ".json"}:
            text = read_source(path)
            rel = path.relative_to(root).as_posix()
            check(f"{rel} never references an AI key",
                  "AI_API_KEY" not in text and "ANTHROPIC_API_KEY" not in text,
                  "AI credentials must stay on the server")

    # Stack traces must never be serialised to clients.
    app_yml = root / "backend" / "src" / "main" / "resources" / "application.yml"
    if app_yml.is_file():
        config = yaml.safe_load(app_yml.read_text(encoding="utf-8")) or {}
        error = ((config.get("server") or {}).get("error") or {})
        check("stack traces are never included in responses",
              error.get("include-stacktrace") == "never", f"found {error.get('include-stacktrace')}")

    # Runtime code must not hardcode localhost; env defaults and dev tooling may.
    allowed = {"frontend/vite.config.js"}
    for path in files:
        rel = path.relative_to(root).as_posix()
        if rel in allowed or path.suffix in {".md", ".html", ".example", ".yml", ".yaml"} \
                or "src/test" in rel or "__tests__" in rel:
            continue
        text = read_source(path)
        # Prose about localhost in a comment is not a hardcoded URL.
        if path.suffix in {".java", ".js", ".jsx", ".css"}:
            text = strip_literals(text, java=path.suffix == ".java", keep_strings=True)
        for match in re.finditer(r"localhost", text):
            line = text[:match.start()].count("\n") + 1
            context = text.splitlines()[line - 1]
            check(f"{rel} does not hardcode localhost", False,
                  f"line {line}: {context.strip()[:60]}")



# ---------------------------------------------------------------------------
# JSX has no parser available offline, so check the failure mode that actually
# happens: a tag that is never closed, or closed by the wrong name.
# ---------------------------------------------------------------------------

# Matched case-sensitively, which is the whole point: JSX resolves a lowercase name
# to an HTML element and a capitalised one to a component. React Router's <Link> is
# not HTML's void <link>, and recharts' <Line> is not SVG's <line> — folding case
# here would silently stop tracking three components that do have closing tags.
VOID_ELEMENTS = {"br", "hr", "img", "input", "meta", "link", "source", "path", "circle",
                 "rect", "line", "polyline", "polygon", "area", "col", "embed", "track", "use"}


def verify_jsx_nesting(root: Path) -> None:
    for path in sorted((root / "frontend" / "src").rglob("*.jsx")):
        rel = path.relative_to(root).as_posix()
        code = strip_literals(path.read_text(encoding="utf-8"))
        stack: list[tuple[str, int]] = []
        problem = None

        i = 0
        n = len(code)
        while i < n and problem is None:
            if code[i] != "<":
                i += 1
                continue
            # `a < b` is a comparison, not a tag.
            prev = next((c for c in reversed(code[:i]) if not c.isspace()), "")
            closing = re.match(r"</\s*([A-Za-z][\w.:-]*)\s*>", code[i:])
            opening = re.match(r"<\s*([A-Za-z][\w.:-]*)", code[i:])
            fragment_open = re.match(r"<>", code[i:])
            fragment_close = re.match(r"</>", code[i:])
            line = code[:i].count("\n") + 1

            if fragment_close:
                if not stack or stack[-1][0] != "<>":
                    problem = f"line {line}: </> closes {stack[-1][0] if stack else 'nothing'}"
                else:
                    stack.pop()
                i += 3
                continue
            if fragment_open:
                stack.append(("<>", line))
                i += 2
                continue
            if closing:
                name = closing.group(1)
                if not stack:
                    problem = f"line {line}: </{name}> closes nothing"
                elif stack[-1][0] != name:
                    problem = (f"line {line}: </{name}> closes <{stack[-1][0]}> "
                               f"opened at line {stack[-1][1]}")
                else:
                    stack.pop()
                i += closing.end()
                continue
            if opening and prev not in {"=", "<", ">", "+", "-", "*", "/", "!", "&", "|", "?"} \
                    or (opening and prev in {"(", "{", ",", "=", ">", "", ";", ":"}):
                name = opening.group(1)
                # Walk to the end of the tag, skipping nested braces in attributes.
                j = i + opening.end()
                depth = 0
                self_closing = False
                while j < n:
                    if code[j] == "{":
                        depth += 1
                    elif code[j] == "}":
                        depth -= 1
                    elif depth == 0 and code[j:j + 2] == "/>":
                        self_closing = True
                        j += 2
                        break
                    elif depth == 0 and code[j] == ">":
                        j += 1
                        break
                    j += 1
                if not self_closing and name not in VOID_ELEMENTS:
                    stack.append((name, line))
                i = j
                continue
            i += 1

        if problem is None and stack:
            name, line = stack[-1]
            problem = f"<{name}> opened at line {line} is never closed"
        check(f"{rel} JSX tags nest correctly", problem is None, problem or "")


# ---------------------------------------------------------------------------
# A missing import is the commonest Java compile error and nothing else here
# would see it, so resolve every simple type name a file uses.
# ---------------------------------------------------------------------------

JAVA_LANG = {
    "String", "Object", "Integer", "Long", "Double", "Float", "Boolean", "Byte", "Short",
    "Character", "Number", "Math", "System", "Exception", "RuntimeException", "Throwable",
    "Error", "IllegalArgumentException", "IllegalStateException", "NullPointerException",
    "UnsupportedOperationException", "Class", "Enum", "Record", "Iterable", "Comparable",
    "Override", "Deprecated", "SuppressWarnings", "FunctionalInterface", "SafeVarargs",
    "StringBuilder", "Thread", "Void", "CharSequence", "Cloneable", "AutoCloseable",
}


# ---------------------------------------------------------------------------
# Record constructor arity.
#
# Adding a component to a record is a one-line edit that silently breaks every
# hand-written `new Record(...)` in the codebase — usually in tests, which is where
# config records get built by hand. This is the one compiler error this harness has
# actually let through (a fourth `Auth` component on ResumeIqProperties, Phase 3),
# so it earns a check of its own.
# ---------------------------------------------------------------------------

RECORD_DECL = re.compile(r"\brecord\s+([A-Z]\w*)\s*\(")


def blank_noise(source: str) -> str:
    """Blank comments and string bodies while preserving every byte position.

    ``strip_literals`` deletes block comments outright, which shifts every line number
    after them. Here the text keeps its shape — comment bytes become spaces, newlines
    stay newlines — so a reported line number matches what the editor shows.
    """
    out: list[str] = []
    i, n = 0, len(source)
    while i < n:
        two = source[i:i + 2]
        if two == "//":
            while i < n and source[i] != "\n":
                out.append(" ")
                i += 1
            continue
        if two == "/*":
            while i < n and source[i:i + 2] != "*/":
                out.append("\n" if source[i] == "\n" else " ")
                i += 1
            out.append("  ")
            i += 2
            continue
        ch = source[i]
        if ch in "\"'":
            out.append(ch)
            i += 1
            while i < n:
                if source[i] == "\\":
                    out.append("  ")
                    i += 2
                    continue
                if source[i] == ch:
                    out.append(ch)
                    i += 1
                    break
                out.append("\n" if source[i] == "\n" else " ")
                i += 1
            continue
        out.append(ch)
        i += 1
    return "".join(out)


def enclosed_text(source: str, open_index: int) -> str | None:
    """The text between the parenthesis at ``open_index`` and its match."""
    depth = 0
    for i in range(open_index, len(source)):
        if source[i] == "(":
            depth += 1
        elif source[i] == ")":
            depth -= 1
            if depth == 0:
                return source[open_index + 1:i]
    return None


def split_top_level(text: str) -> list[str]:
    """Split a parameter or argument list on its top-level commas.

    Angle brackets are tracked so the comma in ``Map<String, Integer>`` does not read as
    an argument separator, but only when the ``<`` follows something type-like — otherwise
    a ``a < b`` comparison would open a depth that never closes.
    """
    parts: list[str] = []
    current: list[str] = []
    round_d = square_d = curly_d = angle_d = 0

    for index, ch in enumerate(text):
        if ch == "(":
            round_d += 1
        elif ch == ")":
            round_d -= 1
        elif ch == "[":
            square_d += 1
        elif ch == "]":
            square_d -= 1
        elif ch == "{":
            curly_d += 1
        elif ch == "}":
            curly_d -= 1
        elif ch == "<" and index and (text[index - 1].isalnum() or text[index - 1] in "_>."):
            angle_d += 1
        elif ch == ">" and angle_d and text[index - 1] != "-":
            angle_d -= 1

        if ch == "," and not (round_d or square_d or curly_d or angle_d):
            parts.append("".join(current).strip())
            current = []
        else:
            current.append(ch)

    parts.append("".join(current).strip())
    return [part for part in parts if part]


STEREOTYPES = ("@Service", "@Component", "@Repository", "@RestController", "@Controller",
               "@Configuration", "@ControllerAdvice", "@RestControllerAdvice")

# A constructor declaration: optional annotations, optional modifiers, the class's own name, and
# an opening paren — anchored to the previous statement boundary so that `new Foo(`, `return
# new Foo(` and `this(` cannot masquerade as declarations (none of `new`/`return` is a modifier).
CTOR_MODIFIERS = "public|protected|private|final|static|abstract|synchronized"


def verify_bean_construction(root: Path) -> None:
    """
    Every Spring-managed class must have exactly one way to be built.

    Spring infers a constructor only when a class declares precisely one. Declare two and leave
    both unannotated and it does not choose — it looks for a no-arg constructor, does not find
    one, and the whole application context fails to start. This cost a full `mvn test` round
    trip: `LoginAttemptService` grew a package-private clock seam for its tests, which made two
    constructors, and 32 tests reported `Failed to load ApplicationContext`. Nothing caught it
    earlier because `@DataJpaTest` and `@WebMvcTest` never instantiate `@Service` beans, so every
    slice test passed while the real application could not boot.
    """
    for path in sorted((root / "backend" / "src" / "main").rglob("*.java")):
        cleaned = blank_noise(read_source(path))
        name = path.stem
        # blank_noise has already erased comments, so a stereotype named in prose (or an
        # @Autowired mentioned in javadoc, as LoginAttemptService does) cannot be miscounted.
        if not any(re.search(rf"{stereotype}\b", cleaned) for stereotype in STEREOTYPES):
            continue
        if not re.search(rf"\b(?:class|record|interface)\s+{name}\b", cleaned):
            continue

        rel = path.relative_to(root).as_posix()
        pattern = re.compile(
            rf"[{{}};]\s*((?:@\w+(?:\s*\([^()]*\))?\s*)*)"
            rf"(?:(?:{CTOR_MODIFIERS})\s+)*{re.escape(name)}\s*\(")
        constructors = [match.group(1) for match in pattern.finditer(cleaned)]
        autowired = [modifiers for modifiers in constructors if "@Autowired" in modifiers]

        label = f"{rel} {name} has one unambiguous injection point"
        if len(constructors) <= 1:
            check(label, True)
        elif len(autowired) == 1:
            check(label, True)
        elif not autowired:
            check(label, False,
                  f"{len(constructors)} constructors and no @Autowired — Spring will look for a "
                  f"no-arg constructor and the context will not start")
        else:
            check(label, False,
                  f"{len(autowired)} constructors marked @Autowired — at most one may be")


def verify_record_arity(root: Path) -> None:
    files = sorted((root / "backend" / "src").rglob("*.java"))
    cleaned_by_path = {path: blank_noise(read_source(path)) for path in files}

    # Canonical arity, plus any explicit overload, because an overloaded constructor is a
    # legitimate second arity. Widening the allowed set can only cause a missed error, never
    # a false one, which is the right direction for a heuristic to fail in.
    allowed: dict[str, set[int]] = {}
    for cleaned in cleaned_by_path.values():
        for match in RECORD_DECL.finditer(cleaned):
            components = enclosed_text(cleaned, match.end() - 1)
            if components is not None:
                allowed.setdefault(match.group(1), set()).add(len(split_top_level(components)))

    for cleaned in cleaned_by_path.values():
        for name, arities in allowed.items():
            for match in re.finditer(rf"\b(?:public|protected|private)\s+{name}\s*\(", cleaned):
                params = enclosed_text(cleaned, match.end() - 1)
                if params is not None:
                    arities.add(len(split_top_level(params)))

    for path, cleaned in cleaned_by_path.items():
        rel = path.relative_to(root).as_posix()
        for name, arities in sorted(allowed.items()):
            pattern = rf"\bnew\s+(?:[A-Z]\w*\s*\.\s*)*{name}\s*\("
            for match in re.finditer(pattern, cleaned):
                args = enclosed_text(cleaned, match.end() - 1)
                if args is None:
                    continue
                count = len(split_top_level(args))
                line = cleaned[:match.start()].count("\n") + 1
                expected = "/".join(str(a) for a in sorted(arities))
                # Too few is a certain error. Too many is only ever reported as a warning,
                # because an uncounted generic comma inflates the total and this check must
                # not cry wolf on code the compiler is happy with.
                if count < min(arities):
                    check(f"{rel}:{line} new {name}(...) passes every component", False,
                          f"{count} argument(s), but {name} takes {expected}")
                elif count not in arities:
                    warn(f"{rel}:{line} new {name}(...) passes {count} arguments "
                         f"but {name} takes {expected} — check for a missing component")
                else:
                    check(f"{rel}:{line} new {name}(...) passes every component", True)


def verify_java_imports(root: Path) -> None:
    files = sorted((root / "backend" / "src").rglob("*.java"))
    by_package: dict[str, set[str]] = {}
    for path in files:
        source = read_source(path)
        package = re.search(r"^package\s+([\w.]+);", source, re.MULTILINE)
        if package:
            by_package.setdefault(package.group(1), set()).update(
                name for _, name in TYPE_DECL.findall(source))

    for path in files:
        source = read_source(path)
        rel = path.relative_to(root).as_posix()
        code = read_cleaned(path)
        package_match = re.search(r"^package\s+([\w.]+);", source, re.MULTILINE)
        package = package_match.group(1) if package_match else ""

        imported, wildcard = set(), False
        for imp in re.findall(r"^import\s+(?:static\s+)?([\w.*]+);", source, re.MULTILINE):
            if imp.endswith(".*"):
                wildcard = True
            else:
                imported.add(imp.split(".")[-1])
        if wildcard:
            warn(f"{rel} uses a wildcard import, so its type names cannot be resolved here")
            continue

        local = {name for _, name in re.findall(r"(class|interface|enum|record)\s+(\w+)", code)}
        visible = imported | local | by_package.get(package, set()) | JAVA_LANG

        for name in sorted(set(re.findall(r"(?<![\w.])([A-Z][A-Za-z0-9]{1,})\b", code))):
            if name.isupper():  # CONSTANT_CASE fragment, not a type
                continue
            check(f"{rel} resolves the type {name}", name in visible,
                  "no import, no local declaration, not in the same package")


# ---------------------------------------------------------------------------
# Persistence invariants. Hibernate generates the schema from these annotations,
# so a wrong one is not a compile error — it is a table that is quietly wrong,
# and the wrongness only shows up as data that has already been written.
# ---------------------------------------------------------------------------

SENSITIVE_ACCESSORS = {"getExtractedText", "getRawText", "getRawResponse", "getPasswordHash"}


def field_units(cleaned_source: str) -> list[tuple[str, str]]:
    """Pair each field declaration with the annotation block immediately above it.

    Line based on purpose: the annotations in this codebase are one per line, and a line scan
    cannot be fooled by the nested parentheses in @CollectionTable the way a regex would be.
    """
    units: list[tuple[str, str]] = []
    buffer: list[str] = []
    depth = 0
    for raw_line in cleaned_source.splitlines():
        line = raw_line.strip()
        if depth > 0:
            buffer.append(line)
            depth += line.count("(") - line.count(")")
            continue
        if line.startswith("@"):
            buffer.append(line)
            depth += line.count("(") - line.count(")")
            continue
        if line.startswith("private ") and line.endswith(";"):
            units.append((" ".join(buffer), line))
            buffer = []
            continue
        if line:
            buffer = []
    return units


def verify_persistence(root: Path) -> None:
    main_java = root / "backend" / "src" / "main" / "java"
    if not main_java.is_dir():
        return

    tables: dict[str, str] = {}
    identifiers: dict[str, str] = {}

    for path in sorted(main_java.rglob("*.java")):
        source = read_source(path)
        rel = path.relative_to(root).as_posix()
        code = read_cleaned(path, keep_strings=True)
        is_entity = re.search(r"^@Entity\b", code, re.MULTILINE) is not None

        # Every declared identifier must be unique and fit MySQL's 64-character limit.
        for kind in ("Table", "CollectionTable", "Index", "UniqueConstraint", "ForeignKey"):
            for match in re.finditer(rf"@{kind}\(\s*name = \"([^\"]+)\"", code):
                name = match.group(1)
                check(f"{rel} identifier '{name}' is within MySQL's 64-character limit",
                      len(name) <= 64, f"{len(name)} characters")
                if kind in {"Index", "UniqueConstraint", "ForeignKey"}:
                    owner = identifiers.setdefault(name, rel)
                    check(f"identifier '{name}' is declared once", owner == rel,
                          f"also declared in {owner}")

        if not is_entity:
            continue

        table = re.search(r"@Table\(\s*name = \"(\w+)\"", code)
        check(f"{rel} names its table explicitly", table is not None,
              "@Entity without @Table(name = ...) leaves the table name to Hibernate")
        if table:
            owner = tables.setdefault(table.group(1), rel)
            check(f"table '{table.group(1)}' is mapped once", owner == rel,
                  f"also mapped by {owner}")

        check(f"{rel} inherits its id and timestamps",
              re.search(r"extends\s+(BaseEntity|PublicIdEntity)\b", code) is not None,
              "an entity that does not extend BaseEntity has no created_at/updated_at")

        for forbidden, why in (("@Data", "generates equals/hashCode over every field"),
                               ("@ToString", "logs the whole row, including sensitive columns"),
                               ("@AllArgsConstructor(access = AccessLevel.PUBLIC)",
                                "a public all-args constructor bypasses the invariants")):
            check(f"{rel} avoids {forbidden}", forbidden not in code, why)

        for annotations, field in field_units(code):
            label = field.rstrip(";").split()[-1].split("=")[0].strip()

            if "@Enumerated" in annotations:
                check(f"{rel}.{label} stores its enum as text",
                      "EnumType.STRING" in annotations,
                      "ORDINAL renumbers every existing row when a constant is inserted")

            to_one = "@ManyToOne" in annotations or "@OneToOne" in annotations
            if to_one:
                check(f"{rel}.{label} is fetched lazily",
                      "FetchType.LAZY" in annotations,
                      "JPA defaults to eager, which loads the parent row on every read")
                if "@JoinColumn" in annotations:
                    check(f"{rel}.{label} names its foreign key",
                          "@ForeignKey(name =" in annotations,
                          "an unnamed constraint is unsearchable in a production error log")

            if "@Column(" in annotations:
                column = re.search(r"@Column\(\s*name = \"(\w+)\"", annotations)
                check(f"{rel}.{label} names its column explicitly", column is not None,
                      "relying on the naming strategy makes the schema invisible from the code")
                if column:
                    check(f"{rel}.{label} uses a snake_case column name",
                          re.fullmatch(r"[a-z][a-z0-9_]*", column.group(1)) is not None,
                          column.group(1))
                if re.search(r"\bprivate\s+String\s", field) and "@Lob" not in annotations:
                    check(f"{rel}.{label} bounds its text column",
                          "length =" in annotations,
                          "an unbounded varchar defaults to 255 silently, or should be a @Lob")

    check("the schema has the tables the design describes", bool(tables))
    documented = (root / "docs" / "database.md")
    if documented.is_file():
        text = documented.read_text(encoding="utf-8")
        collection_tables = set()
        for path in sorted(main_java.rglob("*.java")):
            code = read_cleaned(path, keep_strings=True)
            collection_tables.update(re.findall(r"@CollectionTable\(\s*name = \"(\w+)\"", code))
        for table in sorted(set(tables) | collection_tables):
            check(f"docs/database.md documents the '{table}' table", f"`{table}`" in text,
                  "a schema doc that omits a table is worse than no schema doc")


def verify_repositories(root: Path) -> None:
    """Ownership is enforced by method signature, so the signatures are worth checking."""
    main_java = root / "backend" / "src" / "main" / "java"
    test_java = root / "backend" / "src" / "test" / "java"
    if not main_java.is_dir():
        return

    test_sources = ""
    if test_java.is_dir():
        test_sources = "\n".join(read_source(p) for p in test_java.rglob("*.java"))

    for path in sorted(main_java.rglob("*Repository.java")):
        source = path.read_text(encoding="utf-8")
        rel = path.relative_to(root).as_posix()
        code = read_cleaned(path)

        # The user table is the one exception: the owner of a user row is that row, so
        # findByPublicId has nothing further to scope itself to.
        owned_by_someone_else = path.stem != "UserRepository"

        for method in re.findall(r"\b(find\w*|delete\w*|count\w*|exists\w*)\s*\(", code):
            if "PublicId" not in method or not owned_by_someone_else:
                continue
            # A public id is what a URL carries, so any lookup by one has to be scoped to the
            # caller. Enforcing it in the signature means it cannot be forgotten at a call site.
            check(f"{rel}.{method} is scoped to a user",
                  "User" in method,
                  "a lookup by public id must take the owner's id too")

        check(f"{rel} is exercised by a test",
              path.stem in test_sources,
              "a repository whose derived queries are never executed is never verified")

    for path in sorted(main_java.rglob("*.java")):
        source = read_source(path)
        rel = path.relative_to(root).as_posix()
        if not re.search(r"^public interface \w+ \{", source, re.MULTILINE):
            continue  # projections are plain interfaces; repositories extend JpaRepository
        for accessor in sorted(SENSITIVE_ACCESSORS):
            check(f"{rel} does not project {accessor}()",
                  accessor not in source,
                  "list projections must not be able to read resume or posting text")

    # Assertions about identifiers must name them, not pattern-match them. This one is here
    # because it cost a red build: a projection test asserted that no accessor name contained
    # "text", which is true of getExtractedText and also true of getExtractionError —
    # "getextractionerror" contains "text" inside "ge-text-raction". A substring rule over
    # identifiers finds words nobody wrote.
    for path in sorted(test_java.rglob("*Test.java")) if test_java.is_dir() else []:
        rel = path.relative_to(root).as_posix()
        code = read_cleaned(path, keep_strings=True)
        for match in re.finditer(r"toLowerCase|toUpperCase", code):
            window = code[max(0, match.start() - 200): match.end() + 200]
            check(f"{rel} does not assert on a case-folded identifier",
                  "doesNotContain" not in window and "contains(" not in window,
                  "compare accessor or field names exactly; a substring match on a folded "
                  "identifier fires on words that are not there")


# ---------------------------------------------------------------------------
# Derived queries. Spring Data writes the JPQL for these from the method name
# alone, so a property that was renamed — or simply mistyped — is not a compile
# error. The application fails to start with "No property 'ownerId' found for
# type Resume", and nothing in this repository can see that: there is no
# compiler here, and a repository slice that never starts never runs. So the
# property paths are resolved against the entity fields directly, the same way
# the parser will resolve them.
# ---------------------------------------------------------------------------

FIELD_DECL = re.compile(r"^(?:private|protected)\s+(?:final\s+)?([\w.]+(?:<[^;=]*>)?)\s+(\w+)\s*[;=]")

CONTAINER_TYPES = {"Set", "List", "Collection", "Optional", "SortedSet", "LinkedHashSet"}

DERIVED_VERBS = ("find", "read", "get", "query", "search", "stream", "count", "exists",
                 "delete", "remove")

# Longest first, so "GreaterThanEqual" is stripped before "GreaterThan" can match it.
CONDITION_KEYWORDS = sorted(
    ["Is", "Equals", "Not", "IgnoreCase", "AllIgnoreCase", "Containing", "Contains",
     "StartingWith", "EndingWith", "Like", "NotLike", "In", "NotIn", "Between",
     "LessThan", "LessThanEqual", "GreaterThan", "GreaterThanEqual", "After", "Before",
     "IsNull", "IsNotNull", "NotNull", "IsEmpty", "IsNotEmpty", "True", "IsTrue",
     "False", "IsFalse"],
    key=len, reverse=True)


def lower_first(text: str) -> str:
    return text[0].lower() + text[1:] if text else text


def element_type(type_text: str) -> str:
    """What a property path continues into: a container's element, otherwise the type itself."""
    text = type_text.strip()
    match = re.match(r"(\w+)<(.+)>$", text)
    if match and match.group(1) in CONTAINER_TYPES:
        return match.group(2).split(",")[0].strip().split("<")[0].split(".")[-1]
    return text.split("<")[0].split(".")[-1]


def declared_properties(main_java: Path) -> dict[str, dict[str, str]]:
    """Every project class's fields, flattened through `extends` so inherited ids resolve too."""
    own: dict[str, tuple[str | None, dict[str, str]]] = {}
    for path in sorted(main_java.rglob("*.java")):
        code = read_cleaned(path)
        declaration = re.search(
            r"^(?:public\s+)?(?:abstract\s+|final\s+)*class\s+(\w+)(?:\s+extends\s+(\w+))?",
            code, re.MULTILINE)
        if not declaration:
            continue
        fields: dict[str, str] = {}
        for line in code.splitlines():
            stripped = line.strip()
            if " static " in f" {stripped} ":
                continue
            field = FIELD_DECL.match(stripped)
            if field:
                fields[field.group(2)] = element_type(field.group(1))
        own[declaration.group(1)] = (declaration.group(2), fields)

    flat: dict[str, dict[str, str]] = {}

    def flatten(name: str, seen: tuple[str, ...] = ()) -> dict[str, str]:
        if name in flat:
            return flat[name]
        if name not in own or name in seen:
            return {}
        parent, fields = own[name]
        merged = dict(flatten(parent, seen + (name,))) if parent else {}
        merged.update(fields)
        flat[name] = merged
        return merged

    for name in own:
        flatten(name)
    return flat


def resolve_property(props: dict[str, dict[str, str]], type_name: str, expression: str) -> bool:
    """Can this expression be read off this type? Longest prefix first, with backtracking.

    `UserId` has to become `user` then `id`, and only trying the longest prefix first — then
    giving ground — gets both that and a single field named `publicId` right.
    """
    if not expression:
        return True
    fields = props.get(type_name)
    if not fields:
        return False  # a String, a UUID or an enum has no properties left to walk into
    for cut in range(len(expression), 0, -1):
        candidate = lower_first(expression[:cut])
        if candidate in fields and resolve_property(props, fields[candidate], expression[cut:]):
            return True
    return False


def strip_condition_keywords(condition: str) -> str:
    """Peel `In`, `GreaterThanEqual`, `IgnoreCase` and friends off the end of a condition."""
    trimmed = condition
    peeling = True
    while peeling:
        peeling = False
        for keyword in CONDITION_KEYWORDS:
            if trimmed.endswith(keyword) and len(trimmed) > len(keyword):
                trimmed = trimmed[: -len(keyword)]
                peeling = True
                break
    return trimmed


def split_annotations(member: str) -> tuple[list[str], str]:
    """Separate a member's leading annotations from its declaration."""
    annotations: list[str] = []
    text = member.strip()
    while text.startswith("@"):
        match = re.match(r"@(\w+)", text)
        if not match:
            break
        annotations.append(match.group(1))
        rest = text[match.end():].lstrip()
        if rest.startswith("("):
            depth = 0
            for index, character in enumerate(rest):
                if character == "(":
                    depth += 1
                elif character == ")":
                    depth -= 1
                    if depth == 0:
                        rest = rest[index + 1:].lstrip()
                        break
        text = rest
    return annotations, text


def interface_members(cleaned: str) -> list[tuple[list[str], str]]:
    """Split an interface body into `;`-terminated members, annotations kept with each one.

    Character based rather than line based because an annotation can wrap over several lines —
    the JPQL text blocks in this codebase all do.
    """
    if "{" not in cleaned:
        return []
    members: list[str] = []
    buffer: list[str] = []
    depth = 0
    for character in cleaned[cleaned.index("{") + 1:]:
        if character in "([{":
            depth += 1
        elif character in ")]}":
            depth -= 1
            if depth < 0:
                break  # the interface's own closing brace
        if character == ";" and depth == 0:
            members.append(" ".join("".join(buffer).split()))
            buffer = []
            continue
        buffer.append(character)
    return [split_annotations(member) for member in members if member.strip()]


def verify_derived_queries(root: Path) -> None:
    main_java = root / "backend" / "src" / "main" / "java"
    if not main_java.is_dir():
        return
    props = declared_properties(main_java)

    for path in sorted(main_java.rglob("*Repository.java")):
        rel = path.relative_to(root).as_posix()
        cleaned = read_cleaned(path)
        managed = re.search(r"extends\s+\w*Repository<\s*(\w+)\s*,", cleaned)
        if not managed:
            continue
        entity = managed.group(1)
        check(f"{rel} manages a class this checker can read", entity in props,
              f"no class named {entity} was parsed, so its properties cannot be verified")
        if entity not in props:
            continue

        for annotations, declaration in interface_members(cleaned):
            if declaration.startswith(("default ", "static ")):
                continue
            named = re.search(r"[\w>\]?]\s+(\w+)\s*\(", declaration)
            if not named:
                continue
            method = named.group(1)
            if "Query" in annotations:
                continue  # the JPQL is written out, so nothing is derived from the name
            if not method.startswith(DERIVED_VERBS) or "By" not in method:
                continue

            if method.startswith(("delete", "remove")):
                # Spring Data's generated implementation is @Transactional(readOnly = true),
                # which leaves Hibernate in FlushMode.MANUAL. An unannotated derived delete
                # then loads the rows, removes them from the session, never flushes, and still
                # returns a count. The repository tests cannot see it — they supply their own
                # transaction — so the annotation is checked here instead.
                check(f"{rel}.{method} declares @Transactional",
                      "Transactional" in annotations,
                      "a read-only derived delete reports rows removed and removes nothing")

            _, _, tail = method.partition("By")
            subject, _, ordering = tail.partition("OrderBy")

            # And/Or only split on a property boundary: "OriginalFilename" is one property,
            # not "Or" followed by a mangled second one.
            for condition in re.split(r"(?:And|Or)(?=[A-Z_])", subject):
                expression = strip_condition_keywords(condition.replace("_", ""))
                if not expression:
                    continue
                check(f"{rel}.{method} resolves {entity}.{lower_first(expression)}",
                      resolve_property(props, entity, expression),
                      "the parser resolves this against the entity's properties at startup, "
                      "and a name it cannot resolve stops the context from starting")

            for sort_key in re.split(r"(?:Asc|Desc)(?=[A-Z_]|$)", ordering):
                expression = sort_key.replace("_", "")
                if not expression:
                    continue
                check(f"{rel}.{method} sorts by {entity}.{lower_first(expression)}",
                      resolve_property(props, entity, expression),
                      "an order-by clause is resolved against the entity's properties too")


# ---------------------------------------------------------------------------
# The seed catalogue. Nothing here runs the seeder, so its data is checked the
# way the seeder would see it — including a replica of Skill.slugify, because a
# slug collision is silent: the second skill is simply never inserted.
# ---------------------------------------------------------------------------

def slugify(raw: str) -> str:
    lowered = raw.strip().lower().replace("+", "plus").replace("#", "sharp")
    if lowered.startswith("."):
        lowered = "dot" + lowered[1:]
    return re.sub(r"(^-+)|(-+$)", "", re.sub(r"[^a-z0-9]+", "-", lowered))


def verify_skill_catalog(root: Path) -> None:
    catalog = root / "backend" / "src" / "main" / "resources" / "data" / "skills.json"
    enum_file = root / "backend" / "src" / "main" / "java" / "com" / "resumeiq" / "skill" / "SkillCategory.java"
    check("skills.json exists", catalog.is_file())
    check("SkillCategory.java exists", enum_file.is_file())
    if not (catalog.is_file() and enum_file.is_file()):
        return

    # The Java source is the authority on which categories exist.
    body = read_cleaned(enum_file)
    categories = set(re.findall(r"^\s{4}([A-Z][A-Z_]*)\b", body, re.MULTILINE))
    check("SkillCategory declares constants", bool(categories))

    try:
        entries = json.loads(catalog.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        check("skills.json parses", False, str(exc))
        return
    check("skills.json is a non-empty list", isinstance(entries, list) and bool(entries))

    slugs: dict[str, str] = {}
    aliases: dict[str, str] = {}
    used_categories = set()

    for entry in entries:
        name = entry.get("name", "")
        category = entry.get("category", "")
        check(f"catalogue entry {name or entry!r} has a name", bool(name))
        check(f"catalogue entry '{name}' has a known category", category in categories,
              f"'{category}' is not a SkillCategory constant")
        used_categories.add(category)

        slug = slugify(name) if name else ""
        check(f"'{name}' produces a usable slug", bool(slug))
        check(f"slug '{slug}' fits the column", len(slug) <= 80, f"{len(slug)} characters")
        check(f"display name '{name}' fits the column", len(name) <= 80, f"{len(name)} characters")
        owner = slugs.setdefault(slug, name)
        check(f"slug '{slug}' belongs to one skill", owner == name,
              f"'{name}' and '{owner}' both slug to it, so one would never be inserted")

        for alias in entry.get("aliases") or []:
            normalised = slugify(alias)
            check(f"alias '{alias}' of '{name}' normalises to something", bool(normalised))
            check(f"alias '{alias}' is not '{name}'s own slug", normalised != slug,
                  "the seeder drops it, so it is noise in the catalogue")
            holder = aliases.setdefault(normalised, name)
            check(f"alias '{normalised}' is claimed by one skill", holder == name,
                  f"claimed by both '{name}' and '{holder}', so resolution would depend on row order")

    for alias, holder in sorted(aliases.items()):
        check(f"alias '{alias}' does not shadow a canonical slug", alias not in slugs,
              f"'{alias}' is also the slug of '{slugs.get(alias)}'")

    for category in sorted(categories):
        check(f"category {category} has at least one skill", category in used_categories,
              "an empty category is a dead filter in the UI and a blind spot in the gap analysis")

    # The seeder is only free to re-run if it is switched on by configuration.
    app_yml = root / "backend" / "src" / "main" / "resources" / "application.yml"
    if app_yml.is_file():
        config = yaml.safe_load(app_yml.read_text(encoding="utf-8")) or {}
        seed = ((config.get("resumeiq") or {}).get("seed") or {})
        check("seeding is configurable", "skills" in seed,
              "resumeiq.seed.skills must exist for ResumeIqProperties.Seed to bind")


def main() -> int:
    default_root = Path(__file__).resolve().parent.parent
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else default_root
    print(f"Verifying {root}\n")
    verify_configs(root)
    verify_java(root)
    verify_bean_construction(root)
    verify_record_arity(root)
    verify_java_imports(root)
    verify_persistence(root)
    verify_repositories(root)
    verify_derived_queries(root)
    verify_skill_catalog(root)
    verify_frontend(root)
    verify_js_syntax(root)
    verify_jsx_nesting(root)
    verify_tokens(root)
    verify_contract(root)
    verify_security(root)

    print(f"{CHECKS_RUN} checks run")
    if WARNINGS:
        print(f"\n{len(WARNINGS)} warning(s):")
        for item in WARNINGS:
            print(f"  ! {item}")
    if FAILURES:
        print(f"\n{len(FAILURES)} FAILURE(S):")
        for item in FAILURES:
            print(f"  ✗ {item}")
        return 1
    print("\nAll static checks passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
