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
    r"^\s*(?:public\s+|final\s+|abstract\s+)*(class|interface|enum|record)\s+(\w+)", re.MULTILINE)


def verify_java(root: Path) -> None:
    java_files = sorted((root / "backend" / "src").rglob("*.java"))
    check("backend has Java sources", bool(java_files))

    declared: dict[str, Path] = {}
    for path in java_files:
        source = path.read_text(encoding="utf-8")
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

        cleaned = strip_literals(source, java=True)
        for type_name, member in re.findall(r"\b([A-Z][A-Za-z0-9]*)\.([a-z]\w*)\s*\(", cleaned):
            if type_name not in declared or declared[type_name] == path:
                continue
            target = declared[type_name].read_text(encoding="utf-8")
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


def tracked_files(root: Path) -> list[Path]:
    skip = {"node_modules", "target", "dist", ".git", "storage", "build"}
    found = []
    for path in root.rglob("*"):
        if not path.is_file() or any(part in skip for part in path.parts):
            continue
        if path.suffix in TRACKED_EXTENSIONS or path.name == ".env.example":
            found.append(path)
    return sorted(found)


def verify_security(root: Path) -> None:
    files = tracked_files(root)

    for path in files:
        rel = path.relative_to(root).as_posix()
        text = path.read_text(encoding="utf-8", errors="replace")
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
    for path in (root / "frontend").rglob("*"):
        if path.is_file() and path.suffix in {".js", ".jsx", ".html", ".json"} \
                and "node_modules" not in path.parts:
            text = path.read_text(encoding="utf-8", errors="replace")
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
        text = path.read_text(encoding="utf-8", errors="replace")
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
                if not self_closing and name.lower() not in VOID_ELEMENTS:
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


def verify_java_imports(root: Path) -> None:
    files = sorted((root / "backend" / "src").rglob("*.java"))
    by_package: dict[str, set[str]] = {}
    for path in files:
        source = path.read_text(encoding="utf-8")
        package = re.search(r"^package\s+([\w.]+);", source, re.MULTILINE)
        if package:
            by_package.setdefault(package.group(1), set()).update(
                name for _, name in TYPE_DECL.findall(source))

    for path in files:
        source = path.read_text(encoding="utf-8")
        rel = path.relative_to(root).as_posix()
        code = strip_literals(source, java=True)
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


def main() -> int:
    default_root = Path(__file__).resolve().parent.parent
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else default_root
    print(f"Verifying {root}\n")
    verify_configs(root)
    verify_java(root)
    verify_java_imports(root)
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
