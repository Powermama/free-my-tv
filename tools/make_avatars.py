"""Draws the profile avatars with DiceBear (dicebear.com), once, into app/assets/avatars. Needs Node (npx).
Usage: python3 tools/make_avatars.py

The pictures are committed, so the build and the TV never run or fetch DiceBear. Their licences go in LICENSE.txt
beside them (and in the README): Thumbs, Lorelei and Notionists are CC0; Fun Emoji and Big Smile are CC BY 4.0.
"""
import os
import shutil
import subprocess
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app/assets/avatars")
DICEBEAR = "dicebear@9.4.3"
SIZE = 256
# Soft backgrounds that read on the light and the dark home screen.
BACKGROUNDS = ["ffd5dc", "ffdfbf", "fff1b8", "d1f4d9", "c0e8f9", "d9d4ff"]
SEEDS = ["Otter", "Pancake", "Comet", "Mango", "Pixel", "Biscuit"]
# A style's own seeds where a default one came out glum.
STYLE_SEEDS = {"notionists": ["Otter", "Pancake", "Comet", "Mango", "Pixel", "Waffle"]}
LICENSE = """Profile avatars, made with DiceBear (https://www.dicebear.com, MIT) by tools/make_avatars.py.

funEmoji-*: "Fun Emoji" by Davis Uche, CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/).
bigSmile-*: "Big Smile" by Ashley Seo, CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/).
thumbs-*: "Thumbs" by DiceBear, CC0 1.0.
lorelei-*: "Lorelei" by Lisa Wischofsky, CC0 1.0.
notionists-*: "Notionists" by Zoish, CC0 1.0.
Colors and expressions chosen for Free My TV; otherwise as DiceBear draws them.
"""
# Only happy faces: these are the choices each style offers (dicebear <style> --help).
STYLES = {
    "funEmoji": ["--mouth", "lilSmile", "cute", "wideSmile", "smileTeeth", "smileLol", "tongueOut", "kissHeart",
                 "--eyes", "cute", "wink", "wink2", "plain", "love", "stars", "shades", "closed"],
    "bigSmile": ["--mouth", "openedSmile", "gapSmile", "teethSmile", "kawaii"],
    "thumbs": ["--scale", "80"],   # the shape fills its square: smaller, so it sits inside the circle
    "lorelei": ["--mouth"] + ["happy%02d" % i for i in range(1, 19)],
    "notionists": [],
}


def main():
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(OUT)
    with tempfile.TemporaryDirectory() as tmp:
        for style, options in STYLES.items():
            for i, seed in enumerate(STYLE_SEEDS.get(style, SEEDS)):
                folder = os.path.join(tmp, style + seed)
                subprocess.run(["npx", "--yes", DICEBEAR, style, folder, "--seed", seed, "--format", "png",
                                "--size", str(SIZE), "--backgroundColor", BACKGROUNDS[i % len(BACKGROUNDS)]] + options,
                               check=True, stdout=subprocess.DEVNULL)
                made = os.listdir(folder)[0]
                shutil.copy(os.path.join(folder, made), os.path.join(OUT, "%s-%s.png" % (style, seed.lower())))
    with open(os.path.join(OUT, "LICENSE.txt"), "w") as f:
        f.write(LICENSE)
    total = sum(os.path.getsize(os.path.join(OUT, f)) for f in os.listdir(OUT))
    print("%d avatars, %d KB, in %s" % (len(os.listdir(OUT)) - 1, total // 1024, os.path.relpath(OUT, ROOT)))


if __name__ == "__main__":
    main()
