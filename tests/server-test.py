import os
from pathlib import Path
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
temp = Path(tempfile.mkdtemp(prefix='zartis-server-test-'))
real_docker = shutil.which('docker')
assert real_docker
fake = temp / 'docker'
script = '''#!/bin/sh
printf '%s\\n' "$*" >> "$MOCK_LOG"
case "$1" in
  info) exit 0 ;;
  network|volume) exit 0 ;;
  inspect)
    case "$*" in *backend123*) echo running;; *redis123*) echo exited;; *) exit 1;; esac ;;
  stats) echo '10MiB / 1GiB' ;;
  compose)
    case " $* " in
      *' config '*) exec "$REAL_DOCKER" "$@" ;;
      *' ps '*) case "$*" in *backend) echo backend123;; *redis) echo redis123;; esac ;;
      *' logs '*)
        if [ "${MOCK_LOG_FAILURE:-0}" = 1 ]; then echo 'log stream failed' >&2; exit 42; fi
        printf '%s\\n' 'backend-1  | backend ready' 'frontend-1 | frontend ready' 'redis-1    | cache | ready' ;;
      *' up '*|*' stop '*) exit 0 ;;
      *) exit 1 ;;
    esac ;;
  *) exit 1 ;;
esac
'''
fd = os.open(fake, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o755)
with os.fdopen(fd, 'w') as out:
    out.write(script)
env = dict(os.environ, PATH=f'{temp}:{os.environ["PATH"]}', REAL_DOCKER=real_docker, MOCK_LOG=str(temp / 'calls'))

def run(*args):
    return subprocess.run([str(root / '.docker/server.sh'), *args], cwd='/private/tmp', env=env, text=True, capture_output=True)

def calls():
    return (temp / 'calls').read_text().splitlines()

result = run('dev', 'list')
assert result.returncode == 0 and {'backend', 'redis', 'nginx'} <= set(result.stdout.splitlines()), result.stderr
result = run('dev', 'status')
assert result.returncode == 0, result.stderr
assert 'backend              running' in result.stdout and '10MiB / 1GiB' in result.stdout
assert 'redis                exited' in result.stdout and 'not created' in result.stdout
result = run('dev', 'start', 'all')
assert result.returncode == 0, result.stderr
assert any(' up -d --build' in line for line in calls())
result = run('dev', 'all', 'stop')
assert result.returncode == 0 and any(line.endswith(' stop') for line in calls()), result.stderr
result = run('dev', 'redis', 'backend', 'stop')
assert result.returncode == 0 and any(' stop redis backend' in line for line in calls()), result.stderr
result = run('dev', 'logs', 'backend', 'frontend', 'redis')
assert result.returncode == 0, result.stderr
assert result.stdout.splitlines() == [
    '[backend]  backend ready',
    '[frontend] frontend ready',
    '[redis]    cache | ready',
], repr(result.stdout)
assert any(' logs --no-color --follow backend frontend redis' in line for line in calls())
result = run('dev', 'backend', 'logs', 'frontend', 'redis')
assert result.returncode == 0 and '[frontend] frontend ready' in result.stdout
result = run('dev', 'logs', 'all')
assert result.returncode == 0, result.stderr
lines = result.stdout.splitlines()
assert len(lines) == 3 and lines[0].index('backend ready') == lines[1].index('frontend ready') == lines[2].index('cache | ready')
before = len(calls())
assert run('dev', 'logs', 'not-a-service').returncode == 2
assert not any(' logs ' in line for line in calls()[before:])
result = subprocess.run([str(root / '.docker/server.sh'), 'dev', 'logs', 'backend'], cwd='/private/tmp', env={**env, 'MOCK_LOG_FAILURE': '1'}, text=True, capture_output=True)
assert result.returncode == 42 and 'log stream failed' in result.stderr
before = len(calls())
result = run('dev', 'start', 'backend', 'not-a-service')
assert result.returncode == 2 and 'Unknown service' in result.stderr
assert not any(' up ' in line for line in calls()[before:])
assert run('dev', 'stop', 'all', 'redis').returncode == 2
for unavailable in ('qat', 'uat', 'prd'):
    before = len(calls())
    result = run(unavailable, 'start', 'all')
    assert result.returncode == 2 and 'not configured' in result.stderr
    assert len(calls()) == before
assert run('start', 'backend').returncode == 2
assert run('invalid', 'list').returncode == 2
print('PASS: dev commands, aligned logs, error propagation, service ordering and environment isolation.')
