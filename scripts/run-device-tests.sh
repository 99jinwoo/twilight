#!/usr/bin/env bash
set -euo pipefail
adb install -r built/outputs/apk/debug/app-debug.apk
adb install -r built/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.jinwoo.twilightandyou.test/androidx.test.runner.AndroidJUnitRunner | tee instrumentation.log
adb pull /sdcard/Android/data/com.jinwoo.twilightandyou/files/screenshots screenshots || true
# Sample-only screenshots are also available to tooling that can read job logs.
python3 - <<'PY'
import base64, json
from pathlib import Path
for p in sorted(Path('screenshots').glob('*.png')):
    print('TWILIGHT_PREVIEW ' + json.dumps({'name': p.name, 'png': base64.b64encode(p.read_bytes()).decode()}))
PY
grep -Eq '^OK \([0-9]+ tests?\)' instrumentation.log
