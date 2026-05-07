-- Phase 5b i18n: per-locale translation maps for leave request notes.
ALTER TABLE leave_requests
    ADD COLUMN IF NOT EXISTS note_translations         TEXT,
    ADD COLUMN IF NOT EXISTS decision_note_translations TEXT;

COMMENT ON COLUMN leave_requests.note_translations IS
    'JSON map of locale -> translated staff note. Reserved keys: _source, _auto.';
COMMENT ON COLUMN leave_requests.decision_note_translations IS
    'JSON map of locale -> translated manager decision note.';
