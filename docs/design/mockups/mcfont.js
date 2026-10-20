// Renders text with Minecraft's own font atlas (mc/ascii.png) so numbers, keybinds and HUD text look like the
// game. Usage: <span data-mc="Ctrl F" data-color="#e8d6ae" data-scale="2" data-shadow></span>
(function () {
  const img = new Image();
  img.src = 'mc/ascii.png';
  img.onload = () => {
    const cell = img.width / 16;
    const probe = document.createElement('canvas');
    probe.width = img.width; probe.height = img.height;
    const pctx = probe.getContext('2d');
    pctx.drawImage(img, 0, 0);
    const data = pctx.getImageData(0, 0, img.width, img.height).data;
    const widths = new Array(256).fill(0);
    for (let c = 0; c < 256; c++) {
      const cx = (c % 16) * cell, cy = Math.floor(c / 16) * cell;
      let w = 0;
      for (let x = cell - 1; x >= 0 && !w; x--)
        for (let y = 0; y < cell; y++)
          if (data[((cy + y) * img.width + cx + x) * 4 + 3] > 0) { w = x + 1; break; }
      widths[c] = c === 32 ? 4 : w;
    }
    document.querySelectorAll('[data-mc]').forEach(el => {
      const text = el.dataset.mc, scale = +(el.dataset.scale || 2), color = el.dataset.color || '#ffffff';
      const shadow = el.hasAttribute('data-shadow');
      const glyphW = [...text].map(ch => widths[ch.charCodeAt(0)] + 1);
      const w = glyphW.reduce((a, b) => a + b, 0) + (shadow ? 1 : 0), h = cell + (shadow ? 1 : 0);
      const cv = document.createElement('canvas');
      cv.width = w * scale; cv.height = h * scale;
      cv.style.width = cv.width + 'px'; cv.style.height = cv.height + 'px';
      cv.style.imageRendering = 'pixelated'; cv.style.verticalAlign = 'middle';
      const ctx = cv.getContext('2d');
      ctx.imageSmoothingEnabled = false;
      const tint = (hex, dx, dy) => {
        const off = document.createElement('canvas'); off.width = w; off.height = h;
        const o = off.getContext('2d'); let x = 0;
        [...text].forEach((ch, i) => {
          const c = ch.charCodeAt(0);
          o.drawImage(img, (c % 16) * cell, Math.floor(c / 16) * cell, cell, cell, x + dx, dy, cell, cell);
          x += glyphW[i];
        });
        o.globalCompositeOperation = 'source-in'; o.fillStyle = hex; o.fillRect(0, 0, w, h);
        ctx.drawImage(off, 0, 0, w * scale, h * scale);
      };
      if (shadow) tint(el.dataset.shadowColor || 'rgba(40,30,15,.9)', 1, 1);
      tint(color, 0, 0);
      el.replaceWith(cv);
    });
  };
})();
