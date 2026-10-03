#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Compile the exact production filter to DEX and run it on the connected Android 15 emulator."""
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "build" / "sense-completion-smoke"
CLASSES = OUTPUT / "classes"
CLASSES.mkdir(parents=True, exist_ok=True)
SDK = Path(os.environ["ANDROID_HOME"])
subprocess.run([
    "javac", "--release", "8", "-encoding", "UTF-8", "-d", str(CLASSES),
    str(ROOT / "app/src/main/java/helium314/keyboard/latin/completion/SenseCompletionQuality.java"),
    str(ROOT / "tools/android-smoke/SenseCompletionAndroidSmoke.java"),
], check=True)
JAR = OUTPUT / "classes.jar"
subprocess.run(["jar", "cf", str(JAR), "-C", str(CLASSES), "."], check=True)
subprocess.run([
    str(SDK / "build-tools/35.0.0/d8"), "--min-api", "26", "--lib",
    str(SDK / "platforms/android-35/android.jar"), "--output", str(OUTPUT), str(JAR),
], check=True)
REMOTE = "/data/local/tmp/sense-completion-smoke.dex"
subprocess.run(["adb", "push", str(OUTPUT / "classes.dex"), REMOTE], check=True)
result = subprocess.run([
    "adb", "shell", f"CLASSPATH={REMOTE} app_process / SenseCompletionAndroidSmoke",
], check=True, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
print(result.stdout, end="")
if "PASS: production completion filter, 6 cases" not in result.stdout:
    raise SystemExit("Android completion smoke test did not finish")
