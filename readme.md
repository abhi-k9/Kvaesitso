# Kvaesitso (abhi-k9 fork)

<img src="https://raw.githubusercontent.com/MM2-0/Kvaesitso/main/assets/icons/ic_launcher.png" width="128">

Kvaesitso is a search focused, free and open source launcher for Android. This is a fork of
[MM2-0/Kvaesitso](https://github.com/MM2-0/Kvaesitso) with folders, more app list options, MET
Norway weather, performance work and many bug fixes. It follows upstream and merges its changes.

[Upstream website and documentation](https://kvaesitso.mm20.de)

## Installation

Kvaesitso runs on Android 8.0 or higher.

Download the APK of the [latest release](https://github.com/abhi-k9/Kvaesitso/releases/latest)
and install it. Releases are built by GitHub Actions from this repository. Each release says what
changed; they aren't tested on a device before they're published.

The APK is signed with this fork's key, so it can't be installed over Kvaesitso from F-Droid,
IzzyOnDroid or the upstream releases. To switch:

1. Back up your settings in Settings > Backup
2. Uninstall the other Kvaesitso
3. Install this APK and restore the backup

Releases of this fork install over each other.

## What's different

### Folders

- Any tag can be shown as a folder in the app list: turn on "Show as folder" in the tag editor
- "Add to folder" in an app's long-press menu puts it in one or more folders, or a new one
- "Create folders from app categories" sorts apps into folders such as Games or Social
- Apps in a folder are only shown in the folder. Per app, "App list" in "Add to folder" (or "Also
  show in the app list" in the customize sheet) shows it in both
- Folders can be sorted in with the apps, or shown before or after them, optionally with a gap
  between folders and apps
- Folders open as a popup or a bottom sheet, can be pinned to the favorites, and folders whose
  name matches the search are shown in the search results
- Shortcuts (e.g. web apps) can be shown in the app list and put in folders like apps, and new
  shortcuts can go to the app list instead of the favorites
- All folder settings are in Settings > Search > Folders

### Search and app list

- Search learns from what you open: the result you opened after typing something comes first the
  next time you type it. Learned results can be reviewed and removed in Settings > Search > "Learn
  from what you open"
- Better matching: words in any order ("maps google"), typos in any word of a name ("fotos" for
  Google Photos), extra spaces from the keyboard are ignored, and custom labels, tags and folder
  names are matched like names
- Nearby key mistakes are recognized while typing: with QWERTY, both "swtt" and "swtti" can find
  Settings. Nearby key corrections score higher than distant substitutions; literal matches
  and your learned choices keep their priority
- Choose QWERTY, QWERTZ, AZERTY, Dvorak or Colemak in Settings > Search > "Keyboard layout for
  typos". The choice is saved and takes effect without restarting. Disabled turns off keyboard
  proximity corrections while keeping ordinary typo matching
- Better ranking: names that contain what you typed come before typo guesses, and for one or two
  letters, names starting with them come first. Contacts, events and files are ranked by how well
  they match, regardless of upper or lower case and accents
- Apps can be found by their package name, files by their full name or extension (e.g. ".pdf")
- Tags can be picked from a list in the customize sheet, and are sorted alphabetically
- Tag pickers support search and an Untagged filter without losing checked items. Long-press an
  app and choose "Tag multiple apps" to apply an existing or new tag to a selection
- "Don't suggest this app" removes an app from frequently used suggestions. Manage the list in
  Settings > Search > Favorites > Excluded suggestions; search, the app list and pins still work
- "Search on enter" starts the first search action, such as a web search
- Up to 8 rows of frequently used apps
- "New apps first" shows recently installed apps at the top of the app list
- "Fixed tags bar" keeps the favorites' tags at the bottom of the screen while the list scrolls
- Fonts can be imported (.ttf or .otf) and used in the typography settings
- "Open file location" for local files, and "Skip .nomedia folders" for file search
- Website search only looks up queries that look like web addresses

### Tasks (with the Tasks.org app)

- Tap a task's circle in the calendar widget or the search to complete it (Tasks 15.12 or newer)
- "Add task" quick action, to add the search text as a task
- Overdue tasks and tasks without a due date are found in the search, and can be shown in the
  calendar widget
- Notes are formatted (Markdown), and tasks no longer show up twice after moving them to another
  list

### Widgets and weather

- MET Norway weather, with free forecasts for the whole world. It's the default provider
- Spacer widget, and "Clear when playback stops" for the music widget
- Widgets can take a half, a third or a quarter of the width. Choose their width in edit mode;
  they appear side by side after editing
- Widget stacks share one adjustable-height frame. In edit mode, choose "Stack with next widget",
  then use the stack settings to add, reorder or separate widgets. Swipe or use the arrows to
  switch pages; built-in and app-provided widgets can be mixed
- Current weather on the home clock, and the date of the exchange rates in the unit converter
  settings (tap to update them)

### Plugins

Optional plugins are released with the launcher, as separate APKs on the releases page. Install
them, then turn them on in Settings > Plugins.

- **Open-Meteo**: free weather forecasts for the whole world, without an API key, from the best
  weather model for each place. Choose it in Settings > Integrations > Weather
- **Calendar feeds**: events from calendar subscriptions (.ics or webcal links), e.g. holidays,
  sports schedules or school calendars, in the search and the calendar widget. Add the feeds in
  the plugin's settings

### Fixes and performance

- Many crashes and bugs fixed, including ones reported on the
  [upstream issue tracker](https://github.com/MM2-0/Kvaesitso/issues). Each
  [release](https://github.com/abhi-k9/Kvaesitso/releases) lists them, with the upstream issue
  numbers
- Favorites, pinned and hidden items are no longer deleted while they're only temporarily
  unavailable, e.g. while the work profile is paused
- Safer backup restore and other hardening of input from other apps and files
- Less work after app launches, while typing and while the home page is covered, so the launcher
  stutters less and uses less battery
- Query scoring prepares each query once per batch, unchanged result groups retain their Compose
  state, and the widget picker resolves package labels once per provider snapshot

## Report issues

Report bugs in this fork's [issue tracker](https://github.com/abhi-k9/Kvaesitso/issues). The
"report" button on the crash report screen opens an issue there. Please include steps to
reproduce and, if you can, the crash report (Settings > Debug > Crash reporter) and debug
information (Settings > Debug > Export debug information).

Bugs that also happen in upstream Kvaesitso can be reported
[upstream](https://github.com/MM2-0/Kvaesitso/issues).

## Translations

New strings in this fork are in English only for now. To help translate Kvaesitso, see
[how to translate the project](https://kvaesitso.mm20.de/docs/contributor-guide/i18n).

## Links

- User guide: https://kvaesitso.mm20.de/docs/user-guide
- Upstream repository: https://github.com/MM2-0/Kvaesitso

## Thanks to

- [MM2-0](https://github.com/MM2-0) and the Kvaesitso contributors for Kvaesitso
- [@EliotAku](https://github.com/EliotAku) for the app icon
- All [translators and code contributors](https://github.com/MM2-0/Kvaesitso/graphs/contributors)

## License

This software is free software licensed under the GNU General Public License 3.0.

```

Copyright (C) 2021–2026 MM2-0 and the Kvaesitso contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```

The plugin SDK modules (`plugins/sdk` and `core/shared`) are licensed under the Apache License 2.0.
