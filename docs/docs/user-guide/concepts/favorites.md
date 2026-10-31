# Favorites

Favorites allow even faster access to selected items.

Favorites consist of:

- Manually sorted pinned items
- Automatically sorted pinned items (sorted by how frequently you use them)
- Frequently used items (if there is room)

## Customize the app drawer card

Open **Settings > Search > Favorites**. The **App drawer card layout** controls
icon size, column count, item labels and group headings independently of the main
app grid, dock and favorites widgets. For a denser card, try smaller icons and
more columns, or hide labels and headings. Icons shrink to fit narrow columns.
**Match the app grid layout** resets the card's icon size, columns and item labels.

Under **App drawer card contents**, set separate limits for **Pinned**,
**Recently used** and **Frequently used**. Zero hides a group; **All** shows all
pins. **Row limit** uses the existing frequently-used rows setting and the card's
column count. Pins beyond the chosen limit stay pinned elsewhere.

Items appear once, in that order. Recent and frequent suggestions contain unpinned
apps, so an app already shown in Recent does not repeat in Frequent. Recent sorts
by the last recorded launch, while Frequent sorts by total recorded launches.
Pinned folders keep their place among pinned items; other pinned tags stay in the
tag selector. Selecting a tag shows all of its items without applying group limits.

Recent history records apps opened from this launcher, without a new permission.
It starts filling as apps are opened after this update; existing frequency counts
are preserved. Counts, density and recorded recency are saved in backups.

## Control suggested apps

Long-press an app and choose **Don't suggest this app** to keep it out of recent and
frequently used suggestions. It stays available in search and the app list, and you can still
pin it. The preference continues to apply after future launches.

Restore an app with **Allow in suggestions**, or manage the list at
**Settings > Search > Favorites > Excluded suggestions**. Pinned items remain visible
even when they are excluded from suggestions.

## Pin apps to favorites

You can pin any app and any search result to favorites by long pressing it and selecting the <span class="material-symbols-rounded">star_border</span> icon. Remove items by pressing the same icon again.

Some apps allow the creation of home screen shortcuts. These shortcuts will also appear in the favorites section.

## Access favorites

Favorites can appear in three different places:

- Above (or below) the app grid (enabled by default, can be disabled at Settings > Search > Favorites)
- In the [favorites widget](/docs/user-guide/widgets/favorites-widget) (not enabled by default)
- In the [clock widget](/docs/user-guide/widgets/clock#dynamic-components) (not enabled by default)

> [!INFO]
> If you have the calendar widget enabled, pinned calendar events will not appear in any of the above places. Instead, they will appear in the calendar widget.

## Customization

Favorites can be customized at Settings > Search > Favorites > Edit favorites. The same panel can be opened by clicking the pen icon in the favorites panel.

In this panel, you can

- rearrange items / change their order (note that reordering items in the “automatically sorted” section will have no effect, you need to move them to the “manually sorted” section first)
- add app shortcuts
- manage [tags](tags)
- configure frequently used items
  - how many rows of frequently used items should be shown (including the last row of pinned items which will be filled first)
  - disable non-pinned frequently used altogether
