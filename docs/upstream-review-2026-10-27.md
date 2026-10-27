# Upstream review, 27 October 2026

Compared fork `cabb95809` with upstream `MM2-0/Kvaesitso` at `63ce7484d`.
Reviewed all 26 issues opened from 27 September through 27 October, including
closed issues and the comments available on their GitHub pages. Reports describe
upstream releases; their presence does not establish a bug in this fork.

## Integrated changes

- [30eafd621](https://github.com/MM2-0/Kvaesitso/commit/30eafd621): URL handling
  ([PR #2051](https://github.com/MM2-0/Kvaesitso/pull/2051)). Prefix bare domains
  with `https://` before parsing. Setting a scheme after parsing produces an
  opaque URI without a host; domains with ports are also parsed incorrectly.
  Added classifier regression tests for domains, ports, paths, query strings,
  fragments, explicit schemes, whitespace and ordinary search text.
- [33a96ab1a](https://github.com/MM2-0/Kvaesitso/commit/33a96ab1a): identify
  Overpass requests with the app package/version and report HTTP failures before
  attempting JSON decoding. Relevant to places report #2046, but a successful
  live place search has not been verified. Preserved the fork's query escaping.
  Removed the newly introduced raw-query debug log because it contains search
  terms and location coordinates.

## Deferred changes

- `c095ffb33`, the file-metadata refactor, changes plugin SDK types, persistence
  and file rendering together. Both provider readers use
  `ZonedDateTime.from(Instant.ofEpochSecond(...))`; an `Instant` has no time zone,
  so a populated metadata date throws `DateTimeException`. The SDK writer also
  drops the zone by serializing epoch seconds. Resolve the date contract and add
  provider round-trip tests before integration. The local-file date parser also
  sends date-only formats through `LocalDateTime.parse`, so those formats cannot
  succeed.
- `cf15325d3`, `95aac1fc8`, `63ce7484d` only translate the refactor's new
  `file_meta_date` string. Defer them with that feature.

## Issue triage

Issue numbers link to `MM2-0/Kvaesitso`. “Already addressed” means a matching code
change exists in the fork; it does not mean the reporter's device was retested.

| Issue | Assessment |
| --- | --- |
| [#2050](https://github.com/MM2-0/Kvaesitso/issues/2050) Weather auto-scroll | Still relevant: `WeatherTimeSelector` remembers its scroll state without scrolling the selected/current forecast into view. Candidate for a separate focused UI change. |
| [#2049](https://github.com/MM2-0/Kvaesitso/issues/2049) App-drawer folders | Already available in the fork. Upstream closed as duplicate. |
| [#2047](https://github.com/MM2-0/Kvaesitso/issues/2047), [#2042](https://github.com/MM2-0/Kvaesitso/issues/2042) Shortcut permission crashes | Already guarded: both shortcut query paths catch `SecurityException`, including the background query named in the stack traces. |
| [#2046](https://github.com/MM2-0/Kvaesitso/issues/2046) Places return nothing | Integrated upstream's user-agent/error-handling change. Server health, response and actual device location still need live verification. |
| [#2041](https://github.com/MM2-0/Kvaesitso/issues/2041) Home gesture toggles apps | Fork already distinguishes returning home from triggering its home action (`655f08c55`). Similar symptom, but an intermittent device/navigation reproduction is still needed. |
| [#2039](https://github.com/MM2-0/Kvaesitso/issues/2039) Search focus stuck | Unresolved intermittent report; no reproduction steps. Existing focus fixes do not establish that this exact state is handled. Investigate with a device trace. |
| [#2038](https://github.com/MM2-0/Kvaesitso/issues/2038) Dock gesture dead zone | Feature request; leave for a separate gesture-design change. |
| [#2037](https://github.com/MM2-0/Kvaesitso/issues/2037) Currency conversion | Fork includes missing-rate recovery and manual rate refresh (`f693b2520`, `652dbf108`). |
| [#2036](https://github.com/MM2-0/Kvaesitso/issues/2036) Feet/inches | Already implemented (`0db8c85a3`). |
| [#2035](https://github.com/MM2-0/Kvaesitso/issues/2035) Galician | Language resources already present; the newly deferred translation is only for metadata dates. |
| [#2034](https://github.com/MM2-0/Kvaesitso/issues/2034) Secure workspace specification | Closed upstream as not planned; unrelated to this integration. |
| [#2032](https://github.com/MM2-0/Kvaesitso/issues/2032) Wallpaper icon colours | Fork already refreshes system-colour icons on wallpaper changes (`80d2e156d`). |
| [#2031](https://github.com/MM2-0/Kvaesitso/issues/2031) Seven visible tags | Needs layout reproduction with more than seven tags and the reported screen settings; do not infer data loss from the report. |
| [#2030](https://github.com/MM2-0/Kvaesitso/issues/2030) Unit example wording | Two independent examples, as explained in the comments; no conversion bug established. |
| [#2029](https://github.com/MM2-0/Kvaesitso/issues/2029) Batch tags | Existing settings provide bulk management; upstream comment points to Search > Tags and Excluded items. Drawer multiselect is a separate UX request. |
| [#2028](https://github.com/MM2-0/Kvaesitso/issues/2028) Clock weather | Already implemented (`e2427cfa6`). |
| [#2027](https://github.com/MM2-0/Kvaesitso/issues/2027) Action keywords | Closed as duplicate upstream; feature request, not a regression. |
| [#2026](https://github.com/MM2-0/Kvaesitso/issues/2026) Gallery refresh | Insufficient information: provider/app not identified, as the upstream comment notes. |
| [#2025](https://github.com/MM2-0/Kvaesitso/issues/2025) Rectangle shape crash | Matching workaround already present: zero-radius themes use `CutCornerShape` to avoid animated negative corners. |
| [#2024](https://github.com/MM2-0/Kvaesitso/issues/2024) Widget links crash | Matching fix already present: widget context adds `FLAG_ACTIVITY_NEW_TASK`. |
| [#2023](https://github.com/MM2-0/Kvaesitso/issues/2023) AI contribution policy | Closed discussion; upstream prefers understood, reviewable changes grouped by issue. No upstream submission made here. |
| [#2022](https://github.com/MM2-0/Kvaesitso/issues/2022) Home-screen tags | Fork has a fixed tags bar; the requested clock-screen placement and list presentation need a separate design decision. |
| [#2020](https://github.com/MM2-0/Kvaesitso/issues/2020) Nextcloud keystore crash | Matching recovery already present: unreadable encrypted preferences are deleted through Android's API and retried once. Upstream issue is closed. |
| [#2018](https://github.com/MM2-0/Kvaesitso/issues/2018) Bookmark from search | Feature request; no new upstream implementation to integrate. |
| [#2017](https://github.com/MM2-0/Kvaesitso/issues/2017) Ephemeral custom tabs | Optional browser feature request; defer to a separate settings/compatibility change. |

Next investigation priorities are weather auto-scroll (#2050), stuck search
focus (#2039), and tag overflow (#2031). Avoid speculative fixes for the latter
two without reproductions.

## Validation

- `bash .github/scripts/unit-tests.sh --max-workers=4 --console=plain`: passed;
  229 tests reported, 228 passed, one skipped, no failures or errors. This includes
  the four new URL regression tests.
- `./gradlew :app:app:assembleDefaultDebug --max-workers=4 --console=plain`:
  passed. No signing keys or release publication required.
- A standalone Java 21 check reproduced both deferred metadata date errors.
- No live device or Overpass service validation was performed. The remaining
  intermittent UI reports are triaged, not claimed fixed.
