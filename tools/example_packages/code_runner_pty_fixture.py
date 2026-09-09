"""JSON-lines bridge to a real Bash PTY, with a disposable HOME and real Python.

The package harness mocks only the pip readiness probe; this fixture does not
claim to validate venv installation, Android/proot, or the Kotlin event owner.
"""
import json
import os
from pathlib import Path
import pty
import select
import signal
import sys
import tempfile
import time


def read_until(fd, token, timeout):
    output = b""
    deadline = time.monotonic() + timeout
    while token not in output:
        if time.monotonic() >= deadline:
            return output, False
        if select.select([fd], [], [], 0.02)[0]:
            output += os.read(fd, 65536)
    return output, True


with tempfile.TemporaryDirectory(prefix="kiyori-python-pty-") as directory:
    python = Path(directory) / ".code_runner/py/bin/python"
    python.parent.mkdir(parents=True)
    python.symlink_to(sys.executable)
    pid, fd = pty.fork()
    if pid == 0:
        os.chdir(directory)
        os.environ.update(HOME=directory, PS1="KIYORI_TEST> ", PS2="MORE> ",
                          TERM="dumb", INPUTRC="/dev/null", LC_ALL="C.UTF-8")
        os.execv("/bin/bash", ["bash", "--noprofile", "--norc", "-i"])
    try:
        assert read_until(fd, b"KIYORI_TEST> ", 5)[1]
        for line in sys.stdin:
            request = json.loads(line)
            command = request["command"]
            # A data-only ANSI-C envelope models the PTY boundary. The existing
            # CommandEnvelopePtyTest separately validates the production encoder.
            payload = "".join("\\%03o" % byte for byte in command.encode())
            begin = b"\x1eKIYORI_BEGIN\x1f"
            end = b"\x1eKIYORI_END:"
            envelope = ("printf '\\036KIYORI_BEGIN\\037'; eval $'" + payload +
                        "'; printf '\\036KIYORI_END:%s\\037' \"$?\"\n").encode()
            os.set_blocking(fd, False)
            offset, output = 0, b""
            deadline = time.monotonic() + 15
            while offset < len(envelope):
                assert time.monotonic() < deadline, "PTY write timeout"
                readable, writable, _ = select.select([fd], [fd], [], 0.1)
                if readable:
                    output += os.read(fd, 65536)
                if writable:
                    offset += os.write(fd, envelope[offset:offset + 1024])
            os.set_blocking(fd, True)
            tail, completed = read_until(fd, end, request.get("timeout", 2))
            output += tail
            if completed:
                if b"\x1f" not in output.split(end, 1)[1]:
                    output += read_until(fd, b"\x1f", 2)[0]
                status = int(output.split(end, 1)[1].split(b"\x1f")[0])
            else:
                os.write(fd, b"\x03")
                assert read_until(fd, b"KIYORI_TEST> ", 3)[1], "PTY failed to settle"
                status = -1
            body = output.split(begin, 1)[-1].split(end, 1)[0]
            result = dict(exitCode=status, output=body.decode(errors="replace").replace("\r\n", "\n"),
                          timedOut=not completed, sessionHealthy=True)
            print(json.dumps(result), flush=True)
    finally:
        os.kill(pid, signal.SIGKILL)
        os.waitpid(pid, 0)
        os.close(fd)
