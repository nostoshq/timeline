#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 NOSTOS, LLC
"""Timeline's interface sounds, made from scratch so there is nothing to license:
    python3 tools/make_sounds.py   -> app/src/main/res/raw/ui_move.wav, ui_move_back.wav, ui_edge.wav, ui_start.wav, ui_open.wav, ui_back.wav
move  a short, soft tick for each step of the highlight down or right; move_back the same a minor third lower, for up
      or left (the launcher also leans each a little toward its side: right for forward, left for back)
open  a small rising note for going into something (a page, a game)
back  the same note falling, for coming back out
edge  a low, soft knock when a list has no further to go
start two notes a fifth apart, for a game starting
All quiet on purpose (the loudest is about a fifth of full scale) and short, so a held direction never stacks up."""
import math, os, struct, wave

RATE = 48000
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "raw")


def note(f0, f1, ms, attack_ms, tau_ms, amp, partials=((1, 1.0),)):
    n = int(RATE * ms / 1000)
    out, phase = [], 0.0
    for i in range(n):
        t = i / RATE
        f = f0 * (f1 / f0) ** (i / max(n - 1, 1))               # an even glide in pitch
        phase += 2 * math.pi * f / RATE
        env = min(1.0, t * 1000 / attack_ms) * math.exp(-t * 1000 / tau_ms)
        tail = min(1.0, (n - i) / (RATE * 0.004))                 # the last 4 ms fade to nothing: no click at the end
        out.append(amp * env * tail * sum(a * math.sin(k * phase) for k, a in partials) / sum(a for _, a in partials))
    return out


def write(name, samples):
    os.makedirs(OUT, exist_ok=True)
    with wave.open(os.path.join(OUT, name), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s)) * 32767)) for s in samples))
    print("%-14s %4d ms  peak %.2f" % (name, len(samples) * 1000 // RATE, max(abs(s) for s in samples)))


write("ui_move.wav", note(1500, 1500, 24, 0.5, 4.5, 0.20, ((1, 1.0), (2, 0.35), (3, 0.1))))
write("ui_move_back.wav", note(1500 / 2 ** (3 / 12), 1500 / 2 ** (3 / 12), 26, 0.5, 5.0, 0.20, ((1, 1.0), (2, 0.35), (3, 0.1))))
write("ui_edge.wav", note(560, 520, 36, 0.8, 9.0, 0.13, ((1, 1.0), (2, 0.2))))
# A game starting: two notes a fifth apart, the second a little later and fuller, so it reads as an occasion.
start = note(660, 660, 230, 2.0, 70, 0.14, ((1, 1.0), (2, 0.25), (3, 0.08)))
second = note(990, 990, 320, 2.0, 110, 0.15, ((1, 1.0), (2, 0.25), (3, 0.08)))
lag = int(RATE * 0.085)
start = start + [0.0] * (lag + len(second) - len(start))
for i, s in enumerate(second):
    start[lag + i] += s
write("ui_start.wav", start)
write("ui_open.wav", note(880, 1320, 110, 2.0, 38, 0.17, ((1, 1.0), (2, 0.18))))
write("ui_back.wav", note(1175, 784, 110, 2.0, 38, 0.15, ((1, 1.0), (2, 0.18))))
