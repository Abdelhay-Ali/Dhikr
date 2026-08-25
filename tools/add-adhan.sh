#!/usr/bin/env bash
# إضافة أصوات الأذان إلى التطبيق.
#
#   ./tools/add-adhan.sh makkah ~/Downloads/makkah.mp3
#   ./tools/add-adhan.sh fajr_madinah ~/Downloads/fajr.mp3
#
# الأسماء المعتمدة (تظهر في التطبيق تلقائيًا بمجرد وجود الملف):
#   makkah  madinah  masr  aqsa  qatar  short
#   fajr_makkah  fajr_madinah  fajr_masr
#
# يتطلّب ffmpeg.

set -euo pipefail

NAME="${1:-}"
SRC="${2:-}"
OUT_DIR="$(dirname "$0")/../app/src/main/res/raw"

if [[ -z "$NAME" || -z "$SRC" ]]; then
  echo "الاستعمال: $0 <الاسم> <ملف الصوت>" >&2
  exit 1
fi

case "$NAME" in
  makkah|madinah|masr|aqsa|qatar|short|fajr_makkah|fajr_madinah|fajr_masr) ;;
  *) echo "اسم غير معتمد: $NAME" >&2; exit 1 ;;
esac

command -v ffmpeg >/dev/null || { echo "ffmpeg غير مثبَّت" >&2; exit 1; }

mkdir -p "$OUT_DIR"
DEST="$OUT_DIR/adhan_${NAME}.mp3"

# صوت بشري أحادي القناة بـ64 kbps يكفي تمامًا: جودة سماع ممتازة وحجم صغير.
# أذان مدّته ثلاث دقائق ينزل من نحو ٧ ميغابايت إلى ١٫٤ تقريبًا.
ffmpeg -hide_banner -loglevel error -y \
  -i "$SRC" \
  -ac 1 -ar 44100 -b:a 64k \
  -af "silenceremove=start_periods=1:start_threshold=-50dB, loudnorm=I=-16:TP=-1.5" \
  "$DEST"

SIZE=$(du -h "$DEST" | cut -f1)
echo "تم: $DEST ($SIZE)"
echo "أعد بناء التطبيق ليظهر الخيار في شاشة المواقيت."
