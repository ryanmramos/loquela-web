"""Strict JSON contract shared with the Spring export snapshot."""

from __future__ import annotations

from dataclasses import dataclass
import re
from typing import Any
from uuid import UUID

SCHEMA_VERSION = 1
TEMPLATE_VERSION = "whatsanki-context-v1"
NOTE_TYPE_NAME = "WhatsAnki Context v1"
FIELD_ORDER = (
    "Term", "ContextBefore", "ContextTarget", "ContextAfter", "ContextTranslation",
    "Definition", "DefinitionDetail", "Pronunciation", "PartOfSpeech", "Gender",
    "Number", "WordFamily", "SourceName", "SourceLocator", "LanguagePair",
    "SentenceAudio", "WordAudio", "Video", "TextRecognition", "VideoRecognition", "Recall",
)
REQUIRED_FIELDS = ("Term", "ContextTarget", "Definition")
ENABLEMENT_FIELDS = ("TextRecognition", "VideoRecognition", "Recall")
MEDIA_FIELDS = ("SentenceAudio", "WordAudio")
YOUTUBE_EMBED_URL = re.compile(
    r"https://www\.youtube-nocookie\.com/embed/[A-Za-z0-9_-]{11}\?start=\d+&rel=0"
)


class ContractError(ValueError):
    """A permanent, safe-to-report snapshot validation failure."""


@dataclass(frozen=True)
class CardSnapshot:
    stable_id: UUID
    fields: tuple[str, ...]


@dataclass(frozen=True)
class ExportSnapshot:
    export_id: UUID
    deck_id: UUID
    deck_name: str
    cards: tuple[CardSnapshot, ...]


def parse_snapshot(value: Any) -> ExportSnapshot:
    root = _mapping(value, "snapshot")
    _exact_keys(root, {"schemaVersion", "exportId", "deck", "templateVersion", "cards"}, "snapshot")
    _version(root.get("schemaVersion"), "snapshot")
    if root.get("templateVersion") != TEMPLATE_VERSION:
        raise ContractError("unsupported template version")

    deck = _mapping(root.get("deck"), "deck")
    _exact_keys(deck, {"stableId", "name"}, "deck")
    deck_name = _required_text(deck.get("name"), "deck.name")
    cards_value = root.get("cards")
    if not isinstance(cards_value, list) or not cards_value:
        raise ContractError("cards must be a non-empty array")

    return ExportSnapshot(
        export_id=_uuid(root.get("exportId"), "exportId"),
        deck_id=_uuid(deck.get("stableId"), "deck.stableId"),
        deck_name=deck_name,
        cards=tuple(_card(card, index) for index, card in enumerate(cards_value)),
    )


def _card(value: Any, index: int) -> CardSnapshot:
    card = _mapping(value, f"cards[{index}]")
    _exact_keys(card, {"stableId", "noteType", "anki"}, f"cards[{index}]")
    if card.get("noteType") != "BASIC_CONTEXT":
        raise ContractError("unsupported card note type")
    anki = _mapping(card.get("anki"), f"cards[{index}].anki")
    _exact_keys(anki, {"schemaVersion", "noteTypeName", "templateVersion", "fields"}, "anki")
    _version(anki.get("schemaVersion"), "anki")
    if anki.get("noteTypeName") != NOTE_TYPE_NAME or anki.get("templateVersion") != TEMPLATE_VERSION:
        raise ContractError("unsupported Anki note contract")

    fields = _mapping(anki.get("fields"), f"cards[{index}].anki.fields")
    _exact_keys(fields, set(FIELD_ORDER), "fields")
    ordered: list[str] = []
    for name in FIELD_ORDER:
        field = fields.get(name)
        if not isinstance(field, str):
            raise ContractError(f"field {name} must be text")
        ordered.append(field)
    for name in REQUIRED_FIELDS:
        if not fields[name].strip():
            raise ContractError(f"field {name} must not be blank")
    if not any(fields[name].strip() for name in ENABLEMENT_FIELDS):
        raise ContractError("card has no enabled template")
    # Snapshot v1 has no media manifest from which file existence could be proven.
    # Fail closed instead of emitting dangling or attacker-controlled references.
    if any(fields[name].strip() for name in MEDIA_FIELDS):
        raise ContractError("snapshot v1 does not support media fields")
    video_enabled = bool(fields["VideoRecognition"].strip())
    video_url = fields["Video"].strip()
    if video_enabled != bool(video_url):
        raise ContractError("video recognition and video URL must be enabled together")
    if video_url and YOUTUBE_EMBED_URL.fullmatch(video_url) is None:
        raise ContractError("video URL is not an approved YouTube embed URL")
    return CardSnapshot(_uuid(card.get("stableId"), "card.stableId"), tuple(ordered))


def _mapping(value: Any, name: str) -> dict[str, Any]:
    if not isinstance(value, dict) or not all(isinstance(key, str) for key in value):
        raise ContractError(f"{name} must be an object")
    return value


def _exact_keys(value: dict[str, Any], expected: set[str], name: str) -> None:
    if set(value) != expected:
        raise ContractError(f"{name} has invalid fields")


def _version(value: Any, name: str) -> None:
    # bool is an int subclass in Python and must not be accepted as schema version 1.
    if type(value) is not int or value != SCHEMA_VERSION:
        raise ContractError(f"unsupported {name} schema version")


def _uuid(value: Any, name: str) -> UUID:
    if not isinstance(value, str):
        raise ContractError(f"{name} must be a UUID string")
    try:
        parsed = UUID(value)
    except ValueError as error:
        raise ContractError(f"{name} must be a UUID string") from error
    if str(parsed) != value.lower():
        raise ContractError(f"{name} must be a canonical UUID string")
    return parsed


def _required_text(value: Any, name: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ContractError(f"{name} must not be blank")
    return value.strip()
