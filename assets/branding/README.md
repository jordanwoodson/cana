# Cana branding

**Do more. Because you can.**

The Open C mark pairs an open letter C with an outward arrow: more room to do
more. The wordmark is **(can)a**, with **can** emphasized. Use **Cana** for app
labels, prose, search, and accessibility names.

- Mint: `#70EFAD` on charcoal `#152421`.
- On light backgrounds, use forest `#006C45` for readable brand text.
- Keep the mark flat and preserve its open space. Do not put the wordmark inside
  the launcher icon.
- Android keeps wallpaper-based dynamic colors. Its fallback palette uses the
  Cana greens, with red reserved for errors and destructive actions.
- Credit the original project as **Canta by samolego**.

`cana-mark.svg` is the source for Android foreground and monochrome vectors,
legacy launcher sizes, metadata, and documentation icons. Its 108-unit canvas
includes the padding required for adaptive icons; don't trim the source.
`cana-icon.svg` and `cana-icon-round.svg` show the normal masked appearance.

Regenerate exports from the repository root:

```sh
python3 -m pip install cairosvg
python3 scripts/generate_branding.py
```

The concept was explored with imagegen, then rebuilt as flat vector geometry.
Android sizing follows the [adaptive icon guidelines](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive).
