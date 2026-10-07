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
    # Native Skia cannot model Compose child RenderNodes changing draw destinations.
    scene_source = (Path(__file__).resolve().parents[1] / "app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/TabLiquidGlassScene.kt").read_text(encoding="utf-8")
    gpu_branch = scene_source.split('// Use fixed full-width coordinates', 1)[1].split("} else {", 1)[0]
    assert gpu_branch.count("this@drawWithContent.drawContent()") == 1, "GPU path must record child content exactly once"
    recording = gpu_branch.index("foregroundLayer.record(size = foregroundSize)")
    atlas = gpu_branch.index("atlasLayer.record(size = atlasSize)")
    assert recording < atlas, "Record complete foreground before atlas replay"
    assert "ClipOp.Difference" not in scene_source and "region.left" not in scene_source, "Moving split/crop must not return"
    assert gpu_branch.count("drawLayer(foregroundLayer)") == 1, "Foreground must have one replay destination"
    assert gpu_branch.count("drawLayer(atlasLayer)") == 1, "Output must be drawn once"
    assert "foregroundRenderer.effect(frame," in gpu_branch, "Shader must use full scene coordinates"
    print("PASS: full-width Compose input, single foreground replay and single output (source contract, not device validation)")
    import numpy as np
    import skia
    shader_dir = Path(__file__).resolve().parents[1] / "app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app"
    adaptive_tint = (shader_dir / "GlassAdaptiveTint.kt").read_text(encoding="utf-8").split('"""', 2)[1]
    source = adaptive_tint + (shader_dir / "GlassBackdropShader.kt").read_text(encoding="utf-8").split('"""', 2)[1]
    effect = skia.RuntimeEffect.MakeForShader(source)
    tint_effect = skia.RuntimeEffect.MakeForShader(adaptive_tint + """
        uniform float brightness;
        uniform float opacity;
        uniform float base;
        half4 main(float2 p) {
            return half4(glassAdaptiveTint(half3(base), half3(brightness), opacity), 1.0);
        }
    """)
    for base in (.12, .90):
        previous = None
        for brightness in np.linspace(0.0, 1.0, 41):
            builder = skia.RuntimeShaderBuilder(tint_effect)
            builder.setUniform("brightness", float(brightness))
            builder.setUniform("opacity", .60)
            builder.setUniform("base", base)
            fixture = skia.Surface(1, 1)
            fixture.getCanvas().drawPaint(skia.Paint(Shader=builder.makeShader()))
            rgb = fixture.makeImageSnapshot().toarray()[0, 0, :3].astype(int)
            assert np.max(np.abs(rgb / 255.0 - base)) < .11, "Adaptive tint loses the theme identity"
            if previous is not None:
                assert np.max(np.abs(rgb - previous)) <= 3, "Adaptive tint has a hard brightness jump"
            previous = rgb
        builder.setUniform("opacity", 1.0)
        fixture.getCanvas().drawPaint(skia.Paint(Shader=builder.makeShader()))
        opaque_rgb = fixture.makeImageSnapshot().toarray()[0, 0, :3] / 255.0
        assert np.max(np.abs(opaque_rgb - base)) < .005, "Solid contrast surface still adapts"
    print("PASS: bounded spatial tint, continuous brightness response and opaque accessibility identity")
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
    foreground_source = adaptive_tint + (shader_dir / "TabForegroundLens.kt").read_text(encoding="utf-8").split('"""', 2)[1]
    foreground_effect = skia.RuntimeEffect.MakeForShader(foreground_source)
    glyphs = skia.Surface(w, h)
    glyphs.getCanvas().clear(0)
    for x in range(0, w, 6):
        glyphs.getCanvas().drawLine(x, 55, x, 125, skia.Paint(Color=0xFF2684FF, StrokeWidth=2))
    image = glyphs.makeImageSnapshot()

    def foreground(displacement, center_x, background=None, opacity=.3):
        builder = skia.RuntimeShaderBuilder(foreground_effect)
        width = w
        glyph_image = image
        input_image = glyph_image
        if background is not None:
            atlas = skia.Surface(width * 2, h)
            atlas.getCanvas().clear(0)
            atlas.getCanvas().drawRect(skia.Rect.MakeWH(width, h), skia.Paint(Color=background))
            atlas.getCanvas().drawImage(glyph_image, width, 0)
            input_image = atlas.makeImageSnapshot()
        builder.setChild("content", input_image.makeShader(skia.SamplingOptions(skia.FilterMode.kLinear)))
        builder.setUniform("resolution", skia.V2(width, h))
        builder.setUniform("capsule", skia.V4(center_x, h / 2, 42, 28))
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
        active = foreground(1.8, center_x)
        assert np.array_equal(rest, original), "Stationary foreground is not an identity transform"
        outside = np.ones((h, w), dtype=bool)
        outside[62:118, int(center_x - 42):int(center_x + 42)] = False
        assert np.array_equal(active[outside], original[outside]), "Foreground lens affects other tabs"
        difference = np.max(np.abs(active.astype(int) - original.astype(int)), axis=2)
        assert np.count_nonzero(difference > 3) > 20, "Moving lens does not displace covered foreground pixels"
        assert abs(active[:, :, 3].astype(float).sum() / original[:, :, 3].astype(float).sum() - 1) < .02, "Lens adds material or duplicates glyph coverage"
    print("PASS: foreground stationary identity, moving local refraction, outside-tab isolation and single-layer coverage")

    bright = foreground(1.8, 140, 0xFFFFFFFF)
    dark = foreground(1.8, 140, 0xFF000000)
    assert np.max(bright[:, w:, 3]) == 0, "Atlas input half leaks into visible output"
    assert np.array_equal(bright[:, :w, 3], dark[:, :w, 3]), "Adaptive contrast changes glyph geometry or coverage"
    assert np.count_nonzero(np.max(np.abs(bright[:, :w].astype(int) - dark[:, :w].astype(int)), axis=2) > 2) > 10, "Foreground does not respond to background contrast"
    stationary_atlas = foreground(0, 140, 0xFFFFFFFF)
    assert np.array_equal(stationary_atlas[:, :w], original), "Adaptive atlas changes stationary foreground"
    print("PASS: shared GPU input contrast response, unchanged alpha, invisible input half and stationary identity")

    slow = foreground(.6, 140)
    assert np.count_nonzero(np.max(np.abs(slow.astype(int) - original.astype(int)), axis=2) > 3) > 20, "Slow drag lens is imperceptible in the pixel fixture"
    positions = list(range(45, 276, 23))
    for center_x in positions + positions[::-1]:
        for background in (0xFF000000, 0xFFFFFFFF):
            full = foreground(1.8, center_x, background)[:, :w]
            outside = np.ones((h, w), dtype=bool)
            outside[62:118, center_x-42:center_x+42] = False
            assert np.array_equal(full[outside], original[outside]), "Moving full-width atlas erases other tabs"
            assert abs(full[:, :, 3].astype(float).sum() / original[:, :, 3].sum() - 1) < .02, "Foreground coverage lost during movement"
    print("PASS: full-width forward/reverse lens sweep preserves outside glyphs and total coverage on dark/light backgrounds")


if __name__ == "__main__":
    main()
