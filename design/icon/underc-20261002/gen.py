"""Generate 10 UnderC icon concepts (letter C + ocean) as SVG plus a preview sheet."""
import math

R = 'x="0" y="0" width="512" height="512" rx="112"'


def C(r=150, cx=256, cy=256, a=45):
    """Open arc shaped like a C, gap facing right, half-angle a degrees."""
    t = math.radians(a)
    x, y1, y2 = cx + r * math.cos(t), cy - r * math.sin(t), cy + r * math.sin(t)
    return f"M{x:.1f},{y1:.1f} A{r},{r} 0 1,0 {x:.1f},{y2:.1f}"


def wave(y, amp, n, w=512):
    """Filled wave band from height y down to the bottom edge."""
    seg = w / n
    d = f"M0,{y}"
    for i in range(n):
        x0 = i * seg
        d += f" Q{x0 + seg / 4:.1f},{y - amp} {x0 + seg / 2:.1f},{y} T{x0 + seg:.1f},{y}"
    return d + " V512 H0 Z"


icons = {}

icons["01-tide"] = ("潮汐 C", f'''
<defs><mask id="m"><path d="{C()}" stroke="#fff" stroke-width="84" fill="none" stroke-linecap="round"/></mask>
<linearGradient id="g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2fd3e8"/><stop offset="1" stop-color="#0a5fb4"/></linearGradient></defs>
<rect {R} fill="#0b1f3a"/>
<path d="{C()}" stroke="#e8f6ff" stroke-width="84" fill="none" stroke-linecap="round"/>
<g mask="url(#m)"><path d="{wave(270, 26, 3)}" fill="url(#g)"/><path d="{wave(305, 18, 4)}" fill="#0a5fb4" opacity=".6"/></g>''')

rays = "".join(f'<polygon points="{x},0 {x + 40},0 {x + 150},512 {x + 70},512" fill="#fff" opacity=".07"/>'
               for x in (60, 190, 300))
icons["02-deep-light"] = ("深海光束", f'''
<defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#14b8d4"/><stop offset=".55" stop-color="#0a4f8f"/><stop offset="1" stop-color="#031630"/></linearGradient>
<linearGradient id="c" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffffff"/><stop offset="1" stop-color="#9fe8ff"/></linearGradient>
<clipPath id="k"><rect {R}/></clipPath></defs>
<rect {R} fill="url(#g)"/><g clip-path="url(#k)">{rays}</g>
<path d="{C()}" stroke="url(#c)" stroke-width="76" fill="none" stroke-linecap="round"/>''')

icons["03-curl-wave"] = ("浪卷 C", f'''
<rect {R} fill="#eef7fc"/>
<path d="M362,362 A150,150 0 1,1 330,128" stroke="#0b5fa5" stroke-width="72" fill="none" stroke-linecap="round"/>
<path d="M300,118 C360,80 432,110 428,170 C425,212 380,226 356,200 C340,182 352,158 372,162" stroke="#0b5fa5" stroke-width="40" fill="none" stroke-linecap="round"/>
<circle cx="440" cy="232" r="10" fill="#38bdf8"/><circle cx="462" cy="200" r="6" fill="#38bdf8"/><circle cx="420" cy="262" r="6" fill="#38bdf8"/>
<path d="M60,450 q40,-22 80,0 t80,0 t80,0 t80,0 t80,0" stroke="#38bdf8" stroke-width="16" fill="none" stroke-linecap="round"/>''')

layers = "".join(f'<path d="{C(r, a=40)}" stroke="{c}" stroke-width="30" fill="none" stroke-linecap="round"/>'
                 for r, c in ((186, "#0a4f8f"), (142, "#1580c4"), (98, "#2bb3e0"), (54, "#7fe3ff")))
icons["04-strata"] = ("海层", f'<rect {R} fill="#031a33"/>{layers}')

bub = ""
for i in range(14):
    a = math.radians(50 + i * (260 / 13))
    rr = 22 + i * 2.0  # bubbles grow toward the bottom of the C
    x, y = 256 + 150 * math.cos(a), 256 - 150 * math.sin(a)
    bub += (f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{rr:.1f}" fill="#e9fbff" fill-opacity=".92"/>'
            f'<circle cx="{x - rr * .35:.1f}" cy="{y - rr * .35:.1f}" r="{rr * .22:.1f}" fill="#fff"/>')
icons["05-bubbles"] = ("气泡", f'''
<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#22c3d6"/><stop offset="1" stop-color="#0b4a9c"/></linearGradient></defs>
<rect {R} fill="url(#g)"/>{bub}
<circle cx="400" cy="140" r="9" fill="#e9fbff" opacity=".7"/><circle cx="420" cy="100" r="6" fill="#e9fbff" opacity=".5"/>''')

