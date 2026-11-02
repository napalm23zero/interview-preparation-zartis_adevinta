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

result = run('list')
assert result.returncode == 0 and {'backend', 'redis', 'nginx'} <= set(result.stdout.splitlines()), result.stderr
result = run('status')
assert result.returncode == 0, result.stderr
assert 'backend              running' in result.stdout and '10MiB / 1GiB' in result.stdout
assert 'redis                exited' in result.stdout and 'not created' in result.stdout
result = run('start', 'all')
assert result.returncode == 0, result.stderr
assert any(' up -d --build' in line for line in calls())
result = run('all', 'stop')
assert result.returncode == 0 and any(line.endswith(' stop') for line in calls()), result.stderr
result = run('redis', 'backend', 'stop')
assert result.returncode == 0 and any(' stop redis backend' in line for line in calls()), result.stderr
before = len(calls())
result = run('start', 'backend', 'not-a-service')
assert result.returncode == 2 and 'Unknown service' in result.stderr
assert not any(' up ' in line for line in calls()[before:])
assert run('stop', 'all', 'redis').returncode == 2
print('PASS: list, status and memory, start all, reordered stop, invalid service, and conflicting targets.')
