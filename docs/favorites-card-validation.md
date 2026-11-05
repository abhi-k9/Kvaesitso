# Configurable app-drawer favorites card

Implemented against `499ea8a52`.

The card has separate icon size, column count, label and group-heading controls.
It inherits the main grid until customized, and its layout can be reset without
changing group limits. The main app list, dock and favorites widgets retain their
own layout. Card item models are isolated from the main list so different icon
sizes cannot overwrite each other's rendering state; the card cache is released
when it leaves the composition.

Pinned, recent and frequent groups have independent limits. Zero disables a group;
pins also support All, and frequency supports the existing row limit. Membership
is deduplicated in pinned, recent, frequent order. Pinned folders participate in
the pinned limit, while tag chips and selected-tag contents keep their existing
behavior. Automatic groups contain unpinned apps; hidden, excluded, locked-private
and temporarily unavailable apps do not consume their visible slots.

Recent ordering uses persisted last-launch times. Frequency uses total launch
counts, with existing weight as a tie-breaker. The launch time is captured before
the asynchronous write and older writes cannot move it backwards. Pin/visibility
edits, batched saves, key merges and backups preserve it. Failed Enter-key launches
are no longer counted as usage.

Room migration 35 to 36 adds `lastLaunchedAt` with a zero default. Existing counts,
weights, pins and exclusions remain intact. Zero means unknown recency, so old
usage counts contribute to frequency without inventing recent history. New recent
history fills from launches made through the launcher. Settings and timestamps
are backed up; old backups default to unknown recency.

Validation:

- All-module unit suite passed: **357 tests, 356 passed, one pre-existing skip**.
- Added 25 tests, including real SQLite upgrades from both versions 34 and 35,
  recency/frequency ordering, exclusions, timestamps, backup compatibility, quotas,
  deduplication, persistent settings, card-only density, cache disposal and slider
  updates/disabled behavior.
- `:app:app:assembleDefaultDebug` passed.
- `git diff --check` passed.

No physical-device testing was available.

## Correction after fork.41

Recent and frequent groups incorrectly queried the synthetic type `apps`, while
real saved applications use the domain `app`. The original tests also used the
synthetic type and therefore missed the empty groups on real installations.

The query, app model and serializers now share `Application.Domain`, whose stable
on-disk value remains `app`. Fixtures use that existing record format. Additional
tests check the concrete app/serializer contract and record a launch through the
repository before querying both automatic groups. Corrected fixtures reproduced
the failure against the previous query.

No migration or history reset is necessary: existing counts, timestamps, pins and
exclusions are preserved. Validation passed with 359 tests reported (358 passed,
one skipped), a successful debug APK build and a clean `git diff --check`.

## Compact horizontal strip after fork.43

The app-drawer Favorites card now defaults to a single horizontally scrollable
row. Pins, recents and frequent apps retain their order and quotas, but no longer
occupy three separate vertical sections. Small headings mark the first item of
each group and scroll with it; headings can still be hidden. Selected tags show
all their members without group headings or quotas.

The strip uses stable item keys and lazy composition. A small peek at the next
item signals overflow. It preserves the requested icon size, a minimum 48dp hit
area, and the existing app/folder renderers and actions. Unlabelled icons keep
their compact height even when fewer items are requested in view. Horizontal
overscroll is consumed at the strip, while vertical swipes still scroll the app
drawer. Switching tags starts the new tag at its first item.

**Settings > Search > Favorites > Scroll favorites sideways** switches back to the
stacked grid without losing density or quota settings. The density control is
labelled **Items in view** in strip mode and retains the existing column setting
in grid mode. Both new installations and older settings/backups default to the
strip; an explicit grid choice survives backup/restore. Matching the app grid's
density leaves scrolling mode and quotas unchanged. Dock and widget layouts are
unaffected, and no database migration is needed.

Validation:

- All-module unit suite: 367 tests reported, 366 passed, one skipped, zero
  failures/errors.
- Eight new Compose regressions cover one-row grouping and order, access to the
  last item, tag changes, hidden headings, RTL, large icons/type on narrow
  screens, horizontal overscroll isolation, and vertical drawer scrolling.
- Preference tests also cover old settings defaults, switching modes, backup
  persistence, and preserving layout mode when resetting density.
- `:app:app:assembleDefaultDebug` and `git diff --check`: passed.

Physical-device thumb reach and feel have not been tested in this environment.

## Group heading width correction after fork.44

Strip headings were constrained to the first icon's cell, so a caption such as
Frequent could be ellipsized despite unused space above the rest of its group.
Headings now measure across the group's cells, and the final group can also use
empty trailing viewport space. Their maximum width stops at the next group or
the viewport width. Icon spacing, scroll keys, group limits and row height are
unchanged.

Three regressions use native text measurement at 150% font scale, covering LTR,
RTL and a single-icon final group. Both two-icon cases reproduced truncation
before the fix. All 16 focused Favorites card tests pass; unrelated suites were
not repeated. The debug APK build and `git diff --check` also pass.
