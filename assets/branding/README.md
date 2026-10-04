# Cana branding

**Do more. Because you can.**

The Open C mark pairs an open letter C with an outward arrow: more room to do
more. The wordmark is **(can)a**, with **can** emphasized. Use **Cana** for app
labels, prose, search, and accessibility names.

- The Dracula palette uses purple `#BD93F9` for the C and pink `#FF79C6`
  for its outward arrow, on background `#282A36`.
- The terminal-style monospace wordmark uses purple **can**, pink parentheses,
  and foreground `#F8F8F2` for **a**. Place the wordmark on a dark surface.
- Surfaces use selection `#44475A`; secondary accents use cyan `#8BE9FD`,
  green `#50FA7B`, orange `#FFB86C`, and yellow `#F1FA8C`.
- Comment `#6272A4` is a decorative accent, not small body text on the dark
  background. Use foreground `#F8F8F2` for readable text.
- Keep the mark flat and preserve its open space. Do not put the wordmark inside
  the launcher icon.
- The default Android palette and documentation share the Dracula colors.
  Red `#FF5555` is reserved for errors and destructive actions.
- Android uses a lighter red tone `#FF9CA3` for small error text and destructive
  controls on raised surfaces. Unsafe badges retain the original red fill.
- The Android interface uses stepped dark surfaces (`#2D2F3D`, `#323443`,
  `#383A4B`) and secondary text `#D3D1DF` to separate content without adding
  more accent colors. Cards use 20–24 dp corners; dialogs use 28 dp corners.
- Credit the original project as **Canta by samolego**.

`cana-mark.svg` is the source for the two-color Android foreground and solid
monochrome vectors,
legacy launcher sizes, metadata, and documentation icons. Its 108-unit canvas
includes the padding required for adaptive icons; don't trim the source.
`cana-icon.svg` and `cana-icon-round.svg` show the normal masked appearance.
The Android adaptive icon's background is also defined in
`app/src/main/res/values/colors.xml`; keep it at `#282A36` to match the exports.

Regenerate exports from the repository root:

```sh
python3 -m pip install cairosvg
python3 scripts/generate_branding.py
```

The concept was explored with imagegen, then rebuilt as flat vector geometry.
Android sizing follows the [adaptive icon guidelines](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive).
