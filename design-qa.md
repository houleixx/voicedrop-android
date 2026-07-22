# Recording Detail Long-press Menu — Design QA

## Evidence

- Source visual truth: `/var/folders/h2/_6smmfkj2z1f7pbmqc0f36gm0000gn/T/codex-clipboard-XPC209.png`
- Source dimensions: 945 × 2048 px
- Implementation: Android native `RecordingDetailActivity`
- Target state: long-pressing article text or an article image

## Implemented fidelity surfaces

- Typography: single-line 16sp labels, with bold submenu labels and normal-weight actions.
- Spacing: Android-standard 48dp touch targets and 18dp horizontal content insets.
- Shape: project-aligned 14dp popup radius, clipped outer scroll surface, subtle elevation, and an inset divider between every item.
- Assets: action icons remain removed; only entries that open a second-level menu show the existing project right-chevron affordance.
- Interaction: system actions and local copy/edit controls remain fixed in the root menu; user-created/imported root rows scroll, while every second-level menu keeps “返回” fixed and scrolls its remaining rows when needed, including image menus.
- Shadow: the native bottom-weighted popup elevation is disabled in favor of the project's uniform four-sided soft shadow.
- Placement: popup coordinates are clamped to 16dp screen margins.

## Findings

- Source-level regression coverage verifies icon removal, responsive width, capped height, scrollbar visibility, and popup placement.
- A current rendered screenshot is intentionally omitted because the user asked to perform visual confirmation directly.

## Comparison history

1. The source screenshot showed a narrow, icon-heavy popup with wrapped labels and clipped lower rows.
2. The implementation removes decorative icons, widens the popup, prevents label wrapping, and provides native scrolling for long menus.
3. Rendered visual comparison remains user-owned.

## Final result

final result: blocked

Blocked only on the intentionally omitted post-change screenshot comparison.
