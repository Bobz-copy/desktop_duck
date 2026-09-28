"""Compara modelos con los prompts reales del ganso.

Uso:
    ./gradlew testDebugUnitTest --tests "*PromptSamplesTest"
    python tools/eval_models.py gemma3:4b qwen3:4b [--url http://localhost:11434/v1] [--runs 2]

Para cada modelo imprime lo que contestó en cada situación y un resumen:
porcentaje de respuestas con JSON válido, con acción válida, en el largo
permitido, repetidas, y la latencia media.
"""
import argparse
import json
import statistics
import sys
import time
import urllib.error
import urllib.request

SAMPLES_PATH = 'app/build/prompt-samples.json'
DEFAULT_URL = 'http://localhost:11434/v1'
REQUEST_TIMEOUT_SECONDS = 180
MAX_SAY_LENGTH = 90
DIARY_MAX_SAY_LENGTH = 300
VALID_ACTIONS = {
    'NONE', 'WANDER', 'NAP', 'ZOOMIES', 'DANCE', 'SPIN', 'SING', 'HONK', 'STRETCH',
    'LOOK_AROUND', 'PLAY_DEAD', 'MOONWALK', 'TRACK_MUD', 'SEEK_ATTENTION', 'SULK',
    'CELEBRATE',
}


def ask(url: str, model: str, sample: dict, api_key: str) -> tuple[str, float]:
    body = {
        'model': model,
        'stream': False,
        'max_tokens': sample['max_tokens'],
        'temperature': sample['temperature'],
        # Igual que OpenAiCompatBackend en modo estricto
        'reasoning_effort': 'none',
        'response_format': {
            'type': 'json_schema',
            'json_schema': {'name': 'goose_intent', 'strict': True,
                            'schema': sample['schema']},
        },
        'messages': [
            {'role': 'system', 'content': sample['system']},
            {'role': 'user', 'content': sample['user']},
        ],
    }
    headers = {'Content-Type': 'application/json'}
    if api_key:
        headers['Authorization'] = 'Bearer ' + api_key
    request = urllib.request.Request(
        url.rstrip('/') + '/chat/completions',
        data=json.dumps(body).encode('utf-8'),
        headers=headers)

    started = time.monotonic()
    with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
        payload = json.loads(response.read().decode('utf-8'))
    elapsed = time.monotonic() - started
    content = payload['choices'][0]['message'].get('content') or ''
    return content, elapsed


def strip_reasoning(text: str) -> str:
    while '<think>' in text:
        start = text.index('<think>')
        end = text.find('</think>', start)
        if end < 0:
            return text[:start]
        text = text[:start] + text[end + len('</think>'):]
    return text


def parse(text: str) -> dict | None:
    text = strip_reasoning(text).strip()
    start = text.find('{')
    end = text.rfind('}')
    if start < 0 or end <= start:
        return None
    try:
        value = json.loads(text[start:end + 1])
    except json.JSONDecodeError:
        return None
    return value if isinstance(value, dict) else None


def evaluate(url: str, model: str, samples: list[dict], runs: int, api_key: str) -> None:
    stats = {'total': 0, 'json': 0, 'action': 0, 'length': 0, 'spoke': 0}
    latencies = []
    said = []

    print('\n' + '=' * 78)
    print(model)
    print('=' * 78)
    for sample in samples:
        limit = DIARY_MAX_SAY_LENGTH if sample['trigger'] == 'DIARY' else MAX_SAY_LENGTH
        for _ in range(runs):
            stats['total'] += 1
            try:
                content, elapsed = ask(url, model, sample, api_key)
            except (urllib.error.URLError, TimeoutError, KeyError, ValueError) as error:
                print('  %-20s ERROR: %s' % (sample['name'], error))
                continue
            latencies.append(elapsed)
            intent = parse(content)
            if intent is None:
                print('  %-20s %5.1fs  SIN JSON: %r' % (sample['name'], elapsed, content[:120]))
                continue

            stats['json'] += 1
            say = intent.get('say') if isinstance(intent.get('say'), str) else ''
            action = str(intent.get('action') or 'NONE').strip().upper().replace(' ', '_')
            if action in VALID_ACTIONS:
                stats['action'] += 1
            if len(say) <= limit:
                stats['length'] += 1
            if say:
                stats['spoke'] += 1
                said.append(say)
            extra = ''
            if intent.get('remember'):
                extra = '  [recuerda: %s]' % intent['remember']
            print('  %-20s %5.1fs  %-14s %s%s' % (
                sample['name'], elapsed, action, say, extra))

    total = max(1, stats['total'])
    repeated = len(said) - len(set(said))
    print('-' * 78)
    print('  JSON válido      %3d %%' % (100 * stats['json'] // total))
    print('  acción válida    %3d %%' % (100 * stats['action'] // total))
    print('  largo permitido  %3d %%' % (100 * stats['length'] // total))
    print('  dijo algo        %3d %%' % (100 * stats['spoke'] // total))
    print('  frases repetidas %3d de %d' % (repeated, len(said)))
    if latencies:
        print('  latencia media   %.1f s (máx %.1f s)' % (
            statistics.mean(latencies), max(latencies)))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    parser.add_argument('models', nargs='+')
    parser.add_argument('--url', default=DEFAULT_URL)
    parser.add_argument('--runs', type=int, default=2)
    parser.add_argument('--api-key', default='')
    parser.add_argument('--samples', default=SAMPLES_PATH)
    args = parser.parse_args()

    try:
        with open(args.samples, encoding='utf-8') as handle:
            samples = json.load(handle)
    except FileNotFoundError:
        sys.exit('Falta %s. Corré antes: ./gradlew testDebugUnitTest '
                 '--tests "*PromptSamplesTest"' % args.samples)

    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8')
    for model in args.models:
        evaluate(args.url, model, samples, args.runs, args.api_key)


if __name__ == '__main__':
    main()
