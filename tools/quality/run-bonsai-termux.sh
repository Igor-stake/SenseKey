#!/data/data/com.termux/files/usr/bin/bash
# SPDX-License-Identifier: GPL-3.0-only
set -eu

# Official Android arm64 CPU runtime. This archive does not contain Vulkan.
# Keep this isolated from Termux's llama-cpp: Bonsai 2 requires Prism's transforms.
if [ "$(uname -m)" != aarch64 ] || [ ! -x /system/bin/linker64 ]; then
    printf 'This launcher requires Android arm64 in Termux.\n' >&2
    exit 1
fi
for command in curl tar sha256sum; do
    command -v "$command" >/dev/null || { printf 'Missing %s. Run pkg install curl tar coreutils.\n' "$command" >&2; exit 1; }
done
if curl -sS --max-time 2 http://127.0.0.1:8080/health >/dev/null 2>&1; then
    printf 'Port 8080 is already serving a model. Stop the old server with CTRL then C first.\n' >&2
    exit 1
fi

release=prism-b10754-2459f68
archive_name="llama-$release-bin-android-arm64.tar.gz"
archive_sha=25e53af7564ed8c9c778911abda5346080ba736715234fa82ec44da165816586
runtime_root="$HOME/sensekey-runtime"
runtime_dir="$runtime_root/llama-$release"
mkdir -p "$runtime_root"
archive_path="$runtime_root/$archive_name"
if [ ! -f "$archive_path" ]; then
    printf 'Downloading official Prism Android runtime (78 MB).\n'
    curl -fL --retry 3 -C - -o "$archive_path.partial" \
        "https://github.com/PrismML-Eng/llama.cpp/releases/download/$release/$archive_name"
    printf '%s  %s\n' "$archive_sha" "$archive_path.partial" | sha256sum -c -
    mv "$archive_path.partial" "$archive_path"
else
    printf '%s  %s\n' "$archive_sha" "$archive_path" | sha256sum -c -
fi
tar -xzf "$archive_path" -C "$runtime_root"
server="$runtime_dir/llama-server"
# Android release executables have no $ORIGIN rpath. Load the matching bundled libs.
export LD_LIBRARY_PATH="$runtime_dir${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
"$server" --version

revision=b072e1d3b35a0a630cece372c2127528e0994386
case "${1:-pq}" in
    pq)
        model_name=Ternary-Bonsai-2-27B-PQ2_0.gguf
        model_sha=3907dc1658db1f78a9826bf8d5bcb8dc65db0d466388937af57f2294fae62ec1
        model_size='7.21 GB'
        ;;
    ptq)
        model_name=Ternary-Bonsai-2-27B-PTQ1_0.gguf
        model_sha=53107f530aa52eb00912263ab1ee29bd199261c87cd7b4ad4ca1318c1fe33ee3
        model_size='5.95 GB'
        ;;
    *) printf 'Usage: bash sensekey-bonsai.sh pq|ptq\n' >&2; exit 2 ;;
esac
model_dir="$HOME/sensekey-models"
mkdir -p "$model_dir"
model_path="$model_dir/$model_name"
if [ ! -f "$model_path" ]; then
    printf 'Downloading Bonsai 2 27B (%s). Interrupted downloads can resume.\n' "$model_size"
    curl -fL --retry 3 -C - -o "$model_path.partial" \
        "https://huggingface.co/prism-ml/Ternary-Bonsai-2-27B-gguf/resolve/$revision/$model_name"
    printf '%s  %s\n' "$model_sha" "$model_path.partial" | sha256sum -c -
    mv "$model_path.partial" "$model_path"
else
    printf '%s  %s\n' "$model_sha" "$model_path" | sha256sum -c -
fi

# A large logical decode batch delays the server's processing of cancellation.
# Bound both batch sizes: reducing ubatch alone does not bound the whole decode.
# Limit RAM used by saved prompt states; retain prefix reuse in the live slot.
# Disable reasoning explicitly, rather than just hiding reasoning output.
helper="$model_dir/sensekey-serve.sh"
warmup="$model_dir/sensekey-warmup.json"
source_base=https://raw.githubusercontent.com/Igor-stake/SenseKey/1e4a8470cd012d08122b2c8f0d516a8c460aa333/tools/quality
curl -fL --retry 3 -o "$helper" "$source_base/serve-local-model.sh"
curl -fL --retry 3 -o "$warmup" "$source_base/warmup.json"
printf '%s  %s\n' 170d27965afbf1006ab29b6df5df07badc051c974f244ebdf7ef38a413479f63 "$helper" | sha256sum -c -
printf '%s  %s\n' 44d8301339b694b711931b6ff66ff100414123770376bec159c825e5cfd4308c "$warmup" | sha256sum -c -
printf 'Starting Bonsai 2 CPU server. Keep this Termux session running.\n'
exec bash "$helper" "$warmup" "$server" -m "$model_path" --host 127.0.0.1 --port 8080 --alias sensekey \
    -c 4096 --parallel 1 -t 4 --jinja --n-gpu-layers 0 \
    --batch-size 16 --ubatch-size 16 --cache-ram 128 --ctx-checkpoints 4 \
    --reasoning off --reasoning-budget 0 --n-predict 256 \
    --chat-template-kwargs '{"enable_thinking":false}' --offline --no-webui
