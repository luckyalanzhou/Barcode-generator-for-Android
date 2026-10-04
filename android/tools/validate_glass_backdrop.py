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

    def render(opacity, blur, refract, dark, page_color=None, contact=0, capsule=False, surface_color=None):
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
        builder.setUniform("surfaceColor", skia.V4(*(surface_color if surface_color is not None else ((.08, .09, .12, 1) if dark else (.97, .98, 1, 1)))))
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
        menu_bright = render(.56, 0, 0, dark, 0xFFFFFFFF)
        menu_dark = render(.56, 0, 0, dark, 0xFF000000)
        interior_response = np.abs(menu_bright[65:115, 90:230, :3].astype(int) - menu_dark[65:115, 90:230, :3].astype(int)).max()
        assert interior_response <= 21, "Menu content region still exposes strong background lettering"
        edge_response = np.abs(menu_bright[65:115, 27:29, :3].astype(int) - menu_dark[65:115, 27:29, :3].astype(int)).mean()
        assert edge_response > interior_response, "Menu rim loses its environment response"
        tab_bright = render(.56, 0, 0, dark, 0xFFFFFFFF, capsule=True)
        tab_dark = render(.56, 0, 0, dark, 0xFF000000, capsule=True)
        assert np.abs(tab_bright[65:115, 90:230, :3].astype(int) - tab_dark[65:115, 90:230, :3].astype(int)).mean() > 30, "Menu protection accidentally makes capsules opaque"
        print(f"PASS: {'dark' if dark else 'light'} backdrop pixels, lens displacement, mask isolation, contrast protection, rim contact and solid fallback")

    # Representative neutral fills; the exact material formula is covered by Kotlin tests.
    # Even with zero refraction/contact, a pure-color page must not erase the selected body.
    for dark, page_color, fill in ((False, 0xFFF2F3F8, (.86, .87, .90, 1)),
                                  (True, 0xFF17191D, (.20, .21, .22, 1))):
        static = render(.60, 0, 0, dark, page_color, capsule=True, surface_color=fill)
        reference = skia.Surface(w, h)
        reference.getCanvas().clear(page_color)
        backdrop = reference.makeImageSnapshot().toarray()
        delta = np.abs(static[65:115, 90:230, :3].astype(int) - backdrop[65:115, 90:230, :3].astype(int))
        assert delta.mean() >= 8, "Stationary selected body disappears on a uniform page"
        assert np.max(static[:20, :, 3]) == 0, "Stationary material creates an external frame or glow"
    print("PASS: stationary light/dark neutral capsule body remains visible without contact or refraction")

    # Exercise the actual foreground lens independently: it must add no material or duplicate layer.
    foreground_source = (Path(__file__).resolve().parents[1] / "app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/TabForegroundLens.kt").read_text(encoding="utf-8").split('"""', 2)[1]
    foreground_effect = skia.RuntimeEffect.MakeForShader(foreground_source)
    glyphs = skia.Surface(w, h)
    glyphs.getCanvas().clear(0)
    for x in range(0, w, 6):
        glyphs.getCanvas().drawLine(x, 55, x, 125, skia.Paint(Color=0xFF2684FF, StrokeWidth=2))
    image = glyphs.makeImageSnapshot()

    def foreground(displacement, center_x, background=None, opacity=.3, crop=None):
        builder = skia.RuntimeShaderBuilder(foreground_effect)
        left, right = (0, w) if crop is None else crop
        width = right - left
        glyph_image = image
        if crop is not None:
            cropped = skia.Surface(width, h)
            cropped.getCanvas().clear(0)
            cropped.getCanvas().drawImage(image, -left, 0)
            glyph_image = cropped.makeImageSnapshot()
        input_image = glyph_image
        if background is not None:
            atlas = skia.Surface(width * 2, h)
            atlas.getCanvas().clear(0)
            atlas.getCanvas().drawRect(skia.Rect.MakeWH(width, h), skia.Paint(Color=background))
            atlas.getCanvas().drawImage(glyph_image, width, 0)
            input_image = atlas.makeImageSnapshot()
        builder.setChild("content", input_image.makeShader(skia.SamplingOptions(skia.FilterMode.kLinear)))
        builder.setUniform("resolution", skia.V2(width, h))
        builder.setUniform("capsule", skia.V4(center_x - left, h / 2, 42, 28))
        builder.setUniform("lens", skia.V2(displacement, 1))
        builder.setUniform("atlasMode", 1.0 if background is not None else 0.0)
        builder.setUniform("surfaceOpacity", opacity)
        builder.setUniform("surfaceColor", skia.V4(.97, .98, 1, 1))
        surface = skia.Surface(width * 2 if background is not None else width, h)
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

    bright = foreground(1.2, 140, 0xFFFFFFFF)
    dark = foreground(1.2, 140, 0xFF000000)
    assert np.max(bright[:, w:, 3]) == 0, "Atlas input half leaks into visible output"
    assert np.array_equal(bright[:, :w, 3], dark[:, :w, 3]), "Adaptive contrast changes glyph geometry or coverage"
    assert np.count_nonzero(np.max(np.abs(bright[:, :w].astype(int) - dark[:, :w].astype(int)), axis=2) > 2) > 10, "Foreground does not respond to background contrast"
    stationary_atlas = foreground(0, 140, 0xFFFFFFFF)
    assert np.array_equal(stationary_atlas[:, :w], original), "Adaptive atlas changes stationary foreground"
    print("PASS: shared GPU input contrast response, unchanged alpha, invisible input half and stationary identity")

    for center_x in (45, 80, 140, 200, 275):
        left, right = max(0, center_x - 50), min(w, center_x + 50)
        for background in (0xFF000000, 0xFFFFFFFF):
            full = foreground(1.2, center_x, background)[:, :w]
            cropped = foreground(1.2, center_x, background, crop=(left, right))
            stitched = original.copy()
            stitched[:, left:right] = cropped[:, :right-left]
            delta = np.abs(stitched.astype(int) - full.astype(int))
            # toarray returns unpremultiplied channels: a one-step stored-color rounding
            # difference is amplified at low alpha. Compare actual composited contribution.
            full_contribution = np.rint(full[:, :, :3].astype(float) * full[:, :, 3:4] / 255)
            cropped_contribution = np.rint(stitched[:, :, :3].astype(float) * stitched[:, :, 3:4] / 255)
            premultiplied_delta = np.abs(full_contribution - cropped_contribution)
            assert delta[:, :, 3].max() <= 1 and premultiplied_delta.max() <= 1, "Cropped foreground has a visible seam or coordinate mismatch"
            assert np.max(cropped[:, right-left:, 3]) == 0, "Cropped input half leaks into other tabs"
    print("PASS: cropped foreground matches full-width pixels across positions and backgrounds, with no seam or input leakage")


if __name__ == "__main__":
    main()
