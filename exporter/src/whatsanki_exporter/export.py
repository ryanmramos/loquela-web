"""Generate and independently verify a WhatsAnki Anki package."""

from __future__ import annotations

import hashlib
import html
import os
import sqlite3
import tempfile
import zipfile
from importlib.resources import files
from pathlib import Path
from uuid import UUID, uuid5

import genanki

from .contract import ExportSnapshot, FIELD_ORDER, TEMPLATE_VERSION

MODEL_NAMESPACE = UUID("d9db164f-4a0b-4b55-bbf2-38cb1505d88b")


def export_package(snapshot: ExportSnapshot, output_path: Path) -> tuple[int, str]:
    model = genanki.Model(
        stable_integer(uuid5(MODEL_NAMESPACE, TEMPLATE_VERSION), bits=31),
        "WhatsAnki Context v1",
        fields=[{"name": name} for name in FIELD_ORDER],
        templates=_templates(),
        css=_template_text("style.css"),
    )
    deck = genanki.Deck(stable_integer(snapshot.deck_id, bits=63), snapshot.deck_name)
    for card in snapshot.cards:
        # Escape at the renderer boundary. Stored source/provider text remains plain
        # Unicode while Anki receives inert HTML-safe field values.
        fields = [html.escape(value, quote=True) for value in card.fields]
        deck.add_note(genanki.Note(model=model, fields=fields, guid=stable_guid(card.stable_id)))

    output_path.parent.mkdir(parents=True, exist_ok=True)
    temporary = output_path.with_name(f".{output_path.name}.{os.getpid()}.tmp")
    try:
        genanki.Package(deck).write_to_file(str(temporary))
        _inspect_package(temporary, len(snapshot.cards))
        size = temporary.stat().st_size
        digest = _sha256(temporary)
        os.replace(temporary, output_path)
        return size, digest
    finally:
        temporary.unlink(missing_ok=True)


def stable_integer(value: UUID, *, bits: int) -> int:
    """Derive a deterministic positive Anki ID without randomized hash()."""
    digest = hashlib.sha256(value.bytes).digest()
    result = int.from_bytes(digest[:8], "big") & ((1 << bits) - 1)
    return result or 1


def stable_guid(value: UUID) -> str:
    # Anki stores the guid as text; a UUID-derived digest is stable and contains no
    # user data or raw internal UUID in the exported collection.
    return hashlib.sha256(b"whatsanki-note-v1\0" + value.bytes).hexdigest()[:20]


def _templates() -> list[dict[str, str]]:
    return [
        {"name": "Recognition · Text", "qfmt": _template_text("text-recognition/front.html"),
         "afmt": _template_text("text-recognition/back.html")},
        {"name": "Recognition · Video", "qfmt": _template_text("video-recognition/front.html"),
         "afmt": _template_text("video-recognition/back.html")},
        {"name": "Recall · Meaning to word", "qfmt": _template_text("recall/front.html"),
         "afmt": _template_text("recall/back.html")},
    ]


def _template_text(relative_name: str) -> str:
    resource = files("whatsanki_exporter").joinpath("templates", TEMPLATE_VERSION, relative_name)
    return resource.read_text(encoding="utf-8")


def _inspect_package(path: Path, expected_notes: int) -> None:
    if not path.is_file() or path.stat().st_size == 0 or not zipfile.is_zipfile(path):
        raise ValueError("generated artifact is not a readable package")
    with zipfile.ZipFile(path) as archive:
        if archive.testzip() is not None or "collection.anki2" not in archive.namelist() or "media" not in archive.namelist():
            raise ValueError("generated package is incomplete")
        with tempfile.TemporaryDirectory(prefix="whatsanki-apkg-check-") as directory:
            collection = Path(directory, "collection.anki2")
            collection.write_bytes(archive.read("collection.anki2"))
            with sqlite3.connect(f"file:{collection}?mode=ro", uri=True) as database:
                note_count = database.execute("select count(*) from notes").fetchone()[0]
                if note_count != expected_notes:
                    raise ValueError("generated package note count does not match snapshot")


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as artifact:
        for block in iter(lambda: artifact.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()
