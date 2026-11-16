"""Synthesises the game's placeholder sound effects into app/src/main/res/raw.

Run from the repo root:  python3 tools/make_sounds.py
Everything is generated from sine waves and noise, so there are no licences to track.
"""
import os
import wave

import numpy as np

RATE = 22050
OUT = os.path.join("app", "src", "main", "res", "raw")
rng = np.random.default_rng(7)


def t(seconds):
    return np.arange(int(RATE * seconds)) / RATE


def sweep(seconds, f0, f1, shape="sine"):
    x = t(seconds)
    freq = f0 * (f1 / f0) ** (x / seconds)
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    if shape == "saw":
        return 2 * ((phase / (2 * np.pi)) % 1.0) - 1
    if shape == "square":
        return np.sign(np.sin(phase))
    return np.sin(phase)


def noise(seconds, smooth=1):
    n = rng.uniform(-1, 1, int(RATE * seconds))
    if smooth > 1:
        n = np.convolve(n, np.ones(smooth) / smooth, mode="same") * np.sqrt(smooth) * 0.7
    return n


def decay(seconds, rate):
    return np.exp(-rate * t(seconds))


def fade(sig, attack=0.005, release=0.02):
    a = int(RATE * attack)
    r = int(RATE * release)
    env = np.ones(len(sig))
    if a:
        env[:a] = np.linspace(0, 1, a)
    if r:
        env[-r:] *= np.linspace(1, 0, r)
    return sig * env


def notes(freqs, each, shape="sine", rate=6.0):
    parts = [sweep(each, f, f, shape) * decay(each, rate) for f in freqs]
    return np.concatenate([fade(p, 0.004, 0.01) for p in parts])


def save(name, sig, volume=0.8):
    sig = fade(np.asarray(sig, dtype=float))
    peak = np.max(np.abs(sig)) or 1.0
    pcm = (sig / peak * volume * 32767).astype("<i2")
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())


os.makedirs(OUT, exist_ok=True)

save("sfx_zap", (sweep(0.2, 2400, 260, "saw") * 0.6 + noise(0.2) * 0.7) * decay(0.2, 16))
save("sfx_boom", sweep(0.7, 110, 36) * decay(0.7, 5) + noise(0.7, 24) * decay(0.7, 7) * 0.9, 0.95)
save("sfx_whoosh", noise(0.55, 10) * np.sin(np.pi * t(0.55) / 0.55) ** 1.5, 0.6)
save("sfx_shield", (sweep(0.45, 660, 680) + sweep(0.45, 990, 1010) * 0.6) * np.sin(np.pi * t(0.45) / 0.45), 0.55)
save("sfx_smite", sweep(0.11, 880, 1500) * decay(0.11, 14), 0.5)
save("sfx_loss", sweep(0.4, 240, 105) * decay(0.4, 6), 0.75)
save("sfx_convert", notes([660, 880, 1175], 0.11), 0.55)
save("sfx_crumble", noise(0.9, 18) * decay(0.9, 3.5) * (0.55 + 0.45 * np.abs(noise(0.9, 400))) + sweep(0.9, 70, 40) * decay(0.9, 4) * 0.6, 0.9)
save("sfx_warn", np.concatenate([fade(sweep(0.1, 520, 520, "square")), np.zeros(int(RATE * 0.05)), fade(sweep(0.12, 520, 520, "square"))]), 0.35)
save("sfx_horn", (sweep(0.75, 196, 196, "saw") + sweep(0.75, 294, 294, "saw") * 0.7) * np.minimum(1, t(0.75) * 12) * decay(0.75, 1.6), 0.5)
save("sfx_deny", sweep(0.14, 150, 120, "square") * decay(0.14, 8), 0.3)
save("sfx_win", notes([523, 659, 784, 1047], 0.17, rate=4.0), 0.6)
save("sfx_lose", notes([392, 330, 262, 196], 0.2, rate=3.5), 0.6)
print("wrote", len(os.listdir(OUT)), "files to", OUT)
