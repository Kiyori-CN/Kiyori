import argparse, json, sys
from pathlib import Path
from .budget import bounded_text
BEGIN, END = "__KIYORI_OFFICE_BEGIN__", "__KIYORI_OFFICE_END__"
def dispatch(command, args):
    if command == "office_env_check": return {"ok": True, "python": sys.executable}
    if command == "office_validate":
        p=Path(args.get("path", "")); return {"ok": p.is_file(), "path": str(p)}
    if command == "office_read":
        data=Path(args["path"]).read_text(encoding="utf-8", errors="replace"); return {"ok": True, "text": bounded_text(data, int(args.get("max_chars", 20000)))}
    raise ValueError("E_INPUT_SCHEMA: unsupported command")
def main(argv=None):
    ap=argparse.ArgumentParser(); ap.add_argument("command"); ap.add_argument("--args-file", required=True); ns=ap.parse_args(argv); payload=json.loads(Path(ns.args_file).read_text(encoding="utf-8"))
    try: result=dispatch(ns.command, payload.get("args", {}))
    except Exception as exc: result={"ok":False,"error":{"code":str(exc).split(":",1)[0],"message":str(exc)}}
    print(BEGIN+json.dumps(result, ensure_ascii=False)+END)
if __name__ == "__main__": main()
