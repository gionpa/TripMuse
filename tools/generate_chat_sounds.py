#!/usr/bin/env python3
"""
채팅 수신음을 합성해서 android/app/src/main/res/raw/ 에 넣는다.

    python3 tools/generate_chat_sounds.py

의존성은 파이썬 표준 라이브러리와 ffmpeg(OGG 인코딩)뿐이다. 음색을 바꾸고 싶으면
아래 "소리 정의" 블록의 주파수·감쇠·배음만 손대면 된다.

설계 기준 — 짧고(0.4~1.0초), 높고(1kHz 이상), 올라가는 음정. 셋 다 "귀엽다"는
인상을 만드는 요소다. 소리끼리 체감 음량을 맞춰서, 설정에서 바꿔 들어도
갑자기 커지거나 작아지지 않는다.
"""
import math
import os
import random
import struct
import subprocess
import sys
import wave

SR = 44100
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW_DIR = os.path.join(ROOT, "android", "app", "src", "main", "res", "raw")

# 소리끼리 맞출 체감 음량 (200ms 창의 최대 RMS)
TARGET_LOUDNESS = 0.145


def buf(seconds):
    return [0.0] * int(SR * seconds)


def add_tone(b, t0, dur, f0, f1=None, amp=1.0, attack=0.004, decay=None,
             partials=((1.0, 1.0, 1.0),), vib_hz=0.0, vib_depth=0.0,
             pitch_curve="exp"):
    """partials: (배음비, 진폭비, 감쇠배율). 높은 배음일수록 감쇠배율을 낮춰 먼저 사라지게 한다."""
    f1 = f0 if f1 is None else f1
    decay = decay if decay else dur * 0.42
    i0 = int(t0 * SR)
    n = int(dur * SR)
    atk = max(1, int(attack * SR))
    for ratio, pamp, pdec in partials:
        phase = 0.0
        d = decay * pdec
        for i in range(n):
            idx = i0 + i
            if idx >= len(b):
                break
            t = i / SR
            x = t / dur
            if pitch_curve == "exp":
                f = f0 * (f1 / f0) ** min(1.0, x * 3.0)
            else:
                f = f0 + (f1 - f0) * min(1.0, x * 3.0)
            if vib_hz:
                f *= 1.0 + vib_depth * math.sin(2 * math.pi * vib_hz * t)
            phase += 2 * math.pi * f * ratio / SR
            a = (i / atk) if i < atk else math.exp(-t / d)
            b[idx] += math.sin(phase) * a * pamp * amp


def add_noise(b, t0, dur, amp, decay, lp=0.35):
    """나무 두드리는 소리용 저역통과 노이즈 버스트"""
    i0, n = int(t0 * SR), int(dur * SR)
    atk = max(1, int(0.0015 * SR))       # 없으면 시작부터 값이 튀어 딱 소리가 난다
    prev = 0.0
    rnd = random.Random(7)               # 빌드할 때마다 같은 소리가 나오도록 고정
    for i in range(n):
        idx = i0 + i
        if idx >= len(b):
            break
        w = rnd.uniform(-1, 1)
        prev += lp * (w - prev)
        a = (i / atk) if i < atk else 1.0
        b[idx] += prev * amp * a * math.exp(-(i / SR) / decay)


def reverb(b, wet=0.16, room=0.5):
    """슈뢰더 리버브 — 짧은 공간감만 살짝 얹는다"""
    out = list(b)
    for dms, fb in ((13.7, room), (17.3, room * 0.95), (19.9, room * 0.9), (23.1, room * 0.85)):
        d = int(SR * dms / 1000)
        line = [0.0] * d
        p = 0
        for i in range(len(b)):
            y = line[p]
            line[p] = b[i] + y * fb
            p = (p + 1) % d
            out[i] += y * wet
    for dms, g in ((5.0, 0.5), (1.7, 0.5)):
        d = int(SR * dms / 1000)
        line = [0.0] * d
        p = 0
        for i in range(len(out)):
            y = line[p]
            v = out[i] + y * g
            line[p] = v
            out[i] = y - v * g
            p = (p + 1) % d
    return out


def loudness(b, win=0.2):
    """200ms 창의 최대 RMS — 짧은 소리의 체감 음량에 가깝다"""
    w = int(win * SR)
    if len(b) <= w:
        return math.sqrt(sum(v * v for v in b) / len(b))
    acc = sum(v * v for v in b[:w])
    best = acc
    for i in range(w, len(b)):
        acc += b[i] * b[i] - b[i - w] * b[i - w]
        best = max(best, acc)
    return math.sqrt(best / w)


def trim_tail(b, floor_db=-55.0, tail=0.015):
    """들리지 않는 꼬리를 잘라 파일 크기를 줄인다"""
    peak = max(abs(v) for v in b) or 1.0
    thr = peak * 10 ** (floor_db / 20)
    for i in range(len(b) - 1, 0, -1):
        if abs(b[i]) > thr:
            return b[:min(len(b), i + int(tail * SR))]
    return b


def finish(b, gain, fade_in=0.002, fade_out=0.02):
    b = trim_tail(b)
    n = len(b)
    fi, fo = int(fade_in * SR), int(fade_out * SR)
    for i in range(fi):
        b[i] *= i / fi                                       # 시작 클릭 제거
    for i in range(fo):
        b[n - fo + i] *= 1.0 - i / fo                        # 끝 클릭 제거
    return [math.tanh(v * gain * 1.15) * 0.92 for v in b]    # 부드러운 리미팅


