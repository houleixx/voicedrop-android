# Design QA — Share sheet divider symmetry

**Evidence**

- Source visual truth: `/private/tmp/voicedrop-share-spacing-v2.png`, plus the approved 16dp/16dp divider spacing specification.
- Source pixels: 1080 × 2400.
- Implementation screenshot: `/private/tmp/voicedrop-share-divider-16dp.png`.
- Implementation pixels: 1080 × 2400 on Pixel 7 API 31 at 420 dpi (approximately 411 × 914dp).
- Normalized comparison: `/private/tmp/share-divider-16dp-comparison.png`; equal 1080 × 780 crops from the same device and interaction state.
- State: recording article detail → 更多 → 分享 Bottom Sheet.

**Full-view comparison evidence**

- The overall sheet width, bottom safe area, icons, labels, colors, and actions are unchanged.
- Only the vertical rhythm around the row divider changed, so the final row and system navigation remain at the same position.

**Focused region comparison evidence**

- Before: the first-row label was approximately 13dp above the divider, while the divider was approximately 20dp above the second-row circle.
- After: UI bounds show the first-row label bottom at y=1883 and divider at y=1925, giving exactly 16dp; the divider ends at y=1928 and the second-row circle begins at y=1970, also giving exactly 16dp.
- The “其它分享” label still ends at y=2198, confirming that bottom spacing did not increase.

**Findings**

- No remaining P0, P1, or P2 findings.
- The divider now reads as a true section boundary rather than touching either row.

**Required fidelity surfaces**

- Fonts and typography: unchanged native 14sp labels, normal weight, centered and single-line.
- Spacing and layout rhythm: the divider has 16dp clear space on both sides; final-row and navigation safe-area spacing are preserved.
- Colors and visual tokens: unchanged VoiceDrop greens, Xiaohongshu red, warm neutral utility fill, and secondary text.
- Image quality and asset fidelity: all existing vector and icon-font assets remain unchanged and sharp.
- Copy and content: all destination labels and actions are unchanged.

**Interaction evidence**

- Opened the recording overflow menu and the share Bottom Sheet after installing the updated APK.
- Confirmed all five share targets remain visible and tappable.
- Unit tests and debug APK assembly passed.

**Comparison history**

1. Earlier finding (P2): the divider's 13dp/20dp spacing was visibly asymmetric.
2. Fix: set `FIRST_ROW_EXTRA_BOTTOM_DP = 11` and `ROW_SEPARATOR_HEIGHT_DP = 17`, producing 16dp clear space above and below the 1dp line.
3. Post-fix evidence: `/private/tmp/voicedrop-share-divider-16dp.png` and `/private/tmp/share-divider-16dp-comparison.png`.

final result: passed
