"""工作文件清理必须可预览、可定位，并与实际处理互斥。"""
from pathlib import Path

import pytest

from kiyori_office import protocol, storage
from kiyori_office.paths import task_dir
from kiyori_office.protocol import OfficeError


def populated(task_id='demo'):
    path = task_dir(task_id)
    (path / 'in' / 'source.docx').write_bytes(b'input')
    (path / 'tmp' / 'nested').mkdir()
    (path / 'tmp' / 'nested' / 'preview.png').write_bytes(b'image')
    (path / 'out' / 'final.pdf').write_bytes(b'output')
    (path / 'args-123.json').write_text('{}')
    (path / 'personal.txt').write_bytes(b'keep')
    return path


def test_preview_then_temporary_preserves_outputs_and_unknown_files():
    path = populated()
    preview = storage.clean('demo')
    assert preview['dry_run'] and preview['file_count'] == 3
    assert (path / 'in' / 'source.docx').exists()
    result = storage.clean('demo', confirm=True, plan_token=preview['plan_token'])
    assert result['removed_files'] == 3
    assert (path / 'out' / 'final.pdf').read_bytes() == b'output'
    assert (path / 'personal.txt').read_bytes() == b'keep'
    assert list((path / 'in').iterdir()) == list((path / 'tmp').iterdir()) == []
    assert not storage.lease_path('demo').exists()


def test_entire_task_cleanup_excludes_neighbor_and_runtime(tmp_path):
    path = populated()
    neighbor = populated('neighbor')
    runtime = tmp_path / 'runtime'
    runtime.mkdir()
    plan = storage.clean('demo', scope='task')
    assert plan['output_files'] == 1 and plan['preserves_outputs'] is False
    storage.clean('demo', scope='task', confirm=True, plan_token=plan['plan_token'])
    assert not path.exists() and neighbor.exists() and runtime.exists()


@pytest.mark.parametrize('change', ['add', 'modify', 'scope'])
def test_confirmation_is_bound_to_reviewed_snapshot(change):
    path = populated()
    plan = storage.clean('demo')
    if change == 'add':
        (path / 'tmp' / 'new').write_bytes(b'new')
    if change == 'modify':
        (path / 'in' / 'source.docx').write_bytes(b'changed')
    with pytest.raises(OfficeError, match='重新预览'):
        storage.clean('demo', scope='task' if change == 'scope' else 'temporary', confirm=True, plan_token=plan['plan_token'])
    assert (path / 'in' / 'source.docx').exists()


@pytest.mark.parametrize('task_id', ['', '.', '..', '../outside', '.leases', 'x/y', ' x ', None])
def test_invalid_task_never_creates_a_directory(tmp_path, task_id):
    with pytest.raises(OfficeError):
        storage.clean(task_id)
    assert list(tmp_path.iterdir()) == []


def test_active_task_is_visible_but_not_cleanable():
    populated()
    populated('other')
    storage.acquire('demo', 'owner_token')
    try:
        report = storage.list_tasks()
        assert next(t for t in report['tasks'] if t['task_id'] == 'demo')['cleanable'] is False
        with pytest.raises(OfficeError, match='正在使用'):
            storage.clean('demo')
        with pytest.raises(OfficeError, match='令牌不匹配'):
            storage.release('demo', 'wrong_token')
        assert storage.clean('other')['dry_run']
    finally:
        storage.release('demo', 'owner_token')


def test_inventory_pagination_and_unsupported_directory(tmp_path):
    for name in ('a', 'b', 'c'):
        populated(name)
    (tmp_path / 'unrelated').mkdir()
    first = storage.list_tasks(limit=2)
    second = storage.list_tasks(offset=first['next_offset'], limit=2)
    assert first['total_tasks'] == 3 and second['next_offset'] is None
    assert len({t['task_id'] for t in first['tasks'] + second['tasks']}) == 3
    with pytest.raises(OfficeError, match='可识别'):
        storage.clean('unrelated')


def test_partial_failure_is_reported_and_releases_lease(monkeypatch):
    path = populated()
    plan = storage.clean('demo')
    original = Path.unlink
    def fail_input(target, *args, **kwargs):
        if target.name == 'source.docx':
            raise PermissionError('busy input')
        return original(target, *args, **kwargs)
    monkeypatch.setattr(Path, 'unlink', fail_input)
    with pytest.raises(OfficeError, match='部分完成') as caught:
        storage.clean('demo', confirm=True, plan_token=plan['plan_token'])
    assert caught.value.data['removed_files'] == 1
    assert (path / 'out' / 'final.pdf').exists()
    assert not storage.lease_path('demo').exists()


def test_symlink_is_rejected_without_following_target(tmp_path):
    path = populated()
    target = tmp_path / 'private.txt'
    target.write_bytes(b'private')
    try:
        (path / 'tmp' / 'link').symlink_to(target)
    except OSError:
        pytest.skip('host does not allow symlinks')
    with pytest.raises(OfficeError, match='符号链接'):
        storage.clean('demo')
    assert target.read_bytes() == b'private'


def test_direct_protocol_failure_releases_and_external_lease_stays_owned(monkeypatch):
    path = populated()
    args = {'task_id': 'demo', 'path': str(path / 'missing.docx')}
    assert protocol.run('office_read', args)['ok'] is False
    assert not storage.lease_path('demo').exists()
    storage.acquire('demo', 'external_token')
    monkeypatch.setenv('KIYORI_OFFICE_LEASE_TOKEN', 'external_token')
    assert protocol.run('office_read', args)['ok'] is False
    assert storage.lease_path('demo').exists()
    monkeypatch.setenv('KIYORI_OFFICE_LEASE_TOKEN', 'incorrect_token')
    result = protocol.run('office_read', args)
    assert result['error']['code'] == 'E_VALIDATION_FAILED'
    storage.release('demo', 'external_token')


def test_corrupt_lease_returns_structured_failure_and_preserves_marker(monkeypatch):
    path = populated()
    storage.acquire('demo', 'external_token')
    (storage.lease_path('demo') / 'owner.json').write_text('{broken')
    monkeypatch.setenv('KIYORI_OFFICE_LEASE_TOKEN', 'external_token')
    result = protocol.run('office_read', {'task_id': 'demo', 'path': str(path / 'in' / 'source.docx')})
    assert result['error']['code'] == 'E_VALIDATION_FAILED'
    assert storage.lease_path('demo').exists()
