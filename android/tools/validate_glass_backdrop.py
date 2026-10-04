"""Local native-Skia validation of the actual page-backdrop shader; no Android/CI dependency."""
import argparse
from pathlib import Path
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime-package-dir", type=Path)
    args = parser.parse_args()
    if args.runtime_package_dir:
        sys.path.insert(0, str(args.runtime_package_dir))
    import numpy as np
    import skia
    source = (Path(__file__).resolve().parents[1] / "app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/GlassBackdropShader.kt").read_text(encoding="utf-8").split('"""', 2)[1]
    effect = skia.RuntimeEffect.MakeForShader(source)
    w, h = 320, 180
    page = skia.Surface(w, h)
    page.getCanvas().clear(0xFF75869A)
    paint = skia.Paint(Color=0xFFF4C856, StrokeWidth=2)
    for x in range(0, w, 8):
        page.getCanvas().drawLine(x, 0, x, h, paint)

    def render(opacity, blur, refract, dark, page_color=None, contact=0, capsule=False):
        builder = skia.RuntimeShaderBuilder(effect)
        input_page = page
        if page_color is not None:
            input_page = skia.Surface(w, h)
            input_page.getCanvas().clear(page_color)
        builder.setChild("content", input_page.makeImageSnapshot().makeShader())
        builder.setUniform("resolution", skia.V2(w, h))
        builder.setUniform("bounds", skia.V4(w / 2, h / 2, 135, 65))
        builder.setUniform("shape", skia.V4(24, blur, refract, opacity))
        builder.setUniform("contact", skia.V4(w / 2, 0, contact, 1))
        builder.setUniform("capsuleMode", 1.0 if capsule else 0.0)
        builder.setUniform("surfaceColor", skia.V4(*((.08, .09, .12, 1) if dark else (.97, .98, 1, 1))))
        output = skia.Surface(w, h)
        output.getCanvas().drawPaint(skia.Paint(Shader=builder.makeShader()))
        return output.makeImageSnapshot().toarray()

    for dark in (False, True):
        rest = render(.45, 0, 0, dark)
        active = render(.45, 0, 3, dark)
        assert np.max(rest[:20, :, 3]) == 0, "Glass leaks outside its bounds"
        assert np.min(rest[50:130, 80:240, 3]) == 255, "Backdrop not captured inside material"
        assert np.count_nonzero(np.max(np.abs(rest.astype(int) - active.astype(int)), axis=2) > 4) > 100, "Real page pixels are not refracted"
        solid = render(1, 0, 0, dark)
        assert np.ptp(solid[60:120, 90:230, :3].astype(int), axis=0).max() <= 1, "Opaque fallback leaks page detail"
        protected = render(.45, 0, 0, dark, 0xFFFFFFFF if dark else 0xFF000000)
        center = protected[60:120, 90:230, :3].mean() / 255
        assert (center < .45 if dark else center > .60), "Opposite-brightness background does not gain contrast protection"
        lit = render(.45, 0, 0, dark, contact=1)
        difference = np.max(np.abs(rest.astype(int) - lit.astype(int)), axis=2)
        assert np.count_nonzero(difference > 3) > 30, "Contact does not light the material rim"
        assert difference[65:115, 90:230].max() <= 1, "Contact creates a central hot spot"
        assert np.max(lit[:20, :, 3]) == 0, "Contact produces external glow"
        capsule_rest = render(.45, 0, 0, dark, capsule=True)
        capsule_contact = render(.45, 0, 0, dark, contact=1, capsule=True)
        assert np.array_equal(capsule_rest, capsule_contact), "Tab background duplicates foreground rim light"
        print(f"PASS: {'dark' if dark else 'light'} backdrop pixels, lens displacement, mask isolation, contrast protection, rim contact and solid fallback")

    # Exercise the actual foreground lens independently: it must add no material or duplicate layer.
    foreground_source = (Path(__file__).resolve().parents[1] / "app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/TabForegroundLens.kt").read_text(encoding="utf-8").split('"""', 2)[1]
    foreground_effect = skia.RuntimeEffect.MakeForShader(foreground_source)
    glyphs = skia.Surface(w, h)
    glyphs.getCanvas().clear(0)
    for x in range(0, w, 6):
        glyphs.getCanvas().drawLine(x, 55, x, 125, skia.Paint(Color=0xFF2684FF, StrokeWidth=2))
    image = glyphs.makeImageSnapshot()

    def foreground(displacement, center_x):
        builder = skia.RuntimeShaderBuilder(foreground_effect)
        builder.setChild("content", image.makeShader(skia.SamplingOptions(skia.FilterMode.kLinear)))
        builder.setUniform("resolution", skia.V2(w, h))
        builder.setUniform("capsule", skia.V4(center_x, h / 2, 42, 28))
        builder.setUniform("lens", skia.V2(displacement, 1))
        surface = skia.Surface(w, h)
        surface.getCanvas().drawPaint(skia.Paint(Shader=builder.makeShader()))
        return surface.makeImageSnapshot().toarray()

    original = image.toarray()
    for center_x in (80, 140, 200, 260):
        rest = foreground(0, center_x)
        active = foreground(1.2, center_x)
        assert np.array_equal(rest, original), "Stationary foreground is not an identity transform"
        outside = np.ones((h, w), dtype=bool)
        outside[62:118, int(center_x - 42):int(center_x + 42)] = False
        assert np.array_equal(active[outside], original[outside]), "Foreground lens affects other tabs"
        difference = np.max(np.abs(active.astype(int) - original.astype(int)), axis=2)
        assert np.count_nonzero(difference > 3) > 20, "Moving lens does not displace covered foreground pixels"
        assert abs(active[:, :, 3].astype(float).sum() / original[:, :, 3].astype(float).sum() - 1) < .02, "Lens adds material or duplicates glyph coverage"
    print("PASS: foreground stationary identity, moving local refraction, outside-tab isolation and single-layer coverage")


if __name__ == "__main__":
    main()
