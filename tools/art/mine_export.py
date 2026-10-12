"""usage: mine_export.py CUTOUT_DIR app/src/main/res/drawable-nodpi
Crop, scale and export the cut-outs as WebP drawables for app/src/main/res/drawable-nodpi/."""
import sys, json
import numpy as np
from PIL import Image
src, res = sys.argv[1], sys.argv[2]
meta = {}

def crop(im, pad=6):
    a = np.asarray(im.split()[3])
    ys, xs = np.where(a > 8)
    box = (max(xs.min() - pad, 0), max(ys.min() - pad, 0), min(xs.max() + pad + 1, im.width), min(ys.max() + pad + 1, im.height))
    return im.crop(box), box

rock, rbox = crop(Image.open(f"{src}/rock_full.png"))
pick, pbox = crop(Image.open(f"{src}/pickaxe_full.png").transpose(Image.FLIP_LEFT_RIGHT))  # head up-left, handle end down-right

RW = 640
rock_s = rock.resize((RW, round(rock.height * RW / rock.width)), Image.LANCZOS)
rock_s.save(f"{res}/mine_rock.webp", "WEBP", quality=82, method=6, alpha_quality=90)

# Vein glow mask: the mint veins of the rock as a white-on-transparent layer, tinted and pulsed in Compose.
a = np.asarray(rock_s).astype(np.float32)
r, g, b, al = a[..., 0], a[..., 1], a[..., 2], a[..., 3] / 255
mintness = np.clip((g - np.maximum(r, b * 0.6) - 25) / 80, 0, 1) * al
from scipy import ndimage
mintness = np.clip(ndimage.gaussian_filter(mintness, 2.5) * 1.6, 0, 1)
veins = np.zeros_like(a); veins[..., :3] = 255; veins[..., 3] = mintness * 255
vs = Image.fromarray(veins.astype(np.uint8), "RGBA").resize((RW // 2, rock_s.height // 2), Image.LANCZOS)
vs.save(f"{res}/mine_rock_veins.webp", "WEBP", quality=70, method=6, alpha_quality=70)

PW = 420
pick_s = pick.resize((PW, round(pick.height * PW / pick.width)), Image.LANCZOS)
pick_s.save(f"{res}/mine_pickaxe.webp", "WEBP", quality=82, method=6, alpha_quality=90)
meta = {"rock": rock_s.size, "veins": vs.size, "pickaxe": pick_s.size, "rock_crop": rbox, "pickaxe_crop_mirrored": pbox}
print(json.dumps(meta, default=int))
