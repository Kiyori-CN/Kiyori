"""办公任务占用与清理。只管理 work/<task_id>，不删除运行时或外部交付目录。"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import time
from contextlib import contextmanager
from pathlib import Path

from .paths import _reject_symlink, atomic_write_text, resolve_task_id, work_root
from .protocol import OfficeError


def task_path(task_id):
    if not isinstance(task_id, str) or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,63}', task_id):
        raise OfficeError('E_INPUT_SCHEMA', '必须指定真实 task_id；不接受空值或隐藏目录')
    resolve_task_id({'task_id': task_id})
    root = work_root()
    _reject_symlink(root)
    path = root / task_id
    _reject_symlink(path)
    if path.parent != root:
        raise OfficeError('E_PATH_INVALID', '清理目标必须是工作根目录的直接子目录')
    return path


def lease_path(task_id):
    task_path(task_id)
    path = work_root() / '.leases' / task_id
    _reject_symlink(path)
    return path


def acquire(task_id, token):
    if not re.fullmatch(r'[A-Za-z0-9_-]{8,80}', token or ''):
        raise OfficeError('E_INPUT_SCHEMA', '任务占用令牌非法')
    path = lease_path(task_id)
    path.parent.mkdir(parents=True, exist_ok=True)
    try:
        path.mkdir()
    except FileExistsError as exc:
        raise OfficeError('E_VALIDATION_FAILED', '任务正在使用或上次执行中断未释放，禁止并发处理与清理',
                          data={'task_id':task_id,'lease_path':str(path)},
                          remedy='等待当前操作完成；异常中断后先核实执行状态，不自动删除占用标记') from exc
    # mkdir 是唯一占用提交点，令牌保护释放，另一个调用不能清理不属于自己的占用。
    try:
        atomic_write_text(path / 'owner.json', json.dumps({'token':token,'started_at':time.time()}))
    except Exception:
        path.rmdir()
        raise


def release(task_id, token):
    path = lease_path(task_id)
    owner = path / 'owner.json'
    _reject_symlink(owner)
    if _owner_token(owner) != token:
        raise OfficeError('E_VALIDATION_FAILED', '占用令牌不匹配，未释放其他调用的任务')
    owner.unlink()
    path.rmdir()


def verify_lease(task_id, token):
    owner = lease_path(task_id) / 'owner.json'
    _reject_symlink(owner)
    if _owner_token(owner) != token:
        raise OfficeError('E_VALIDATION_FAILED', '任务占用已失效，停止处理')


def _owner_token(owner):
    try:
        value = json.loads(owner.read_text(encoding='utf-8'))
        if not isinstance(value, dict):
            raise ValueError('invalid owner record')
        return value.get('token')
    except (OSError, ValueError) as exc:
        raise OfficeError('E_VALIDATION_FAILED', '任务占用记录缺失或损坏，保留标记以便核实', detail=str(owner)) from exc


@contextmanager
def task_lease(task_id, token=None):
    import uuid
    owned = token is None
    token = token or uuid.uuid4().hex
    if owned:
        acquire(task_id, token)
    else:
        verify_lease(task_id, token)
    try:
        yield
    finally:
        if owned:
            release(task_id, token)


def _temporary(relative):
    return relative.parts[0] in ('in', 'tmp') or len(relative.parts) == 1 and (
        re.fullmatch(r'args(?:-[A-Za-z0-9._-]+)?\.json', relative.name) or relative.name in ('check.json','local-install-marker'))


def inventory(task_id):
    path = task_path(task_id)
    if not path.is_dir():
        raise OfficeError('E_PATH_INVALID', '任务目录不存在', detail=str(path))
    if not any((path / name).is_dir() for name in ('in','tmp','out')):
        raise OfficeError('E_PATH_INVALID', '目录不是可识别的办公任务，拒绝清理', detail=str(path))
    files, directories = [], []
    for directory, dirs, names in os.walk(path, followlinks=False):
        dirs.sort(); names.sort()
        for name in dirs + names:
            entry = Path(directory) / name
            _reject_symlink(entry)
            relative = entry.relative_to(path)
            stat = entry.stat()
            if entry.is_dir():
                directories.append(relative.as_posix())
            elif entry.is_file():
                files.append({'path':relative.as_posix(),'bytes':stat.st_size,'mtime_ns':stat.st_mtime_ns,
                              'temporary':bool(_temporary(relative)), 'output':relative.parts[0] == 'out'})
            else:
                raise OfficeError('E_PATH_INVALID', '任务内包含特殊文件，拒绝清理', detail=str(entry))
            if len(files) + len(directories) > 100000:
                raise OfficeError('E_BUDGET_EXCEEDED', '单任务文件过多，请先在文件管理中检查')
    return path, files, directories


def list_tasks(offset=0, limit=20):
    root = work_root()
    _reject_symlink(root)
    candidates = []
    if root.exists():
        for path in root.iterdir():
            if path.name.startswith('.') or not re.fullmatch(r'[A-Za-z0-9._-]{1,64}', path.name):
                continue
            if path.is_symlink():
                continue
            if path.is_dir() and any((path / name).exists() for name in ('in','tmp','out')):
                candidates.append(path)
    candidates.sort(key=lambda p: (-p.stat().st_mtime_ns, p.name))
    tasks = []
    for path in candidates[offset:offset + limit]:
        try:
            _, files, _ = inventory(path.name)
            active = lease_path(path.name).exists()
            tasks.append({'task_id':path.name,'path':str(path),'files':len(files),
                          'bytes':sum(f['bytes'] for f in files),
                          'temporary_bytes':sum(f['bytes'] for f in files if f['temporary']),
                          'output_bytes':sum(f['bytes'] for f in files if f['output']),
                          'output_files':sum(f['output'] for f in files),
                          'modified_at':max([path.stat().st_mtime] + [f['mtime_ns']/1e9 for f in files]),
                          'active':active, 'cleanable':not active})
        except (OfficeError, OSError) as exc:
            tasks.append({'task_id':path.name,'path':str(path),'cleanable':False,'error':str(exc)})
    return {'execution_env':'linux','work_root':str(root),'runtime_root':str(root.parent/'runtime'),
            'tasks':tasks,'total_tasks':len(candidates),'offset':offset,
            'next_offset':offset+limit if offset+limit < len(candidates) else None,
            'listed_bytes':sum(t.get('bytes',0) for t in tasks),
            'note':'这里只统计 Ubuntu 办公任务；runtime 是程序文件，Android 交付和自建测试工作区不在清理范围。'}


def clean(task_id, *, scope='temporary', confirm=False, plan_token=None):
    if scope not in ('temporary','task'):
        raise OfficeError('E_INPUT_SCHEMA', 'scope 必须是 temporary 或 task')
    with task_lease(task_id):
        path, files, directories = inventory(task_id)
        selected = files if scope == 'task' else [f for f in files if f['temporary']]
        selected_dirs = directories if scope == 'task' else [p for p in directories if Path(p).parts[0] in ('in','tmp')]
        # 确认绑定文件清单、大小、纳秒修改时间与目录。预览后状态变化必须重新预览。
        snapshot = json.dumps([str(path),scope,files,directories],sort_keys=True).encode('utf-8')
        token = hashlib.sha256(snapshot).hexdigest()
        report = {'task_id':task_id,'path':str(path),'scope':scope,'dry_run':not confirm,
                  'plan_token':token,'file_count':len(selected),'bytes':sum(f['bytes'] for f in selected),
                  'output_files':sum(f['output'] for f in selected),'preserves_outputs':scope == 'temporary',
                  'sample_files':[f['path'] for f in selected[:20]],'removed':False}
        if not confirm:
            return report
        if not plan_token or plan_token != token:
            raise OfficeError('E_VALIDATION_FAILED', '清理计划缺失或目录已变化，请重新预览', data=report)
        removed = 0
        try:
            # 不使用广泛 rmtree：精确删除预览清单；新增/未知文件会让目录删除失败。
            for entry in selected:
                target = path / entry['path']
                _reject_symlink(target)
                target.unlink()
                removed += 1
            for relative in sorted(selected_dirs, key=lambda p:len(Path(p).parts), reverse=True):
                target = path / relative
                _reject_symlink(target)
                if scope != 'task' and relative in ('in','tmp'):
                    continue
                target.rmdir()
            if scope == 'task':
                path.rmdir()
        except (OSError, OfficeError) as exc:
            raise OfficeError('E_PATH_INVALID', '清理部分完成，停止并保留剩余内容',
                              data={**report,'removed_files':removed},detail=str(exc)) from exc
        return {**report,'removed':True,'removed_files':removed}


def main():
    from .protocol import build_failure, emit
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=('acquire','release'))
    parser.add_argument('task_id'); parser.add_argument('token')
    args = parser.parse_args()
    try:
        (acquire if args.action == 'acquire' else release)(args.task_id,args.token)
        emit({'ok':True,'command':'office_task_lease','data':{'action':args.action}})
    except OfficeError as exc:
        emit(build_failure('office_task_lease',exc))
    except OSError as exc:
        emit(build_failure('office_task_lease', OfficeError('E_PATH_INVALID', '无法更新任务占用', detail=str(exc))))


if __name__ == '__main__':
    main()
