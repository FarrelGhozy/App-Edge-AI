#!/bin/bash
# Download YOLOv8 Face TFLite model untuk FaceGateApp
# Letakkan di android/core/src/main/assets/
# Jalankan: bash scripts/download-yolo-model.sh

MODEL_DIR="$(dirname "$0")/../android/core/src/main/assets"
MODEL_NAME="yolov8n_face.tflite"
MODEL_PATH="$MODEL_DIR/$MODEL_NAME"

echo "📥 Download YOLOv8 Face TFLite model..."
mkdir -p "$MODEL_DIR"

# Multiple source URLs — coba sampai ada yang berhasil
URLS=(
  # Hugging Face — arnabdhar model (paling reliable untuk face detection)
  "https://huggingface.co/arnabdhar/YOLOv8-Face-Detection/resolve/main/yolov8n_face.tflite"
  # Backup — model dari Bingsu
  "https://huggingface.co/Bingsu/yolov8n-face-tflite/resolve/main/yolov8n_face_int8.tflite"
)

for URL in "${URLS[@]}"; do
  echo "  Mencoba: $URL"
  HTTP_CODE=$(curl -sL -o "$MODEL_PATH" -w "%{http_code}" "$URL" --max-time 60)
  if [ "$HTTP_CODE" = "200" ] && [ -s "$MODEL_PATH" ]; then
    FILE_SIZE=$(stat -c%s "$MODEL_PATH" 2>/dev/null || stat -f%z "$MODEL_PATH" 2>/dev/null)
    if [ "$FILE_SIZE" -gt 1000000 ]; then
      echo "✅ Berhasil! Model: $MODEL_NAME ($(( FILE_SIZE / 1000000 )) MB)"
      echo "  Lokasi: $MODEL_PATH"
      exit 0
    fi
  fi
  echo "  Gagal (HTTP $HTTP_CODE), coba URL berikutnya..."
done

# Jika semua gagal, beri instruksi manual
rm -f "$MODEL_PATH"
echo "⚠️  Semua URL gagal. Download manual:"
echo "   1. Buka https://github.com/ultralytics/ultralytics"
echo "   2. Jalankan: yolo export model=yolov8n-face.pt format=tflite"
echo "   3. Copy yolov8n_face.tflite ke $MODEL_DIR/"
echo ""
echo "Atau gunakan Google Colab untuk export:"
echo "   https://colab.research.google.com/drive/1x1xJQ0tMx1jVnEGGtYR1aUyLxFZ_YvZn"
exit 1
