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
