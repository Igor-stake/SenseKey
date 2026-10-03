#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Synthetic local-model evaluation. No private chats, profile, or Android device access."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import time
import urllib.request

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parents[1]
JSON_SHA = '3ea61b2a06e31edf1c91134fe9106b0ebb16628be169f3db75bc7a2b06b45796'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server', type=Path, required=True, help='llama-server executable')
    parser.add_argument('--model', type=Path, required=True, help='local GGUF file')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--cases', type=Path, nargs='+', default=[ROOT/'cases.json', ROOT/'holdout.json'])
    parser.add_argument('--json-jar', type=Path, help='optional cached org.json 20250517 JAR')
    args = parser.parse_args()
    cases = [case for path in args.cases for case in json.loads(path.read_text())]
    args.output.mkdir(parents=True, exist_ok=True)
    model_sha = hashlib.file_digest(args.model.open('rb'), 'sha256').hexdigest()
    with tempfile.TemporaryDirectory(prefix='sensekey-quality-') as temporary:
        tmp = Path(temporary)
        jar = args.json_jar or tmp/'json.jar'
        if not jar.exists():
            with urllib.request.urlopen('https://repo.maven.apache.org/maven2/org/json/json/20250517/json-20250517.jar', timeout=30) as response:
                jar.write_bytes(response.read())
        if hashlib.sha256(jar.read_bytes()).hexdigest() != JSON_SHA:
            raise RuntimeError('Unexpected org.json JAR checksum')
        classes = tmp/'classes'
        sources = list((ROOT/'java').rglob('*.java'))
        production = REPO/'app/src/main/java/helium314/keyboard/latin/completion'
        sources += [production/'SenseCompletionClient.java', production/'SenseCompletionQuality.java']
        subprocess.run(['java', '--module', 'jdk.compiler/com.sun.tools.javac.Main', '-cp', str(jar),
                        '-d', str(classes), *map(str, sources)], check=True)
        bridge = ['java', '-cp', f'{classes}:{jar}', 'helium314.keyboard.latin.completion.QualityBridge']

        def decode(rows):
            result = subprocess.run(bridge, input=''.join(json.dumps(row, ensure_ascii=False)+'\n' for row in rows),
                                    text=True, capture_output=True, check=True)
            return [json.loads(line) for line in result.stdout.splitlines()]

        payloads = decode(cases)
        (args.output/'requests.json').write_text(json.dumps(payloads, ensure_ascii=False, indent=2)+'\n')
        manifest = {'model': args.model.name, 'model_sha256': model_sha, 'seed': 42,
                    'threads': 4, 'context_tokens': 4096, 'case_files': [p.name for p in args.cases],
                    'client_sha256': hashlib.sha256((production/'SenseCompletionClient.java').read_bytes()).hexdigest(),
                    'note': 'Host CPU timings are not phone latency. Quality must be reviewed against each case check.'}
        (args.output/'manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')
        rows = []
        with (args.output/'server.log').open('w') as log:
            process = subprocess.Popen([str(args.server.resolve()), '-m', str(args.model.resolve()),
                '--host', '127.0.0.1', '--port', '8099', '--alias', 'sensekey', '-c', '4096',
                '--parallel', '1', '-t', '4', '--jinja'], stdout=log, stderr=subprocess.STDOUT)
            try:
                for _ in range(120):
                    if process.poll() is not None:
                        raise RuntimeError('llama-server exited; see server.log')
                    try:
                        with urllib.request.urlopen('http://127.0.0.1:8099/health', timeout=1) as response:
                            if response.status == 200:
                                break
                    except OSError:
                        time.sleep(.5)
                else:
                    raise RuntimeError('Model did not become ready')
                for case, payload in zip(cases, payloads):
                    data = json.loads(payload['messages'][-1]['content'])
                    started = time.monotonic()
                    if not data['screen_context'].strip():
                        raw, finish, decoded = '', 'not_requested', {'suffix': '', 'echo': False, 'parse_error': '', 'unsupported_specifics': False}
                    else:
                        payload['seed'] = 42  # Evaluation reproducibility; the app does not force a seed.
                        request = urllib.request.Request('http://127.0.0.1:8099/v1/chat/completions',
                            data=json.dumps(payload, ensure_ascii=False).encode(), headers={'Content-Type': 'application/json'})
                        with urllib.request.urlopen(request, timeout=180) as response:
                            choice = json.load(response)['choices'][0]
                        raw, finish = choice['message'].get('content', ''), choice.get('finish_reason')
                        decoded = decode([dict(case, raw=raw)])[0]
                        if finish == 'length':
                            decoded['suffix'] = ''
                    row = dict(id=case['id'], draft=case['draft'], raw=raw, finish_reason=finish,
                               elapsed_seconds=round(time.monotonic()-started, 2), check=case['check'], **decoded)
                    rows.append(row)
                    (args.output/'results.json').write_text(json.dumps(rows, ensure_ascii=False, indent=2)+'\n')
                    print(json.dumps(row, ensure_ascii=False), flush=True)
            finally:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()


if __name__ == '__main__':
    main()
