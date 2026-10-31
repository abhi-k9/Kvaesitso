# Tagging, suggestions, widget stacks and performance

Implemented against fork baseline `d163267e5`.

## Behavior

- Tag pickers search names, custom labels and package names, with a derived Untagged
  filter. Selection survives query/filter changes. The app menu opens a bulk picker;
  applying a tag preserves existing tag members, other tags, pins and suggestions.
- Apps can be excluded from frequently used suggestions and restored individually
  from the app menu or Settings > Search > Favorites > Excluded suggestions.
  Search, app-list visibility, pins and query learning keep their existing behavior.
  Room migration 34 to 35 adds a false-by-default flag; backups preserve it and old
  backups default to allowing suggestions.
- Adjacent widgets can become a manually paged stack. Built-in and app-provided
  widgets share a configurable frame, with arrows as an alternative to swiping.
  The editor supports adding, reordering, configuring, removing and separating
  pages. Existing widget UUIDs, widths and Android host IDs survive grouping and
  separation. Stacks cannot nest.
- Stack operations and restoration use transactions. Undo inserts against the
  current order and refuses to recreate children under a removed stack. Provider
  IDs remain alive until Undo completes and are released on dismissal/cancellation.
  Stack backups use one consistent snapshot and restore all validated pages atomically.

## Performance changes

- Prepare query whitespace, word tokens and FZF maxima once per candidate batch.
  The immutable scorer snapshots the keyboard layout and retains no candidate data.
- Skip writes for unchanged search categories, using full content equality;
  changed lists publish atomically. Hidden-result filtering uses a set of keys.
- Resolve widget/provider display labels once per provider snapshot. Package
  lookups stay off the UI thread and out of keystroke filters and sort comparators.
  Reopening the picker obtains a fresh snapshot.
- Index custom labels once per update, preserving the old first-match behavior
  for duplicate keys. This replaces a label-list scan for every item.
- Bulk tagging awaits saved-item insertion in a batch before assigning tags.
  Widget dragging updates its offset without launching a coroutine per pointer move.
- The stack pager composes the current page and pages required by an active swipe;
  it does not precompose every widget or rotate pages on a timer.

## Scoring benchmark

A standalone Java 21/Kotlin harness compiled the baseline and current scorer
together. All 12,000 scores matched exactly: 20 queries, 50 labels, six keyboard
layouts, and both primary and secondary fields. Cases included partial typos,
multiword queries, exact matches and whitespace normalization.

The timing workload scored eight queries against 500 labels (4,000 candidate
scores per batch). After 300 alternating warmup batches, nine alternating samples
measured 100 batches each. Median values on the cloud JVM were:

| Metric, per batch | Baseline | Prepared queries |
| --- | ---: | ---: |
| Elapsed time | 1.437 ms | 1.075 ms |
| Thread-allocated memory | 1,701,120 bytes | 1,157,872 bytes |

This is about 25% less scoring time and 32% fewer allocated bytes for that workload.
These are JVM microbenchmark results, not phone frame-rate, battery or end-to-end
search measurements. The harness and raw output for this run are retained under
`/workspace/feature-validation/benchmark` in the execution workspace.

## Validation

- `bash .github/scripts/unit-tests.sh --max-workers=4 --console=plain`: passed.
  332 tests reported: 331 passed, one pre-existing skip, zero failures or errors.
  The change adds 59 regression tests covering migration, backup compatibility,
  suggestion filtering, bulk tags, picker selection, paging, undo, resource IDs,
  reusable scoring and unchanged-result handling.
- `./gradlew :app:app:assembleDefaultDebug --max-workers=4 --console=plain`: passed.
- `git diff --check`: passed. Independent code review found no remaining blockers.

No physical-device testing was available. In particular, horizontal gestures inside
third-party widgets may compete with stack paging; the explicit page arrows provide
an alternative. Existing translated resources fall back to the new English strings.
