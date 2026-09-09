from pathlib import Path
def safe_child(root, child):
    root, target = Path(root).resolve(), (Path(root) / child).resolve()
    if root != target and root not in target.parents: raise ValueError("E_PATH_ESCAPE: path escapes workspace")
    return target
