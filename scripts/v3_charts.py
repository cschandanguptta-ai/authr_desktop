#!/usr/bin/env python3
"""Charts for Authr OS 3.0 Improvement Plan (Rev 2). GO-1 Graphite Orange palette."""
import os
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "v3_assets")
os.makedirs(OUT, exist_ok=True)

ACCENT = "#D4875A"      # GO-1 accent
NEUTRAL = "#9AA6B2"     # non-emphasis grey
TEXT = "#333333"
GRID = "#E0E0E0"

plt.rcParams["axes.unicode_minus"] = False


def save_chart(fig, path, dpi=200):
    fig.savefig(path, dpi=dpi, bbox_inches="tight", pad_inches=0.1,
                facecolor="white", edgecolor="none")
    plt.close(fig)
    return path


# ── Figure 1: Pocket TTS synthesis speed (x real time) ──────────────────────
def chart_rtf():
    cats = [
        "Small 18.2M\n1 CPU thread",
        "Main 100M\nApple M4 CPU",
        "Main 100M\nTesla T4 GPU",
        "Main 100M\nx86 laptop CPU",
    ]
    vals = [25.0, 6.0, 6.28, 2.4]
    labels = ["up to 25x", "~6x", "~6.3x", "~2.4x"]

    fig, ax = plt.subplots(figsize=(10, 6))
    bars = ax.bar(cats, vals, color=[ACCENT, ACCENT, ACCENT, ACCENT],
                  width=0.6, edgecolor="white")
    for bar, lab in zip(bars, labels):
        ax.text(bar.get_x() + bar.get_width() / 2, bar.get_height() + 0.5,
                lab, ha="center", va="bottom", fontsize=11, color=TEXT)
    ax.axhline(1.0, color="#B87472", linewidth=1.4, linestyle="--", alpha=0.8)
    ax.text(3.42, 1.35, "real time (1x)", fontsize=9, color="#B87472", ha="right")
    ax.set_title("Pocket TTS Synthesis Speed by Model and Hardware",
                 fontsize=14, pad=15, color=TEXT)
    ax.set_ylabel("Speed relative to real time (x)", fontsize=11, color=TEXT)
    ax.set_ylim(0, 28)
    ax.tick_params(axis="x", labelsize=10)
    ax.spines[["top", "right"]].set_visible(False)
    ax.grid(axis="y", alpha=0.3, color=GRID)
    return save_chart(fig, os.path.join(OUT, "fig1_pocket_tts_rtf.png"))


# ── Figure 2: Roadmap effort by phase ────────────────────────────────────────
def chart_effort():
    cats = ["P0\nFoundation", "P1\nEngine Core", "P2\nProse Quality",
            "P3\nSerialization", "P4\nAudio", "P5\nPublishing"]
    vals = [4, 6, 5, 6, 6, 5]
    colors = [NEUTRAL, NEUTRAL, NEUTRAL, ACCENT, NEUTRAL, NEUTRAL]

    fig, ax = plt.subplots(figsize=(10, 6))
    bars = ax.bar(cats, vals, color=colors, width=0.6, edgecolor="white")
    for bar, val in zip(bars, vals):
        ax.text(bar.get_x() + bar.get_width() / 2, bar.get_height() + 0.08,
                f"{val} wk", ha="center", va="bottom", fontsize=11, color=TEXT)
    ax.set_title("v3.0 Roadmap Effort by Phase (Total: 32 Person-Weeks)",
                 fontsize=14, pad=15, color=TEXT)
    ax.set_ylabel("Person-weeks", fontsize=11, color=TEXT)
    ax.set_ylim(0, 7.5)
    ax.tick_params(axis="x", labelsize=10)
    ax.spines[["top", "right"]].set_visible(False)
    ax.grid(axis="y", alpha=0.3, color=GRID)
    ax.text(0.99, 0.97, "P3 (highlighted) = Web Novel Serialization,\npromoted from post-P5 into the main plan in Rev 2",
            transform=ax.transAxes, fontsize=9, color="#8A6A52", ha="right", va="top")
    return save_chart(fig, os.path.join(OUT, "fig2_roadmap_effort.png"))


if __name__ == "__main__":
    print(chart_rtf())
    print(chart_effort())
    print("done")
