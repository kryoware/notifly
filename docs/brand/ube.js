const MK = 'M16 80 V46 A23 23 0 0 1 62 46 V76 L84 50';
const mk = (c, s, w) => `<svg viewBox="0 0 100 100" width="${s}" height="${s}" style="display:block"><path d="${MK}" transform="translate(0 -1.5)" fill="none" stroke="${c}" stroke-width="${w || 16}" stroke-linecap="round" stroke-linejoin="round"/></svg>`;
document.querySelectorAll('[data-mk]').forEach(e => e.innerHTML = mk(e.dataset.mk || 'currentColor', e.dataset.s || 32, e.dataset.w));
document.querySelectorAll('[data-i]').forEach(e => {
  const s = e.dataset.s || 24;
  e.innerHTML = `<svg viewBox="0 -960 960 960" width="${s}" height="${s}" fill="currentColor" style="display:block"><path d="${I[e.dataset.i]}"/></svg>`;
});

// Halftone dusk: dot radius tracks sky luminance, colour ramps halaya → ube → lilac → gata.
const P = { halaya: [58, 31, 102], ube: [116, 67, 230], lilac: [203, 182, 255], gata: [244, 240, 248] };
const mix = (a, b, t) => a.map((v, i) => Math.round(v + (b[i] - v) * t));
const ramp = v => v < .4 ? mix(P.halaya, P.ube, v / .4) : v < .78 ? mix(P.ube, P.lilac, (v - .4) / .38) : mix(P.lilac, P.gata, Math.min(1, (v - .78) / .22));
let seed = 11;
const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
document.querySelectorAll('canvas[data-ht]').forEach(cv => {
  const r = devicePixelRatio || 1, W = cv.clientWidth, H = cv.clientHeight;
  cv.width = W * r; cv.height = H * r;
  const g = cv.getContext('2d'); g.scale(r, r);
  const hz = +(cv.dataset.hz || .74) * H, sx = +(cv.dataset.sx || .64) * W, step = +(cv.dataset.step || 7);
  const bg = g.createLinearGradient(0, 0, 0, hz);
  bg.addColorStop(0, '#110D17'); bg.addColorStop(1, '#26134A');
  g.fillStyle = bg; g.fillRect(0, 0, W, H);
  for (let y = step / 2, row = 0; y < hz + step; y += step * .866, row++) {
    for (let x = row % 2 ? step / 2 : 0; x < W + step; x += step) {
      const t = Math.min(1, y / hz);
      let v = .02 + .56 * Math.pow(t, 1.9);
      const dx = (x - sx) / W * 1.5, dy = (y - hz) / H;
      v += .75 * Math.exp(-(dx * dx + dy * dy) * 5.5);
      const c = Math.sin(y * .05 + Math.sin(x * .009) * 1.8) * Math.sin(x * .005 + y * .013);
      v *= 1 - .32 * Math.max(0, c);
      v = Math.max(0, Math.min(1, v));
      const rad = step * .52 * Math.sqrt(v);
      if (rad < .4) continue;
      g.fillStyle = `rgb(${ramp(v)})`;
      g.beginPath(); g.arc(x, y, rad, 0, 7); g.fill();
    }
  }
  g.fillStyle = '#110D17';
  g.fillRect(0, hz, W, H - hz);
  if (!cv.dataset.sky) return;
  seed = +cv.dataset.sky;
  const lit = [];
  for (let x = -4; x < W;) {
    const w = 12 + rnd() * 30, near = Math.abs(x - sx) < W * .09;
    const h = near ? 4 + rnd() * 10 : (x / W > .12 && x / W < .42 ? 26 : 8) + rnd() * H * .14;
    g.fillRect(x, hz - h, w + 1, h + 1);
    if (h > 30 && rnd() > .45) lit.push([x + 4 + rnd() * (w - 10), hz - h + 6 + rnd() * (h - 14)]);
    x += w;
  }
  g.fillStyle = 'rgba(203,182,255,.55)';
  lit.forEach(([x, y]) => g.fillRect(x, y, 2.4, 2.4));
});