def write_wav(path, samples):
    with wave.open(path, "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b"".join(
            struct.pack("<h", int(max(-1.0, min(1.0, v)) * 32767)) for v in samples
        ))


# 배음 프리셋
MARIMBA = ((1.0, 1.0, 1.0), (4.0, 0.30, 0.45), (10.0, 0.10, 0.25))
KALIMBA = ((1.0, 1.0, 1.0), (2.0, 0.35, 0.6), (3.01, 0.18, 0.4), (5.4, 0.07, 0.25))
BELL = ((1.0, 1.0, 1.0), (2.76, 0.45, 0.6), (5.4, 0.22, 0.35), (8.9, 0.10, 0.2))
SOFT = ((1.0, 1.0, 1.0), (2.0, 0.12, 0.5))


def build_sounds():
    """소리 정의 — 여기만 고치면 음색이 바뀐다"""
    sounds = {}

    # 뽀로롱 — 칼림바 3음 상행 (기본값)
    b = buf(0.9)
    for i, f in enumerate((1046.5, 1318.5, 1568.0)):          # C6 E6 G6
        add_tone(b, i * 0.07, 0.75, f, amp=0.85 - i * 0.08,
                 decay=0.22 - i * 0.03, partials=KALIMBA)
    sounds["bboroong"] = reverb(b, wet=0.20)

    # 물방울 — 위로 미끄러지는 물방울
    b = buf(0.9)
    add_tone(b, 0.0, 0.5, 620, 2350, amp=1.0, attack=0.002, decay=0.075, partials=SOFT)
    add_tone(b, 0.0, 0.5, 310, 1100, amp=0.35, attack=0.002, decay=0.05, partials=SOFT)
    add_tone(b, 0.055, 0.6, 1975, amp=0.16, decay=0.16, partials=SOFT)
    sounds["water"] = reverb(b, wet=0.30, room=0.6)

    # 반짝 — 종 두 번
    b = buf(1.0)
    add_tone(b, 0.0, 0.9, 1318.5, amp=0.9, decay=0.23, partials=BELL)      # E6
    add_tone(b, 0.11, 0.85, 1975.5, amp=0.7, decay=0.26, partials=BELL)    # B6
    add_tone(b, 0.11, 0.45, 3951.0, amp=0.10, decay=0.11, partials=SOFT)
    sounds["twinkle"] = reverb(b, wet=0.26, room=0.62)

    # 짹짹 — 새소리 두 번
    b = buf(0.8)
    add_tone(b, 0.0, 0.16, 1500, 2600, amp=0.8, attack=0.006, decay=0.055,
             partials=((1.0, 1.0, 1.0), (2.0, 0.25, 0.5), (3.0, 0.08, 0.3)),
             vib_hz=38, vib_depth=0.035)
    add_tone(b, 0.13, 0.22, 2100, 1750, amp=0.7, attack=0.006, decay=0.07,
             partials=((1.0, 1.0, 1.0), (2.0, 0.22, 0.5)),
             vib_hz=32, vib_depth=0.03, pitch_curve="lin")
    sounds["chirp"] = reverb(b, wet=0.22)

    # 뿅 — 레트로 게임 팝
    b = buf(0.7)
    add_tone(b, 0.0, 0.28, 480, 1560, amp=0.85, attack=0.003, decay=0.06,
             partials=((1.0, 1.0, 1.0), (3.0, 0.30, 0.5), (5.0, 0.13, 0.35), (7.0, 0.05, 0.25)))
    add_tone(b, 0.10, 0.35, 2093, amp=0.22, decay=0.10, partials=KALIMBA)
    sounds["pop"] = reverb(b, wet=0.18)

    # 톡톡 — 나무 노크 + 마림바. 조용한 자리에서도 튀지 않는 쪽
    b = buf(0.9)
    add_noise(b, 0.0, 0.05, amp=0.55, decay=0.011)
    add_tone(b, 0.005, 0.5, 880, amp=0.55, decay=0.13, partials=MARIMBA)
    add_tone(b, 0.085, 0.6, 1174.7, amp=0.45, decay=0.16, partials=MARIMBA)   # D6
    sounds["knock"] = reverb(b, wet=0.17)

    return sounds


def main():
    if subprocess.run(["which", "ffmpeg"], capture_output=True).returncode != 0:
        sys.exit("ffmpeg이 필요합니다: brew install ffmpeg")

    os.makedirs(RAW_DIR, exist_ok=True)
    tmp_wav = os.path.join(RAW_DIR, "_tmp.wav")

    for name, raw in build_sounds().items():
        peak = max(abs(v) for v in raw) or 1.0
        gain = min(TARGET_LOUDNESS / (loudness(raw) or 1.0), 0.95 / peak)
        samples = finish(raw, gain)
        write_wav(tmp_wav, samples)

        ogg = os.path.join(RAW_DIR, f"chat_{name}.ogg")
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error", "-i", tmp_wav,
             "-c:a", "libvorbis", "-q:a", "6", "-ar", str(SR), "-ac", "1", ogg],
            check=True,
        )
        print(f"chat_{name}.ogg  {len(samples)/SR:.2f}초  {os.path.getsize(ogg)/1024:.1f}KB")

    os.remove(tmp_wav)
    print(f"\n→ {RAW_DIR}")


if __name__ == "__main__":
    main()
