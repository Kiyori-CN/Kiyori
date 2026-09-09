def bounded_text(text, max_chars):
    if len(text) <= max_chars: return {"value": text, "truncated": False}
    return {"value": text[:max_chars], "truncated": True, "full_output_path": None}
