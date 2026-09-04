# Screenshots

Nothing is committed here yet, and the README deliberately links to this list rather than to
image files — a README with six broken image icons is worse than a README with none.

## What to capture

Run the app in the `dev` profile with `AI_PROVIDER=mock`, sign in as a seeded account, and take
these seven. Order matters: it is the order a reader meets the product in.

1. **`landing.png`** — `/`, the top of the page, scrolled to zero. The one image that has to earn
   the click, so capture it in light mode where the type contrast is highest.
2. **`demo.png`** — `/demo`, framed on the two score dials and the band label. This is the shot
   that proves there is a real product behind the README, because a reader can open the same
   route and check it.
3. **`analysis-report.png`** — `/analyses/:id` for a real analysis, scrolled to the skills matrix
   so that STRONG, PARTIAL and MISSING all appear in one frame.
4. **`skill-gap.png`** — `/skill-gap`, the radar chart with its accessible table expanded. The
   expanded table is the point: it shows the charts are not the only way to read the data.
5. **`dashboard.png`** — `/dashboard` with at least four analyses in history so the trend line has
   a shape. A one-point line chart makes the feature look broken.
6. **`upload.png`** — `/analyses/new`, mid-flow, with a file chosen and the posting text present,
   so the two required inputs are visible together.
7. **`dark-mode.png`** — any of the above with the theme set to dark. One is enough; a second
   dark shot only doubles the maintenance.

## How to capture

Viewport 1440×900 for the full-page shots and 1440×720 for the framed ones, at 2× device pixel
ratio. Crop to content — no browser chrome, no OS window frame, no bookmark bar.

Use `demo@resumeiq.local` or a fresh account. Never capture a real resume: the whole product is
built on not exposing them, and the README is not the place to make an exception.

Save as PNG. Keep each file under 400 KB; run them through an optimiser if a full-page shot
lands over that.

## After capturing

Add the images to the README with descriptive alt text, then run
`python3 tools/verify_sources.py` — it checks that every image path a markdown file references
resolves on disk, so a typo in a filename fails the build instead of shipping a broken icon.
