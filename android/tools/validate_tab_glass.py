"""Compile the actual tab shader with native Skia and check its pixel boundaries.

Optional local check: numpy and skia-python==138.0. This is not a GitHub Actions
or instrumentation dependency, and does not substitute for Android GPU testing.
"""

import argparse
from pathlib import Path
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime-package-dir", type=Path)
    parser.add_argument("--render", type=Path, help="Optional disposable comparison PNG")
    args = parser.parse_args()
    if args.runtime_package_dir:
        sys.path.insert(0, str(args.runtime_package_dir))
    import numpy as np
    import skia

    shader_file = Path(__file__).resolve().parents[1] / "app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/TabGlassShader.kt"
    source = shader_file.read_text(encoding="utf-8").split('"""', 2)[1]
    effect = skia.RuntimeEffect.MakeForShader(source)
    print("PASS: actual AGSL source compiled by native Skia")

    width, height = 640, 124

    def input_image(dark, grid):
        surface = skia.Surface(width, height)
        canvas = surface.getCanvas()
        bg = 0xFF17191E if dark else 0xFFF5F7FC
        canvas.clear(bg)
        paint = skia.Paint(Color=0xFFE2E7F0 if dark else 0xFF151E2E, AntiAlias=True)
        if grid:
            paint.setStrokeWidth(1.0)
            for x in range(0, width, 12):
                canvas.drawLine(x, 0, x, height, paint)
            for y in range(0, height, 12):
                canvas.drawLine(0, y, width, y, paint)
        else:
            font = skia.Font(skia.Typeface("Arial"), 26)
            for index, label in enumerate(("Generate", "History", "Favorites", "Settings")):
                x = (index + 0.5) * width / 4
                canvas.drawCircle(x, 42, 19, paint)
                canvas.drawString(label, x - font.measureText(label) / 2, 101, font, paint)
        return surface.makeImageSnapshot(), skia.Color4f(bg)

    def render(image, bg, center_x, motion, material):
        builder = skia.RuntimeShaderBuilder(effect)
        builder.setChild("content", image.makeShader(skia.SamplingOptions(skia.FilterMode.kLinear)))
        builder.setUniform("resolution", skia.V2(width, height))
        builder.setUniform("capsule", skia.V4(center_x, height / 2, 77, 58))
        builder.setUniform("optics", skia.V4(4.5 * motion, motion, 0, 2))
        builder.setUniform("touchPoint", skia.V2(center_x + 32, 16))
        builder.setUniform("backgroundColor", skia.V4(bg.fR, bg.fG, bg.fB, bg.fA))
        builder.setUniform("accentColor", skia.V4(0.05, 0.48, 0.95, 1))
        builder.setUniform("material", skia.V4(*material))
        builder.setUniform("edgeWidth", 2.2)
        surface = skia.Surface(width, height)
        surface.getCanvas().drawPaint(skia.Paint(Shader=builder.makeShader()))
        return surface.makeImageSnapshot()

    yy, xx = np.mgrid[0:height, 0:width] + 0.5
    comparisons = []
    for dark in (False, True):
        grid, bg = input_image(dark, True)
        original = grid.toarray()
        rest = render(grid, bg, 240, 0, (0, 0, 0, 0)).toarray()
        assert np.max(np.abs(rest.astype(int) - original.astype(int))) <= 1, "Static lens modifies original pixels"
        for center_x in (80, 240, 320, 560):
            warped = render(grid, bg, center_x, 1, (0, 0, 0, 0)).toarray()
            qx = np.abs(xx - center_x) - (77 - 58)
            qy = np.abs(yy - height / 2)
            distance = np.sqrt(np.maximum(qx, 0) ** 2 + qy ** 2) + np.minimum(np.maximum(qx, qy), 0) - 58
            difference = np.max(np.abs(warped.astype(int) - original.astype(int)), axis=2)
            assert difference[distance > 1.0].max() <= 1, "Pixels outside capsule changed"
            assert np.count_nonzero(difference[distance < -1] > 4) > 300, "Lens does not warp actual grid pixels"
            assert warped[:, :, 3].min() == 255, "Unexpected transparency/duplicate compositing"
        scene, bg = input_image(dark, False)
        materials = (.04, .021, .14, .085) if dark else (.14, .025, .20, .06)
        still = render(scene, bg, 240, 0, materials)
        active = render(scene, bg, 240, 1, materials)
        comparisons.extend((scene.toarray(), still.toarray(), active.toarray()))
        print(f"PASS: {'dark' if dark else 'light'} static identity, real grid displacement, capsule isolation and alpha")
    if args.render:
        args.render.parent.mkdir(parents=True, exist_ok=True)
        skia.Image.fromarray(np.concatenate(comparisons, axis=0)).save(str(args.render), skia.kPNG)
        print(f"Comparison: {args.render}")


if __name__ == "__main__":
    main()
