"""Background removal for the two user-supplied images (EdgeORE Mine animation).
Pickaxe: flat dark background, removed by colour distance from the sampled background, limited to regions
connected to the border. Rock: smooth near-black gradient background, removed by local contrast (rock and soil are
textured), keeping the soil mound and fading it out at its edges."""
import sys
import numpy as np
from PIL import Image, ImageFilter
from scipy import ndimage

# usage: mine_cutout.py ROCK.jpg PICKAXE.jpg OUT_DIR   (needs Pillow, numpy, scipy; see docs/design/mine-animation.md)
ROCK, PICK, out = sys.argv[1], sys.argv[2], sys.argv[3]

def smooth(x, a, b):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)

def unpremult_dark(rgb, alpha, bg):
    # remove the dark background colour that bleeds into soft edges
    a = np.maximum(alpha[..., None], 1e-3)
    return np.clip((rgb - bg * (1 - a)) / a, 0, 255)

# --- pickaxe
im = np.asarray(Image.open(PICK).convert("RGB")).astype(np.float32)
bg = np.median(np.concatenate([im[:20].reshape(-1, 3), im[-20:].reshape(-1, 3)]), axis=0)
d = np.sqrt(((im - bg) ** 2).sum(-1))
alpha = smooth(d, 6, 26)
# only treat as background what is connected to the border (dark steel stays opaque)
lab, _ = ndimage.label(alpha < 0.5)
border = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
inner_holes = (alpha < 0.5) & ~np.isin(lab, list(border))
alpha[inner_holes] = 1
alpha = np.asarray(Image.fromarray((alpha * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2))).astype(np.float32) / 255
# keep only the pickaxe itself (drops a small corner mark in the source image)
lab, n = ndimage.label(alpha > 0.03)
sizes = ndimage.sum(np.ones_like(alpha), lab, range(1, n + 1))
alpha *= ndimage.binary_dilation(lab == (1 + int(np.argmax(sizes))), iterations=2)
rgb = unpremult_dark(im, alpha, bg)
Image.fromarray(np.dstack([rgb, alpha * 255]).astype(np.uint8), "RGBA").save(f"{out}/pickaxe_full.png")

# --- rock
im = np.asarray(Image.open(ROCK).convert("RGB")).astype(np.float32)
h, w, _ = im.shape
lum = im @ np.array([0.299, 0.587, 0.114], np.float32)
bgrow = np.median(np.concatenate([im[:, :12], im[:, -12:]], axis=1), axis=1)  # per-row background
bglum = bgrow @ np.array([0.299, 0.587, 0.114], np.float32)
# The background is a smooth gradient (brighter behind the rocks); rock and soil are textured. Use local contrast.
m1 = ndimage.uniform_filter(lum, 9)
std = np.sqrt(np.maximum(ndimage.uniform_filter(lum * lum, 9) - m1 * m1, 0))
mask = ndimage.gaussian_filter(std, 3.0) > 3.0
mask = ndimage.binary_closing(mask, structure=np.ones((31, 31)))
mask = ndimage.binary_fill_holes(mask)
lab, n = ndimage.label(mask)
if n:
    sizes = ndimage.sum(mask, lab, range(1, n + 1))
    mask = lab == (1 + int(np.argmax(sizes)))
mask = ndimage.binary_erosion(mask, iterations=2)
alpha = ndimage.gaussian_filter(mask.astype(np.float32), 2.5)
# fade the soil mound: elliptical vignette centred under the rocks, plus a bottom fade
yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
cx, cy = w * 0.5, h * 0.62
e = ((xx - cx) / (w * 0.47)) ** 2 + (np.maximum(yy - cy, 0) / (h * 0.36)) ** 2  # soil only; the rock tops are not faded
alpha *= 1 - smooth(e, 0.55, 1.0)
alpha *= 1 - smooth(yy, h * 0.80, h * 0.93)
alpha = np.clip(ndimage.gaussian_filter(alpha, 1.0), 0, 1)
rgb = unpremult_dark(im, alpha, np.array([12, 12, 11], np.float32))
Image.fromarray(np.dstack([rgb, alpha * 255]).astype(np.uint8), "RGBA").save(f"{out}/rock_full.png")
print("ok")
