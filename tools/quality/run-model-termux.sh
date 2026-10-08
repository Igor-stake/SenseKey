#!/data/data/com.termux/files/usr/bin/bash
# SPDX-License-Identifier: GPL-3.0-only
set -eu

# Text-only GGUF: no vision projector or private user data is downloaded.
case "${1:-9b}" in
    4b)
        source_repo="unsloth/Qwen3.5-4B-GGUF"
        revision="e87f176479d0855a907a41277aca2f8ee7a09523"
        source_file="Qwen3.5-4B-Q4_K_M.gguf"
        expected="00fe7986ff5f6b463e62455821146049db6f9313603938a70800d1fb69ef11a4"
        local_file="SenseKey-qwen35-4b.gguf"
        ;;
    9b)
        source_repo="unsloth/Qwen3.5-9B-GGUF"
        revision="3885219b6810b007914f3a7950a8d1b469d598a5"
        source_file="Qwen3.5-9B-Q4_K_M.gguf"
        expected="03b74727a860a56338e042c4420bb3f04b2fec5734175f4cb9fa853daf52b7e8"
        local_file="SenseKey-qwen35-9b.gguf"
        ;;
    *) printf 'Usage: bash run-model-termux.sh 4b|9b\n' >&2; exit 2 ;;
esac

command -v llama-server >/dev/null || { printf 'Run pkg install llama-cpp first.\n' >&2; exit 1; }
model_dir="$HOME/sensekey-models"
mkdir -p "$model_dir"
model_path="$model_dir/$local_file"
if [ ! -f "$model_path" ]; then
    curl -fL --retry 3 -C - -o "$model_path.partial" \
        "https://huggingface.co/$source_repo/resolve/$revision/$source_file"
    printf '%s  %s\n' "$expected" "$model_path.partial" | sha256sum -c -
    mv "$model_path.partial" "$model_path"
else
    printf '%s  %s\n' "$expected" "$model_path" | sha256sum -c -
fi

# Let the server handle cancellation between small logical prompt batches.
# Optional flags are used only when the installed Termux version supports them.
server_help=$(llama-server --help 2>&1)
set -- -m "$model_path" --host 127.0.0.1 --port 8080 \
    --alias sensekey -c 4096 --parallel 1 -t 4 --jinja \
    --batch-size 64 --ubatch-size 64
if [[ "$server_help" == *--cache-ram* ]]; then set -- "$@" --cache-ram 128; fi
if [[ "$server_help" == *--reasoning\ \[* ]]; then set -- "$@" --reasoning off; fi
if [[ "$server_help" == *--reasoning-budget* ]]; then set -- "$@" --reasoning-budget 0; fi
helper="$model_dir/sensekey-serve.sh"
warmup="$model_dir/sensekey-warmup.json"
source_base=https://raw.githubusercontent.com/Igor-stake/SenseKey/1e4a8470cd012d08122b2c8f0d516a8c460aa333/tools/quality
curl -fL --retry 3 -o "$helper" "$source_base/serve-local-model.sh"
curl -fL --retry 3 -o "$warmup" "$source_base/warmup.json"
printf '%s  %s\n' 170d27965afbf1006ab29b6df5df07badc051c974f244ebdf7ef38a413479f63 "$helper" | sha256sum -c -
printf '%s  %s\n' 44d8301339b694b711931b6ff66ff100414123770376bec159c825e5cfd4308c "$warmup" | sha256sum -c -
printf 'Starting SenseKey local model with prompt batch 64. Keep this Termux session running.\n'
exec bash "$helper" "$warmup" llama-server "$@"
