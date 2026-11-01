#!/usr/bin/env bash
# Downloads the tiny stand-in models used by instrumented tests into ./test-models (cached by CI).
set -euo pipefail
DIR="${1:-test-models}"
mkdir -p "$DIR"

fetch() {
  local name="$1" url="$2"
  if [[ -s "$DIR/$name" ]]; then echo "cached: $name"; return; fi
  echo "downloading: $name"
  curl -fL --retry 5 --retry-delay 5 -o "$DIR/$name.part" "$url"
  mv "$DIR/$name.part" "$DIR/$name"
}

fetch ggml-tiny.en.bin "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin"
fetch ggml-silero-v5.1.2.bin "https://huggingface.co/ggml-org/whisper-vad/resolve/main/ggml-silero-v5.1.2.bin"
fetch qwen2.5-0.5b-instruct-q4_k_m.gguf "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf"
# Piper voice in sherpa-onnx packaging (model .onnx, tokens.txt, espeak-ng-data/).
VOICE=vits-piper-en_US-amy-low
if [[ ! -d "$DIR/$VOICE" ]]; then
  echo "downloading: $VOICE"
  curl -fL --retry 5 --retry-delay 5 -o "$DIR/$VOICE.tar.bz2" "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$VOICE.tar.bz2"
  tar xjf "$DIR/$VOICE.tar.bz2" -C "$DIR"
  rm "$DIR/$VOICE.tar.bz2"
fi
ls -la "$DIR"
