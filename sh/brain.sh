#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
URL="http://10.8.21.70:8081/login"
JAR_PATH="/home/nvidia/YDS/project/brain/springboot/springb-0.0.1-SNAPSHOT.jar"
PY_DIR="/home/nvidia/YDS/py"
CONDA_ENV="v11dmt"
LOG_DIR="/home/nvidia/YDS/start-bash/brain/log"

SPRING_LOG="$LOG_DIR/springb.log"
UNET_LOG="$LOG_DIR/unet4engine.log"
MESH_LOG="$LOG_DIR/mesh-api.log"
DETECT_LOG="$LOG_DIR/detect-api.log"

PIDS=()
CLEANED_UP=0

cleanup() {
  if [[ "$CLEANED_UP" -eq 1 ]]; then
    return
  fi
  CLEANED_UP=1

  echo
  echo "[brain.sh] Stopping services..."
  for pid in "${PIDS[@]:-}"; do
    if kill -0 "$pid" 2>/dev/null; then
      kill -- -"$pid" 2>/dev/null || true
    fi
  done
  wait || true
  echo "[brain.sh] All services stopped."
}

trap cleanup EXIT INT TERM HUP

start_service() {
  local name="$1"
  local cmd="$2"

  echo "[brain.sh] Starting ${name}..."
  setsid bash -c "$cmd" &
  local pid=$!
  PIDS+=("$pid")
  echo "[brain.sh] ${name} PID=${pid}"
}

open_browser() {
  echo "[brain.sh] Opening browser: $URL"

  if command -v chromium-browser >/dev/null 2>&1; then
    chromium-browser --incognito --new-window "$URL" >/dev/null 2>&1 &
    return 0
  fi

  if command -v google-chrome >/dev/null 2>&1; then
    google-chrome --incognito --new-window "$URL" >/dev/null 2>&1 &
    return 0
  fi

  if command -v firefox >/dev/null 2>&1; then
    firefox --private-window "$URL" >/dev/null 2>&1 &
    return 0
  fi

  if command -v xdg-open >/dev/null 2>&1; then
    xdg-open "$URL" >/dev/null 2>&1 &
    return 0
  fi

  echo "[brain.sh] Browser open failed. Please open manually: $URL"
}

mkdir -p "$SCRIPT_DIR" "$LOG_DIR"

if ! command -v java >/dev/null 2>&1; then
  echo "[brain.sh] java not found."
  exit 1
fi

CONDA_SH=""
if command -v conda >/dev/null 2>&1; then
  CONDA_BASE="$(conda info --base 2>/dev/null || true)"
  if [[ -n "$CONDA_BASE" && -f "$CONDA_BASE/etc/profile.d/conda.sh" ]]; then
    CONDA_SH="$CONDA_BASE/etc/profile.d/conda.sh"
  fi
fi

if [[ -z "$CONDA_SH" ]]; then
  for candidate in \
    "/home/nvidia/miniconda3/etc/profile.d/conda.sh" \
    "/home/nvidia/anaconda3/etc/profile.d/conda.sh" \
    "/opt/miniconda3/etc/profile.d/conda.sh"
  do
    if [[ -f "$candidate" ]]; then
      CONDA_SH="$candidate"
      break
    fi
  done
fi

if [[ -z "$CONDA_SH" ]]; then
  echo "[brain.sh] conda.sh not found. Please update the script."
  exit 1
fi

for file in \
  "$JAR_PATH" \
  "$PY_DIR/unet4engine.py" \
  "$PY_DIR/mesh-api.py" \
  "$PY_DIR/detect-api.py"
do
  if [[ ! -f "$file" ]]; then
    echo "[brain.sh] File not found: $file"
    exit 1
  fi
done

set +u
source "$CONDA_SH"
conda activate "$CONDA_ENV"
set -u

start_service "springboot" "java -jar \"$JAR_PATH\" 2>&1 | tee -a \"$SPRING_LOG\""
start_service "unet4engine" "python3 \"$PY_DIR/unet4engine.py\" 2>&1 | tee -a \"$UNET_LOG\""
start_service "mesh-api" "python3 \"$PY_DIR/mesh-api.py\" 2>&1 | tee -a \"$MESH_LOG\""
start_service "detect-api" "python3 \"$PY_DIR/detect-api.py\" 2>&1 | tee -a \"$DETECT_LOG\""

echo "[brain.sh] Waiting 5 seconds for services to start..."
sleep 5

open_browser

echo "[brain.sh] Services are running. Keep this terminal open."
wait "${PIDS[@]}"
