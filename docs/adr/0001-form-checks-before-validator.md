# 0001 - Form checks before the validator; validator exposes the field

Status: accepted (architect, 2026-11-10)

## Context
UX requires one short message under each wrong field, all at once on save, focus on the first wrong field, and no validation dialog. Today `WorkShiftValidator.validateAndPopulate` throws `IllegalArgumentException` at the first problem, with messages written for a dialog ("Horário inicial inválido..."). About 16 existing tests assert those messages.

## Options
1. Rewrite the validator to collect all errors with UX texts. Breaks many existing message tests; mixes screen wording into the domain gate.
2. Parse the validator's message text to find the field. Fragile.
3. Pure `ShiftFormCheck` with UX texts per field (all errors, Tab order), run first; validator unchanged in rules and messages, but throws `FieldValidationException(field, message) extends IllegalArgumentException` so any remaining error can still be placed under a field.

## Decision
Option 3.

## Consequences
- Existing validator tests pass unchanged (`assertThrows(IllegalArgumentException.class, ...)` accepts the subclass; messages are identical).
- Two places know some rules (required, KM order, notes length). Mitigated by a test in T5a: inputs accepted by the checker are accepted by the validator.
- The live calculation keeps calling the validator and catching `IllegalArgumentException`, as today.
