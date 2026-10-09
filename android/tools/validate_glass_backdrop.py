"""Local native-Skia validation of the actual page-backdrop shader; no Android/CI dependency."""
import argparse
from pathlib import Path
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime-package-dir", type=Path)
    parser.add_argument("--preview-dir", type=Path, help="Synthetic native-Skia optical fixtures, not device screenshots")
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
    shader_dir = Path(__file__).resolve().parents[1] / "app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/component/glass"
    adaptive_tint = (shader_dir / "GlassAdaptiveTint.kt").read_text(encoding="utf-8").split('"""', 2)[1]
    lens_profile = (shader_dir / "GlassLensProfile.kt").read_text(encoding="utf-8").split('"""', 2)[1]
    backdrop_parts = (shader_dir / "GlassBackdropShader.kt").read_text(encoding="utf-8").split('"""')
    source = adaptive_tint + lens_profile + backdrop_parts[1]
    effect = skia.RuntimeEffect.MakeForShader(source)
    profile_effect = skia.RuntimeEffect.MakeForShader(lens_profile + """
        half4 main(float2 p) {
            return half4(half3(glassLensProfile(p.x - 0.5, 100.0 / 0.85, 1.0)), 1.0);
        }
    """)
    profile_surface = skia.Surface(101, 1)
    profile_surface.getCanvas().drawPaint(skia.Paint(Shader=skia.RuntimeShaderBuilder(profile_effect).makeShader()))
    profile_values = profile_surface.makeImageSnapshot().toarray()[0, :, 0].astype(int)
    assert profile_values[0] == 0 and profile_values[-1] == 0, "Lens must meet its edge and center without seams"
    assert profile_values[50] >= 254, "Rounded lens has no inward peak"
    assert np.max(np.abs(profile_values - profile_values[::-1])) <= 1, "Lens profile is asymmetric"
    assert np.max(np.abs(np.diff(profile_values))) <= 8, "Lens profile has a hard derivative jump"
    print("PASS: shared rounded lens profile, smooth edge/center and bounded inward peak")
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
            expected = base + (brightness - base) * .42 * (1 - .20 * .60)
            assert np.max(np.abs(rgb / 255.0 - expected)) < .006, "Adaptive tint deviates from the current shared sampling formula"
            if previous is not None:
                assert np.max(np.abs(rgb - previous)) <= 3, "Adaptive tint has a hard brightness jump"
            previous = rgb
    # 实色策略在合成阶段屏蔽背景，不要求采样辅助函数提前停止适应。
    print("PASS: shared sampled tint formula and continuous brightness response")
    w, h = 320, 180
    page = skia.Surface(w, h)
    page.getCanvas().clear(0xFF75869A)
    paint = skia.Paint(Color=0xFFF4C856, StrokeWidth=2)
    for x in range(0, w, 8):
        page.getCanvas().drawLine(x, 0, x, h, paint)

    def render(opacity, blur, refract, dark, page_color=None, contact=0, capsule=False, surface_color=None, menu=False, density=1.0, runtime_effect=None, input_scene=None):
        builder = skia.RuntimeShaderBuilder(effect if runtime_effect is None else runtime_effect)
        input_page = page if input_scene is None else input_scene
        if page_color is not None:
            input_page = skia.Surface(w, h)
            input_page.getCanvas().clear(page_color)
        builder.setChild("content", input_page.makeImageSnapshot().makeShader())
        builder.setUniform("resolution", skia.V2(w, h))
        builder.setUniform("bounds", skia.V4(w / 2, h / 2, 135, 65))
        builder.setUniform("shape", skia.V4(24, blur, refract, opacity))
        builder.setUniform("contact", skia.V4(w / 2, 0, contact, 1))
        builder.setUniform("capsuleMode", 1.0 if capsule else 0.0)
        builder.setUniform("menuMaterial", 1.0 if menu else 0.0)
        builder.setUniform("pixelDensity", density)
        builder.setUniform("capsuleOptics", skia.V2(0, 0))
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
        capsule_difference = np.max(np.abs(capsule_rest.astype(int) - capsule_contact.astype(int)), axis=2)
        assert np.count_nonzero(capsule_difference > 2) > 10, "Control bevel does not follow contact"
        assert capsule_difference[65:115, 90:230].max() <= 1, "Control reflection creates a central hot spot"
        assert np.max(capsule_contact[:20, :, 3]) == 0, "Control bevel leaks outside the capsule"
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

    # 菜单独立材质：真实背景有透显，但不会改变普通按钮和 Tab 的默认保护策略。
    for dark in (False, True):
        opacity = .78 if dark else .70
        bright = render(opacity, 12, 0, dark, 0xFFFFFFFF, menu=True)
        dim = render(opacity, 12, 0, dark, 0xFF000000, menu=True)
        response = np.abs(bright[65:115, 90:230, :3].astype(int) - dim[65:115, 90:230, :3].astype(int)).mean()
        assert 10 < response < 100, f"Menu is opaque or loses its text contrast protection: dark={dark}, response={response:.2f}"
        assert np.max(bright[:20, :, 3]) == 0, "Menu material leaks outside its bounds"
        solid = render(1, 0, 0, dark, menu=True)
        assert np.ptp(solid[60:120, 90:230, :3].astype(int), axis=0).max() <= 1, "Opaque menu leaks background detail"
    print("PASS: menu shader compiles, transmits bounded background pixels, isolates bounds and preserves opaque fallback")

    # Representative neutral fills; the exact material formula is covered by Kotlin tests.
    # The center separates subtly; a broad matte disk must not substitute for the bevel.
    for dark, page_color, body, accent, opacity in (
            (False, 0xFFF2F3F8, .08222, .00889, .46889),
            (True, 0xFF17191D, .06583, .00667, .50889)):
        base = np.array([(page_color >> shift & 255) / 255 for shift in (16, 8, 0)])
        neutral = np.ones(3) if dark else np.array([144, 152, 162]) / 255
        fill = base + (neutral - base) * body
        fill += (np.array([0, 122, 255]) / 255 - fill) * accent
        static = render(opacity, 0, 0, dark, page_color, capsule=True, surface_color=(*fill, 1))
        reference = skia.Surface(w, h)
        reference.getCanvas().clear(page_color)
        backdrop = reference.makeImageSnapshot().toarray()
        delta = np.abs(static[65:115, 90:230, :3].astype(int) - backdrop[65:115, 90:230, :3].astype(int))
        assert 2 <= delta.mean() <= 14, "Control center disappears or becomes a dense matte fill"
        assert np.max(static[:20, :, 3]) == 0, "Stationary material creates an external frame or glow"
    print("PASS: stationary light/dark neutral capsule body remains visible without contact or refraction")

    # Exercise the actual foreground lens independently: it must add no material or duplicate layer.
    foreground_source = adaptive_tint + lens_profile + (shader_dir / "TabForegroundLens.kt").read_text(encoding="utf-8").split('"""', 2)[1]
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

    if args.preview_dir:
        args.preview_dir.mkdir(parents=True, exist_ok=True)
        # Render the actual shader on controlled backgrounds, not a mock Android screenshot.
        # These isolate the optical material; Compose clipping/driver behavior needs a device.
        sheet = skia.Surface(960, 640)
        for dark in (False, True):
            width, height = 480, 640
            background = 0xFF17191D if dark else 0xFFF2F3F8
            panel = skia.Surface(width, height)
            scene = skia.Surface(width, height)
            scene.getCanvas().clear(background)
            for y in (245, 435):
                for x in range(24, 458, 12):
                    color = (0xFF386182 if dark else 0xFFD0DDEB) if x % 24 == 0 else background
                    scene.getCanvas().drawRect(skia.Rect.MakeXYWH(x, y - 40, 4, 80), skia.Paint(Color=color))
            panel.getCanvas().drawImage(scene.makeImageSnapshot(), 0, 0)
            text = skia.Paint(Color=0xFFF2F3F8 if dark else 0xFF17191D, AntiAlias=True)
            typeface = skia.Typeface.MakeFromName("Segoe UI", skia.FontStyle.Normal())
            font = skia.Font(typeface, 18)
            panel.getCanvas().drawString("DARK / native shader fixture" if dark else "LIGHT / native shader fixture", 24, 30, font, text)
            for row, (y, movement, label) in enumerate(((105, 0, "Rest / flat background"),
                                                       (245, 0, "Rest / detailed background"),
                                                       (435, 1, "Moving / detailed background"))):
                panel.getCanvas().drawString(label, 24, y - 55, font, text)
                for cx, halfwidth, halfheight, circular in ((105, 36, 36, True), (315, 88, 36, False)):
                    body = .07 if dark and circular else .09 if circular else .06583 if dark else .08222
                    opacity = .46 if dark and circular else .42 if circular else .50889 if dark else .46889
                    accent = .006 if circular else .00667 if dark else .00889
                    base = np.array([(background >> shift & 255) / 255 for shift in (16, 8, 0)])
                    neutral = np.ones(3) if dark else np.array([144, 152, 162]) / 255
                    tint = base + (neutral - base) * body
                    tint += (np.array([0, 122, 255]) / 255 - tint) * accent
                    builder = skia.RuntimeShaderBuilder(effect)
                    builder.setChild("content", scene.makeImageSnapshot().makeShader(skia.SamplingOptions(skia.FilterMode.kLinear)))
                    builder.setUniform("resolution", skia.V2(width, height))
                    builder.setUniform("bounds", skia.V4(cx, y, halfwidth, halfheight))
                    builder.setUniform("shape", skia.V4(halfheight, .75, (1.8 if circular else 1.2) * (1 - movement) + 4.5 * movement, opacity))
                    builder.setUniform("contact", skia.V4(cx + halfwidth * .6, y - halfheight * .6, movement, 1))
                    builder.setUniform("capsuleMode", 1.0)
                    builder.setUniform("menuMaterial", 0.0)
                    builder.setUniform("capsuleOptics", skia.V2(.4 * movement, .10 * movement))
                    builder.setUniform("pixelDensity", 1.5)
                    builder.setUniform("surfaceColor", skia.V4(*tint, 1))
                    panel.getCanvas().drawPaint(skia.Paint(Shader=builder.makeShader()))
                    panel.getCanvas().drawString("+" if circular else "Selected", cx - (6 if circular else 35), y + 6,
                                                 font, skia.Paint(Color=0xFF007AFF, AntiAlias=True))
            panel.getCanvas().drawString("Synthetic fixture - NOT device acceptance", 24, 600, skia.Font(typeface, 16), text)
            sheet.getCanvas().drawImage(panel.makeImageSnapshot(), 480 if dark else 0, 0)
        output = args.preview_dir / "glass-controls-optical-fixture.png"
        sheet.makeImageSnapshot().save(str(output), skia.kPNG)
        print(f"PREVIEW: {output}")


if __name__ == "__main__":
    main()
