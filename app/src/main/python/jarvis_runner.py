"""Runs a Python program inside JARVIS: output streams to the terminal, input() asks the terminal (or given lines)."""
import builtins
import os
import runpy
import sys
import traceback


class _Out:
    def __init__(self, io):
        self.io = io

    def write(self, s):
        if s:
            self.io.write(str(s))
        return len(s or "")

    def flush(self):
        pass


def _input(io):
    def ask(prompt=""):
        if prompt:
            io.write(str(prompt))
        line = io.readline()
        if line is None:
            raise EOFError("no more input")
        io.write(str(line) + "\n")
        return str(line)
    return ask


def run(code, path, workdir, io):
    """code: program text (or None to run the file at path). Returns nothing; everything goes to io."""
    os.makedirs(workdir, exist_ok=True)
    os.chdir(workdir)
    if workdir not in sys.path:
        sys.path.insert(0, workdir)
    old = sys.stdout, sys.stderr, builtins.input
    sys.stdout = sys.stderr = _Out(io)
    builtins.input = _input(io)
    try:
        if code is None:
            runpy.run_path(path, run_name="__main__")
        else:
            exec(compile(code, "main.py", "exec"), {"__name__": "__main__"})
    except SystemExit:
        pass
    except BaseException:
        traceback.print_exc()
    finally:
        sys.stdout, sys.stderr, builtins.input = old