surface = "M0,214 Q64,190 128,214 T256,214 T384,214 T512,214"
icons["06-under-surface"] = ("水面之下", f'''
<defs><clipPath id="k"><rect {R}/></clipPath>
<clipPath id="top"><path d="{surface} V0 H0 Z"/></clipPath>
<clipPath id="bot"><path d="{surface} V512 H0 Z"/></clipPath>
<linearGradient id="s" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#1678c2"/><stop offset="1" stop-color="#062a55"/></linearGradient></defs>
<g clip-path="url(#k)"><rect width="512" height="512" fill="#dff3ff"/>
<path d="{surface} V512 H0 Z" fill="url(#s)"/>
<g clip-path="url(#top)"><path d="{C()}" stroke="#0b2545" stroke-width="80" fill="none" stroke-linecap="round"/></g>
<g clip-path="url(#bot)"><path d="{C(cx=268)}" stroke="#bdefff" stroke-width="80" fill="none" stroke-linecap="round"/></g></g>''')

icons["07-droplet"] = ("水滴", f'''
<defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#38d0f0"/><stop offset="1" stop-color="#0753a8"/></linearGradient></defs>
<rect {R} fill="#ffffff"/>
<path d="M256,52 C256,52 112,222 112,318 a144,144 0 0,0 288,0 C400,222 256,52 256,52 Z" fill="url(#g)"/>
<path d="{C(78, 256, 318)}" stroke="#ffffff" stroke-width="40" fill="none" stroke-linecap="round"/>''')

icons["08-ripple"] = ("涟漪", f'''
<rect {R} fill="#062b4f"/>
<path d="{C(208, a=38)}" stroke="#2bb3e0" stroke-width="10" fill="none" stroke-linecap="round" opacity=".35"/>
<path d="{C(168, a=40)}" stroke="#2bb3e0" stroke-width="14" fill="none" stroke-linecap="round" opacity=".6"/>
<path d="{C(104, a=45)}" stroke="#7fe8ff" stroke-width="64" fill="none" stroke-linecap="round"/>''')

icons["09-minimal-line"] = ("极简线条", f'''
<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#1188d6"/><stop offset="1" stop-color="#0a3d7a"/></linearGradient></defs>
<rect {R} fill="url(#g)"/>
<path d="{C(140, 236)}" stroke="#ffffff" stroke-width="44" fill="none" stroke-linecap="round"/>
<path d="M300,220 q22,-16 44,0 t44,0 t44,0" stroke="#9feaff" stroke-width="18" fill="none" stroke-linecap="round"/>
<path d="M300,262 q22,-16 44,0 t44,0 t44,0" stroke="#ffffff" stroke-width="18" fill="none" stroke-linecap="round"/>
<path d="M300,304 q22,-16 44,0 t44,0 t44,0" stroke="#9feaff" stroke-width="18" fill="none" stroke-linecap="round"/>''')

refl = "".join(f'<rect x="{256 - w / 2}" y="{y}" width="{w}" height="9" rx="4.5" fill="#ffe9a8" opacity="{o}"/>'
               for y, w, o in ((352, 170, .75), (378, 130, .55), (404, 90, .4), (430, 52, .28)))
icons["10-moon-sea"] = ("月夜海面", f'''
<defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0b1d3f"/><stop offset="1" stop-color="#132f5f"/></linearGradient>
<mask id="m"><rect width="512" height="512" fill="#fff"/><circle cx="300" cy="170" r="104" fill="#000"/></mask>
<clipPath id="k"><rect {R}/></clipPath></defs>
<g clip-path="url(#k)"><rect width="512" height="512" fill="url(#g)"/>
<circle cx="236" cy="190" r="128" fill="#ffe9a8" mask="url(#m)"/>
<path d="{wave(330, 14, 4)}" fill="#0a5fb4"/><path d="{wave(360, 12, 5)}" fill="#073f80"/>{refl}</g>''')

html = ['<html><meta charset="utf-8"><body style="margin:0;background:#3a3f47;font-family:Microsoft YaHei">'
        '<div style="display:grid;grid-template-columns:repeat(5,260px);gap:24px;padding:24px">']
for k, (name, body) in icons.items():
    svg = f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">{body}</svg>'
    with open(f"{k}.svg", "w", encoding="utf-8") as f:
        f.write(svg)
    html.append(f'<div style="text-align:center;color:#fff"><img src="{k}.svg" width="220">'
                f'<div style="margin:4px 0">{k} {name}</div>'
                f'<img src="{k}.svg" width="48"> <img src="{k}.svg" width="32"> <img src="{k}.svg" width="16"></div>')
html.append('</div></body></html>')
with open("preview.html", "w", encoding="utf-8") as f:
    f.write("".join(html))
