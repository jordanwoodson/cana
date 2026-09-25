#!/usr/bin/env python3
"""Export Cana's vector mark to Android layers, launcher PNGs and web icons.

Requires cairosvg (pip install cairosvg). Run from any working directory.
Edit assets/branding/cana-mark.svg, then run this script to update all exports.
"""

from pathlib import Path
import re
import xml.etree.ElementTree as ET

import cairosvg

ROOT = Path(__file__).resolve().parents[1]
BRAND = ROOT / "assets/branding"
RES = ROOT / "app/src/main/res"
WEB = ROOT / "docs/public/branding"
SVG = "{http://www.w3.org/2000/svg}"
BACKGROUND = "#152421"


def save(path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content + "\n")


def render(svg, path, size):
    path.parent.mkdir(parents=True, exist_ok=True)
    cairosvg.svg2png(bytestring=svg.encode(), write_to=str(path),
                    output_width=size, output_height=size)


def main():
    source = (BRAND / "cana-mark.svg").read_text()
    group = ET.fromstring(source).find(f"{SVG}g")
    tx, ty, scale = re.fullmatch(
        r"translate\(([\d.]+) ([\d.]+)\) scale\(([\d.]+)\)",
        group.attrib["transform"],
    ).groups()
    paths = [path.attrib["d"] for path in group.findall(f"{SVG}path")]
    mint = group.attrib["fill"]
    art = re.search(r"  <g[\s\S]*</g>", source).group()

    # Android clips the full 108 dp layers to a 72 dp viewport. The mark fits
    # inside the central safe circle, including the outward arrow's tip.
    for name, color in (("foreground", mint), ("monochrome", "#FFFFFF")):
        android_paths = "\n".join(
            f'        <path android:fillColor="{color}" android:pathData="{p}" />'
            for p in paths
        )
        save(RES / f"drawable/ic_launcher_{name}.xml", f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Generated from assets/branding/cana-mark.svg. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <group android:translateX="{tx}" android:translateY="{ty}"
        android:scaleX="{scale}" android:scaleY="{scale}">
{android_paths}
    </group>
</vector>''')

    def icon(shape):
        return f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72">
  <title>Cana — Open C</title>
  {shape}
{art}
</svg>'''

    square = icon(f'<rect x="18" y="18" width="72" height="72" fill="{BACKGROUND}"/>')
    rounded = icon(f'<rect x="18" y="18" width="72" height="72" rx="17" fill="{BACKGROUND}"/>')
    circle = icon(f'<circle cx="54" cy="54" r="36" fill="{BACKGROUND}"/>')
    background = f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108"><path fill="{BACKGROUND}" d="M0 0H108V108H0Z"/></svg>'
    mono = source.replace(mint, "#FFFFFF")

    save(BRAND / "cana-icon.svg", rounded)
    save(BRAND / "cana-icon-round.svg", circle)
    save(WEB / "cana-icon.svg", rounded)
    save(WEB / "cana-wordmark.svg", (BRAND / "cana-wordmark.svg").read_text().strip())
    for density, launcher_size, layer_size in (
        ("mdpi", 48, 108), ("hdpi", 72, 162), ("xhdpi", 96, 216),
        ("xxhdpi", 144, 324), ("xxxhdpi", 192, 432),
    ):
        folder = RES / f"mipmap-{density}"
        render(rounded, folder / "ic_launcher.png", launcher_size)
        render(source, folder / "ic_launcher_foreground.png", layer_size)
        render(background, folder / "ic_launcher_background.png", layer_size)
        render(mono, folder / "ic_launcher_monochrome.png", layer_size)

    render(rounded, ROOT / "metadata/en-US/images/icon.png", 512)
    # Store applies its own mask; supply a full-bleed square here.
    render(square, RES / "play_store_512.png", 512)
    render(rounded, WEB / "cana-icon.png", 512)
    print("Exported Cana branding: Android vectors, 20 launcher PNGs, store and web icons.")


if __name__ == "__main__":
    main()
