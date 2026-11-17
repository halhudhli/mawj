---
version: alpha
name: Mawj Sea
description: Mawj's visual identity. A night sea with the moon's gold; calm, premium, cinematic. Read this before changing any screen of the app or making anything for Mawj (site, video, store art).
---

## Overview

Mawj («موج», "wave") is a films-and-series app for Android tablets first. Its world is the sea at night: deep blue water, a gold moon on its surface, waves moving slowly. Every screen sits on that sea, artwork melts into it, and gold is used sparingly for what matters.

- The sea is the app background, behind every page, five wave layers drifting slowly and fading deeper toward the bottom. It is never drawn inside a hero or a poster.
- Screens are RTL Arabic. Titles serif, body sans.
- In-app text is formal Arabic in the impersonal voice. The app's name is «موج», never «الموج».
- The player keeps its own look; this document does not restyle it.
- Every visual feature has an on/off switch (flag `mawjStage`, «واجهة موج الجديدة»).

## Colors

```yaml
colors:
  sea: "#070D14"          # page background, the deep water
  sea-raised: "#0D1620"   # cards, lists, sheets
  sea-card: "#121C26"     # nested surfaces
  sea-crest: "#2E5470"    # lightest wave crest
  gold: "#D6B36A"         # accent: primary buttons, selection, progress, focus
  gold-deep: "#A9843F"    # pressed gold, gradient start
  gold-light: "#F1DDA6"   # gold text on dark, highlights
  ink: "#EEF2F6"          # main text
  ink-strong: "#F6F1E4"   # titles (warm white)
  muted: "#A9B6C4"        # secondary text
  faint: "#8796A8"        # captions, metadata
  on-gold: "#070D14"      # text on gold
  success: "#6FC3A5"
  warning: "#E0A050"
  danger: "#E0786A"
```

- Gold is an accent, not a fill: one gold action per area, gold progress bars, gold rings on avatars and cast photos, the gold line on wave crests.
- Text on gold is always `on-gold`, never white.
- No purple, no neon. Old hard-coded blues and purples follow the theme accent.

## Typography

```yaml
typography:
  display:
    fontFamily: Serif (Amiri on the web)
    fontSize: 60px
    fontWeight: 700
    lineHeight: 1.1
  title-lg:
    fontFamily: Serif (Amiri on the web)
    fontSize: 28px
    fontWeight: 700
    lineHeight: 1.2
  title-md:
    fontFamily: Serif (Amiri on the web)
    fontSize: 22px
    fontWeight: 700
    lineHeight: 1.25
  body:
    fontFamily: System sans (IBM Plex Sans Arabic on the web)
    fontSize: 15px
    fontWeight: 400
    lineHeight: 1.8
  label:
    fontFamily: System sans (IBM Plex Sans Arabic on the web)
    fontSize: 13px
    fontWeight: 500
    lineHeight: 1.5
  caption:
    fontFamily: System sans (IBM Plex Sans Arabic on the web)
    fontSize: 12px
    fontWeight: 400
    lineHeight: 1.5
```

- Page, section and row titles are serif. Names of people, works and collections in heroes are serif.
- Eyebrows (small gold lines above titles, such as «مدّ الليلة ── دراما · غموض») use `label` in gold.

## Spacing

```yaml
spacing:
  xs: 6px
  sm: 10px
  md: 16px
  lg: 22px
  xl: 30px
  xxl: 48px
  poster-gap: 22px
  row-gap: 30px
  page-gutter: 48px
```

- Posters in a row are 22 apart; rows are 30 apart. Nothing is packed edge to edge.

## Rounded

```yaml
rounded:
  sm: 10px
  md: 14px
  lg: 16px
  xl: 20px
  sheet: 28px
  pill: 999px
```

## Components

```yaml
components:
  poster:
    width: 200px            # user-selectable: 150 / 170 / 200 / 236
    aspect: "2 / 3"
    radius: "{rounded.lg}"
    rating-badge: "{colors.gold}"
    title-font: "{typography.title-md}"
    rest-state: still       # posters never move at rest
  continue-card:
    aspect: "16 / 9"
    width: 360px
    progress: "{colors.gold}"
  chip:
    height: 40px
    radius: "{rounded.pill}"
    selected-fill: "{colors.gold}"
    selected-text: "{colors.on-gold}"
    idle-border: "rgba(238,242,246,0.18)"
  primary-button:
    height: 50px
    radius: "{rounded.pill}"
    fill: "{colors.gold}"
    text: "{colors.on-gold}"
  card:
    fill: "{colors.sea-raised}"
    border: "rgba(214,179,106,0.12)"
    radius: "{rounded.xl}"
  toast:
    fill: "#13202D"
    border: "rgba(214,179,106,0.45)"
    dot: "{colors.gold}"
  loading:
    style: shimmer in the exact shape of the content
    colors: ["#0F1A25", "#1D2C3B"]
```

## Home

The approved home, top to bottom: hero («مدّ الليلة» line, serif title, gold play button) → continue watching → mood compass («بوصلة المزاج») → «ماذا يُشاهَد الليلة؟» card in gold → catalog rows (200 posters, 22 apart, no rank numbers) → «الجزر» (all collections in one section with tabs) → next-episode card.

## Motion

Calm and premium, no bounce. Signature curve `cubic-bezier(0.4, 0, 0.2, 1)`; quick 150 ms, standard 300 ms, slow 500 ms; the sea drifts over 14–24 s. Posters do not move at rest. Respect reduced motion. Details in the `mawj-motion` skill.

## Icons

Gentleface (MIT): line-round icons; the rail and settings use its animated set (files/motion), which rests still and moves once when a tab is chosen or a settings group appears. Switch: «أيقونات متحركة». Player icons stay as they are.

## Sound

Short sea cues on page open (a small wave), back (water drawing back), add (gold sparkle) and error, each behind a switch. No opening sound for now.

## Do and don't

- Do let the sea show behind every page; no opaque full-screen washes.
- Do keep one gold action per area.
- Do write Arabic in the impersonal voice: «تمت مشاهدة ١٢ من ٩٦», not «شاهدتِ».
- Don't pack posters together or animate them.
- Don't use purple, neon, or glass-over-photo backgrounds under the new look.
- Don't add English labels to the app.
