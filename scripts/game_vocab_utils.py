"""SPD vocabulary helpers: short Whisper prompt + post-transcript lexicon cleanup."""

import os
import re

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DEFAULT_VOCAB_PATH = os.path.join(SCRIPT_DIR, "game_vocabulary.txt")

SHORT_WHISPER_PROMPT = "Shattered Pixel Dungeon live commentary."


def load_vocabulary_terms(vocab_path: str = None) -> list:
    path = vocab_path or DEFAULT_VOCAB_PATH
    if not os.path.exists(path):
        return []
    terms = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#"):
                terms.append(line)
    # Longer phrases first so multi-word terms win over shorter ones
    terms.sort(key=lambda t: len(t), reverse=True)
    return terms


def whisper_initial_prompt() -> str:
    return SHORT_WHISPER_PROMPT


def correct_transcript_with_lexicon(transcript: list, vocab_path: str = None) -> list:
    """
    Case-insensitive phrase/word replacement using game_vocabulary.txt terms.
    Mutates segment text and word tokens in place; returns the same list.
    """
    terms = load_vocabulary_terms(vocab_path)
    if not terms or not transcript:
        return transcript

    # Build patterns: exact term match as whole words (allow spaces inside term)
    compiled = []
    for term in terms:
        # Escape, then allow flexible whitespace between words of multi-word terms
        parts = [re.escape(p) for p in term.split()]
        pattern = r"\b" + r"\s+".join(parts) + r"\b"
        compiled.append((re.compile(pattern, re.IGNORECASE), term))

    for seg in transcript:
        text = seg.get("text") or ""
        for rx, term in compiled:
            text = rx.sub(term, text)
        seg["text"] = text

        words = seg.get("words") or []
        for w in words:
            raw = w.get("word") or ""
            fixed = raw
            for rx, term in compiled:
                # Only replace single-token-ish matches on individual words
                if " " not in term and rx.fullmatch(raw.strip()):
                    # Preserve surrounding punctuation by replacing core token
                    fixed = rx.sub(term, raw)
                    break
            w["word"] = fixed

        # Also fix multi-word phrases already reflected in segment text;
        # word-level multi-word is best-effort via segment text only.

    return transcript
