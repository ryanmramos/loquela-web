import copy
import unittest
from uuid import uuid4

from whatsanki_exporter.contract import ContractError, FIELD_ORDER, parse_snapshot


def valid_snapshot():
    fields = {name: "" for name in FIELD_ORDER}
    fields.update({
        "Term": "vivir",
        "ContextBefore": "Quiero ",
        "ContextTarget": "vivir",
        "ContextAfter": " aquí.",
        "Definition": "to live",
        "TextRecognition": "1",
    })
    return {
        "schemaVersion": 1,
        "exportId": str(uuid4()),
        "deck": {"stableId": str(uuid4()), "name": "Spanish Reading"},
        "templateVersion": "whatsanki-context-v1",
        "cards": [{
            "stableId": str(uuid4()),
            "noteType": "BASIC_CONTEXT",
            "anki": {
                "schemaVersion": 1,
                "noteTypeName": "WhatsAnki Context v1",
                "templateVersion": "whatsanki-context-v1",
                "fields": fields,
            },
        }],
    }


class ContractTest(unittest.TestCase):
    def test_parses_unicode_snapshot_in_declared_field_order(self):
        parsed = parse_snapshot(valid_snapshot())

        self.assertEqual("Spanish Reading", parsed.deck_name)
        self.assertEqual("vivir", parsed.cards[0].fields[0])
        self.assertEqual(" aquí.", parsed.cards[0].fields[3])
        self.assertEqual(len(FIELD_ORDER), len(parsed.cards[0].fields))

    def test_rejects_unknown_fields_and_boolean_schema_version(self):
        extra = valid_snapshot()
        extra["privateUserId"] = str(uuid4())
        boolean_version = valid_snapshot()
        boolean_version["schemaVersion"] = True

        with self.assertRaises(ContractError):
            parse_snapshot(extra)
        with self.assertRaises(ContractError):
            parse_snapshot(boolean_version)

    def test_rejects_missing_required_or_enablement_fields(self):
        missing_required = valid_snapshot()
        missing_required["cards"][0]["anki"]["fields"]["Definition"] = " "
        disabled = copy.deepcopy(valid_snapshot())
        disabled["cards"][0]["anki"]["fields"]["TextRecognition"] = ""

        with self.assertRaises(ContractError):
            parse_snapshot(missing_required)
        with self.assertRaises(ContractError):
            parse_snapshot(disabled)

    def test_rejects_media_without_a_versioned_manifest(self):
        snapshot = valid_snapshot()
        snapshot["cards"][0]["anki"]["fields"]["SentenceAudio"] = "[sound:missing.mp3]"

        with self.assertRaises(ContractError):
            parse_snapshot(snapshot)

    def test_accepts_definition_only_recall_card(self):
        snapshot = valid_snapshot()
        fields = snapshot["cards"][0]["anki"]["fields"]
        fields["ContextBefore"] = ""
        fields["ContextAfter"] = ""
        fields["TextRecognition"] = ""
        fields["Recall"] = "1"

        parsed = parse_snapshot(snapshot)

        self.assertEqual("vivir", parsed.cards[0].fields[0])
        self.assertIn("1", parsed.cards[0].fields)

    def test_accepts_only_a_controlled_youtube_video_url(self):
        snapshot = valid_snapshot()
        fields = snapshot["cards"][0]["anki"]["fields"]
        fields["TextRecognition"] = ""
        fields["VideoRecognition"] = "1"
        fields["Video"] = "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?start=62&rel=0"

        parsed = parse_snapshot(snapshot)

        self.assertIn(fields["Video"], parsed.cards[0].fields)

    def test_rejects_unpaired_or_untrusted_video_fields(self):
        unpaired = valid_snapshot()
        unpaired["cards"][0]["anki"]["fields"]["VideoRecognition"] = "1"
        untrusted = valid_snapshot()
        untrusted_fields = untrusted["cards"][0]["anki"]["fields"]
        untrusted_fields["VideoRecognition"] = "1"
        untrusted_fields["Video"] = "javascript:alert(1)"

        with self.assertRaises(ContractError):
            parse_snapshot(unpaired)
        with self.assertRaises(ContractError):
            parse_snapshot(untrusted)


if __name__ == "__main__":
    unittest.main()
