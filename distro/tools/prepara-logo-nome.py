"""Draws distro/docs/immagini/logo-nome.svg, the logo with the name in Space Grotesk, for the README.

Needs fontTools. Usage: prepara-logo-nome.py LOGO.svg FONT.ttf OUT.svg
"""
import re, sys
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.boundsPen import BoundsPen
logo_file, font_file, out = sys.argv[1:4]
logo = open(logo_file).read()
paths = re.findall(r'<path d="[^"]*"', logo)
font = TTFont(font_file); gs = font.getGlyphSet(); cmap = font.getBestCmap()
# Logo crop: the sofa spans x 31.8–992.2, y 165.9–858.1 of its 1024 box.
LX, LY, LW, LH = 31.776, 165.873, 960.448, 692.254
GAP = 150
TEXT_W = 0.78 * LW
names = [cmap[ord(c)] for c in 'Salotto']
x = 0; outline = []
bounds = BoundsPen(gs)
for name in names:
    gs[name].draw(TransformPen(bounds, (1, 0, 0, 1, x, 0))); x += gs[name].width
xmin, ymin, xmax, ymax = bounds.bounds
scale = TEXT_W / (xmax - xmin)
top = LY + LH + GAP
pen = SVGPathPen(gs)
x = 0
for name in names:
    # Font units grow upwards: flip, scale, put the tallest point at `top`, centre under the sofa.
    t = (scale, 0, 0, -scale, LX + (LW - TEXT_W) / 2 - xmin * scale + x * scale, top + ymax * scale)
    gs[name].draw(TransformPen(pen, t)); x += gs[name].width
height = top + (ymax - ymin) * scale - LY
svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="{LX:.1f} {LY:.1f} {LW:.1f} {height:.1f}">
  <title>Salotto</title>
  <g fill="#E8CFC4">
    {chr(10).join("    " + p + "/>" for p in paths).strip()}
    <path d="{pen.getCommands()}"/>
  </g>
</svg>
'''
open(out, 'w').write(svg)
print(len(svg), height)
