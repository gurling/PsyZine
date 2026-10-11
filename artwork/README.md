# PsyZine launcher artwork

`psyzine-icon.png` is the original uploaded 1254 × 1254 PNG, preserved byte for byte.
Regenerate derived Android resources with `python3 tools/generate-launcher-icons.py`
(requires Pillow). No artwork is redrawn, cropped or stretched during generation;
legacy round icons apply Android's circular mask to the surrounding star field.

Legacy icons use 48/72/96/144/192 pixels. Adaptive foreground and monochrome layers
use 108/162/216/324/432 pixels. The full image occupies a centered 72dp square in
the 108dp adaptive layer; the approximately 58dp circular emblem, including the
complete eye, remains inside the centered 66dp safe circle. The black background
extends behind Android's launcher masks. Android's themed-icon option tints the
derived monochrome layer; ordinary icons retain the supplied colors.

Shared `main` resources apply to both Direct and Play, Debug and Release variants.

`psyzine-eye-closed.png` preserves the uploaded closed-eye counterpart byte for byte.
Onboarding uses 512px, uncropped copies of both artworks in `drawable-nodpi/`,
showing the open eye only when all four acknowledgements are checked.
