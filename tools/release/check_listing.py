#!/usr/bin/env python3
"""Check the F-Droid listing in fastlane/metadata/android/ (#66).

Every locale must have the files en-US has (title, short description, the
changelogs, and the full description once it exists), within F-Droid's
limits, with the same HTML structure as the English full description and every
protected name the English uses. Run from anywhere:
    python3 tools/release/check_listing.py
Exits 1 and lists the problems if any check fails.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
META = ROOT / "fastlane/metadata/android"
LOCALES = ["en-US", "de-DE", "es-ES", "fr-FR", "hi-IN", "it-IT", "ja-JP", "pt-BR", "zh-CN",
           "ar", "cs-CZ", "id", "ko-KR", "nl-NL", "pl-PL", "ru-RU", "sv-SE", "tr-TR", "uk", "vi", "zh-TW"]
LIMITS = {"title.txt": 50, "short_description.txt": 80, "full_description.txt": 4000}
CHANGELOG_LIMIT = 500
# F-Droid renders a small HTML subset in full_description.txt.
ALLOWED_TAGS = {"p", "b", "i", "u", "em", "strong", "ul", "ol", "li", "br", "a"}
# Names that stay in English in every locale (docs/i18n/TRANSLATING.md).
PROTECTED = [
    "Marmalade STT", "FUTO", "HeliBoard", "FlorisBoard", "AnySoftKeyboard", "Unexpected Keyboard",
    "Smart Turn", "Parakeet", "Canary", "Nemotron", "Whisper", "Qwen3-ASR", "Granite", "GitHub",
    "GitHub Sponsors", "Hugging Face", "1.0.0", "1.0.1", "1.1.0", "Duolingo", "1.2.0", "Marmalade TTS",
]
TAG = re.compile(r"</?([a-zA-Z0-9]+)[^>]*>")


def files(locale):
    base = META / locale
    return sorted(p.relative_to(base).as_posix() for p in base.rglob("*.txt"))


def check(locale, rel, text, english, problems):
    where = f"{locale}/{rel}"
    if not text.endswith("\n") or text.endswith("\n\n"):
        problems.append(f"{where}: must end with exactly one newline")
    body = text.rstrip("\n")
    limit = CHANGELOG_LIMIT if rel.startswith("changelogs/") else LIMITS.get(rel)
    if limit and len(body) > limit:
        problems.append(f"{where}: {len(body)} chars, limit {limit}")
    if rel in ("title.txt", "short_description.txt") and "\n" in body:
        problems.append(f"{where}: must be one line")
    if any(line != line.rstrip() for line in body.split("\n")):
        problems.append(f"{where}: trailing whitespace")
    if "&amp;#" in body or "&amp;amp;" in body:
        problems.append(f"{where}: double-escaped entity")
    if rel == "title.txt" and locale != "en-US" and body != english.rstrip("\n"):
        problems.append(f"{where}: the title is not translated; must be {english.strip()!r}")
    if rel == "full_description.txt":
        tags = TAG.findall(body)
        bad = sorted({t.lower() for t in tags} - ALLOWED_TAGS)
        if bad:
            problems.append(f"{where}: tags F-Droid doesn't render: {bad}")
        if [t.lower() for t in tags] != [t.lower() for t in TAG.findall(english)]:
            problems.append(f"{where}: HTML tag sequence differs from en-US")
    for name in PROTECTED:
        if name in english and name not in body:
            problems.append(f"{where}: missing protected name {name!r}")


def main():
    problems = []
    expected = files("en-US")
    for locale in LOCALES:
        if not (META / locale).is_dir():
            problems.append(f"{locale}: folder missing")
            continue
        have = files(locale)
        for rel in sorted(set(expected) - set(have)):
            problems.append(f"{locale}/{rel}: missing")
        for rel in sorted(set(have) - set(expected)):
            problems.append(f"{locale}/{rel}: not in en-US")
        for rel in sorted(set(have) & set(expected)):
            text = (META / locale / rel).read_text(encoding="utf-8")
            english = (META / "en-US" / rel).read_text(encoding="utf-8")
            check(locale, rel, text, english, problems)
    extra = sorted(p.name for p in META.iterdir() if p.is_dir() and p.name not in LOCALES)
    if extra:
        problems.append(f"unexpected locale folders: {extra}")
    for p in problems:
        print(p)
    width = max(len(r) for r in expected)
    for rel in expected:
        lens = []
        for locale in LOCALES:
            f = META / locale / rel
            lens.append(f"{locale} {len(f.read_text(encoding='utf-8').rstrip(chr(10)))}" if f.exists() else f"{locale} -")
        print(f"{rel:<{width}}  " + " · ".join(lens))
    print("OK" if not problems else f"{len(problems)} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
