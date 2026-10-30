# Upstream review, 30 October 2026

Compared fork `bd851229d` with `MM2-0/Kvaesitso` main at
`1aaa05813bd04276e1807582932993fc33e31759`. Upstream has no new commits since
the last check on 29 October. Checked all 26 issues and seven pull requests opened from
30 September through 30 October, including closed reports and their comments.
Also checked the 15 older issue/PR records updated during that window. Source:
GitHub's public issues, comments and pull-request APIs, retrieved on 30 October.

## Upstream commits and pull requests

The useful URL and Overpass fixes are already integrated (`f2e25bcf3`,
`6fec13c43`), with URL regression tests and removal of location/query logging.
No additional upstream commit was selected in this review.

Continue deferring `c095ffb33` and its Italian, Czech, Galician and Hungarian
translations (`cf15325d3`, `95aac1fc8`, `63ce7484d`, `1aaa05813`). The metadata
date contract still converts an `Instant` to `ZonedDateTime` without a zone and
parses date-only strings as `LocalDateTime`. These previously reproduced errors
have not been corrected upstream. See the [previous review](upstream-review-2026-10-27.md).

| Pull request | Decision |
| --- | --- |
| [#2051](https://github.com/MM2-0/Kvaesitso/pull/2051), URL handling | Already integrated and tested. |
| [#2048](https://github.com/MM2-0/Kvaesitso/pull/2048), compact timers/alarms | Defer: the description says `5` creates five minutes, but the implementation creates five seconds. Its early classifier return also drops the existing phone-number classification for numeric input. Needs defined parsing precedence and regression tests before integration. |
| [#2045](https://github.com/MM2-0/Kvaesitso/pull/2045), hide frequently used apps | Useful optional feature, but requires a database migration, backup changes and new settings. Defer to a separate feature change with migration/restore tests. |
| [#2044](https://github.com/MM2-0/Kvaesitso/pull/2044), wallpaper blur fallback | Defer: broad wallpaper rendering and lifecycle change, requires all-files access on Android 13+, and uses a static thumbnail for live wallpapers. Needs device and permission-flow validation. The fork already clears the blur flag when disabled. |
| [#2043](https://github.com/MM2-0/Kvaesitso/pull/2043), shortcut permissions | Matching guards already exist in both shortcut query paths. |
| [#2040](https://github.com/MM2-0/Kvaesitso/pull/2040), alternate launcher design | Closed upstream; broad redesign outside this maintenance pass. |
| [#2033](https://github.com/MM2-0/Kvaesitso/pull/2033), multiple tasks for custom intents | Defer: adding `FLAG_ACTIVITY_MULTIPLE_TASK` unconditionally changes every custom action's task behavior. Better treated as an explicit intent option. |

## Changes made in the fork

- [#2052](https://github.com/MM2-0/Kvaesitso/issues/2052): company-only contacts
  were found by Android but discarded when constructing a result without a
  structured personal name. Fall back to a nonblank organization name, search
  organization rows explicitly, preserve personal names and deduplicate matches.
  Eight SQLite-backed Robolectric tests exercise real provider selections,
  saved-contact loading, blank names, irrelevant results and bound query arguments.
  Company matches load all linked raw contacts so personal names, phone/email
  details and custom actions are retained.
- [#2050](https://github.com/MM2-0/Kvaesitso/issues/2050): position the hourly
  forecast at the selected/current hour initially, when selection changes, and
  when the launcher resumes. Preserve manual scrolling during temperature and
  format updates, including through the parent animation. Compose tests cover
  these behaviors and day changes.
- [#2056](https://github.com/MM2-0/Kvaesitso/issues/2056): the reported duplicate
  app key on work-profile unlock matches fixes already present (`934cedb67`,
  `57e8e7c8b`). Package notifications and profile reloads replace existing entries
  under one mutex. Add three regression tests for both notification orders and
  repeated package-added notifications, preserving the personal-profile copy.
  No additional production workaround is needed for this report.

## Remaining recent issues

“Already addressed” identifies matching fork code; it does not claim that a
reporter's device has been retested.

| Issue | Assessment |
| --- | --- |
| [#2055](https://github.com/MM2-0/Kvaesitso/issues/2055), broadcast custom intents | Separate automation feature; no existing upstream implementation to merge. |
| [#2054](https://github.com/MM2-0/Kvaesitso/issues/2054), ColorOS 17 favorites delay | Needs a device/profile trace and reproducible sequence. Existing fork changes reduce repeated Android calls while restoring favorites; no evidence establishes that they resolve this report. |
| [#2053](https://github.com/MM2-0/Kvaesitso/issues/2053), app-like actions/web shortcuts | Separate presentation feature; defer. |
| [#2049](https://github.com/MM2-0/Kvaesitso/issues/2049), drawer folders | Already available in the fork; closed as duplicate upstream. |
| [#2047](https://github.com/MM2-0/Kvaesitso/issues/2047), [#2042](https://github.com/MM2-0/Kvaesitso/issues/2042), shortcut permission crashes | Both query paths already catch `SecurityException`. |
| [#2046](https://github.com/MM2-0/Kvaesitso/issues/2046), places return nothing | User agent and HTTP failure handling already integrated; live server/location verification remains outstanding. |
| [#2041](https://github.com/MM2-0/Kvaesitso/issues/2041), home gesture | Fork distinguishes returning home from the home action; intermittent device/navigation behavior still needs reproduction. |
| [#2039](https://github.com/MM2-0/Kvaesitso/issues/2039), stuck search focus | No new reproduction or comments. Keep open for a device trace; do not infer resolution from other search fixes. |
| [#2038](https://github.com/MM2-0/Kvaesitso/issues/2038), gesture dead zone | Separate gesture-design request. |
| [#2037](https://github.com/MM2-0/Kvaesitso/issues/2037), currencies | Missing-rate recovery and manual refresh already implemented. |
| [#2036](https://github.com/MM2-0/Kvaesitso/issues/2036), feet/inches | Already implemented and tested. |
| [#2035](https://github.com/MM2-0/Kvaesitso/issues/2035), Galician | Language already present; defer the metadata-only translation with its feature. |
| [#2034](https://github.com/MM2-0/Kvaesitso/issues/2034), secure workspace | Closed as not planned upstream; outside launcher maintenance. |
| [#2032](https://github.com/MM2-0/Kvaesitso/issues/2032), wallpaper icon colors | Fork already refreshes system-color icons on wallpaper changes. |
| [#2031](https://github.com/MM2-0/Kvaesitso/issues/2031), seven visible tags | No new evidence; needs reproduction with the reported display settings and more than seven tags. |
| [#2030](https://github.com/MM2-0/Kvaesitso/issues/2030), unit examples | Comments explain two independent examples; no conversion bug established. |
| [#2029](https://github.com/MM2-0/Kvaesitso/issues/2029), batch tags | Existing settings support bulk management; drawer multiselect is a separate feature. |
| [#2028](https://github.com/MM2-0/Kvaesitso/issues/2028), clock weather | Already implemented. |
| [#2027](https://github.com/MM2-0/Kvaesitso/issues/2027), action keywords | Closed as duplicate upstream; separate feature request. |
| [#2026](https://github.com/MM2-0/Kvaesitso/issues/2026), gallery refresh | Provider/app remains unidentified; insufficient reproduction detail. |
| [#2025](https://github.com/MM2-0/Kvaesitso/issues/2025), rectangular shape crash | Matching zero-radius shape workaround already present. |
| [#2024](https://github.com/MM2-0/Kvaesitso/issues/2024), widget links crash | Matching widget-context `FLAG_ACTIVITY_NEW_TASK` fix already present. |

## Older reports with recent activity

- [#1971](https://github.com/MM2-0/Kvaesitso/issues/1971) and
  [#1955](https://github.com/MM2-0/Kvaesitso/issues/1955): latest icon-placeholder
  comments tie failures to app updates. Matching cache invalidation and
  replacement handling exist (`ed92fff57`, `724448053`), with icon-service tests.
- [#1116](https://github.com/MM2-0/Kvaesitso/issues/1116): archive/restore remains
  a distinct Android 15+ compatibility investigation. Standard package event
  invalidation does not establish correct archived-state/unarchive behavior.
- [#1504](https://github.com/MM2-0/Kvaesitso/issues/1504): missing widget editor
  menu action remains a small candidate; the recent comment describes a third
  party APK patch rather than an upstream source commit.
- [#945](https://github.com/MM2-0/Kvaesitso/issues/945), untagged filtering, and
  [#1533](https://github.com/MM2-0/Kvaesitso/issues/1533), calendar action titles:
  useful separate features; neither has a new implementation to integrate.
- [#1589](https://github.com/MM2-0/Kvaesitso/issues/1589): the fork already offers
  widget widths and side-by-side placement; free placement is a separate design.
- [#1864](https://github.com/MM2-0/Kvaesitso/issues/1864): dock/favorites
  duplication was intentional upstream; comments ask about skip-row settings.
- [#1451](https://github.com/MM2-0/Kvaesitso/issues/1451): latest Nextcloud
  commenter resolved missing results by enabling online search; no new defect
  or reason to change TLS behavior.
- [#1603](https://github.com/MM2-0/Kvaesitso/issues/1603): wallpaper picker depends
  on installed/system handlers; needs a reproduction before changing resolution.
- [#778](https://github.com/MM2-0/Kvaesitso/issues/778) is closed without new
  diagnostic comments; no new fix identified.
- [#2001](https://github.com/MM2-0/Kvaesitso/issues/2001), impersonating website,
  and [#2023](https://github.com/MM2-0/Kvaesitso/issues/2023), contribution policy:
  discussions rather than fork code defects.
- [#1509](https://github.com/MM2-0/Kvaesitso/pull/1509), legacy Crowdin PR,
  remains unmerged; current translation updates use Weblate. Closed
  [#2015](https://github.com/MM2-0/Kvaesitso/pull/2015) was already integrated
  upstream and is the basis of the fork's subsequent search improvements.

## Validation

- `bash .github/scripts/unit-tests.sh --max-workers=4 --console=plain`: passed.
  273 tests reported: 272 passed, one skipped, zero failures/errors. Includes
  eight contact-provider tests, five weather Compose tests and three profile
  notification tests added in this review.
- `./gradlew :app:app:assembleDefaultDebug --max-workers=4 --console=plain`: passed.
- `git diff --check`: passed; independent review found no remaining blockers.

No physical-device testing has been performed; the ColorOS delay,
archive/restore and intermittent focus reports remain investigations.
