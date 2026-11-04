# Upstream review, 4 November 2026

Compared fork `d28fde134` (fork.42) with `MM2-0/Kvaesitso` main at
`831a591cfccd0363ac6fe29f3b5ff997e849acad`. Reviewed all four commits since the
previous upstream review at `1aaa05813`. All four are translations; upstream has
no new functional fixes in this interval.

## Integrated changes

- `7fe134801`: Belarusian wording improvements for URL templates, cloud account
  settings and measurement systems; translations for the repair action, local
  network permission, skipped favorite rows and scientific metric units. Omitted
  only `file_meta_date`, whose feature and default resource remain deferred.
- `15870a51d`: Belarusian feminine URL-template wording and account translations.
- `e99fa816b`: Belarusian masculine URL-template wording.

These are selective integrations so the fork retains its search, favorites,
widget stacks and other custom behavior. Translation credit is preserved in the
commits.

## Deferred changes

- `831a591cf`: only adds the Hebrew translation of `file_meta_date`; defer with
  its feature.
- The older file-metadata refactor `c095ffb33` and its associated translations
  remain deferred for the reasons in the
  [27 October review](upstream-review-2026-10-27.md). None of the new commits fixes
  the previously identified metadata date parsing/serialization problems.

## Validation

- Parsed the three changed XML resource files, checked for duplicate keys and
  default resources, and verified format arguments and search-template
  placeholders for all 20 changed translations: passed.
- `./gradlew :app:app:assembleDefaultDebug --max-workers=4 --console=plain`: passed.
- `git diff --check`: passed.

This review covers commits on upstream main; it is not a new issue/PR triage or
an APK release.
