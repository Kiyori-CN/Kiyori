"""包名/二进制、字体族/文件、ReportLab/XeLaTeX 的环境契约。"""
import subprocess
from pathlib import Path

import pytest

from kiyori_office import env, protocol


def fontconfig_fixture(tmp_path, monkeypatch):
    font = tmp_path / 'NotoSansCJK-Regular.ttc'
    font.write_bytes(b'font discovery fixture; rendering is not exercised')
    monkeypatch.setattr(env, 'CJK_FONT_GLOBS', [str(font)])
    monkeypatch.setattr(env.shutil, 'which', lambda name: '/usr/bin/' + name if name in ('fc-list', 'pandoc', 'xelatex', 'tesseract') else None)
    monkeypatch.setattr(subprocess, 'run', lambda *args, **kwargs: subprocess.CompletedProcess(
        args[0], 0, 'Noto Sans CJK JP\t%s\nNoto Sans CJK SC\t%s\n' % (font, font), ''))
    return font


def test_tier4_uses_installed_binary_names(monkeypatch):
    monkeypatch.setattr(env.shutil, 'which', lambda name: '/usr/bin/' + name if name in ('tesseract', 'xelatex') else None)
    state = env.detect()['tiers']['tier4']
    assert state['complete'] is True
    assert state['components']['tesseract-ocr']['path'] == '/usr/bin/tesseract'
    assert state['components']['texlive-xetex']['path'] == '/usr/bin/xelatex'


def test_cjk_family_is_from_fontconfig_not_filename(tmp_path, monkeypatch):
    font = fontconfig_fixture(tmp_path, monkeypatch)
    state = env.detect_cjk_fonts()
    assert state['font_families'][0] == 'Noto Sans CJK SC'
    assert 'Noto Sans CJK-Regular' not in state['font_families']
    assert state['system_font_families'] == ['Noto Sans CJK SC', 'Noto Sans CJK JP']
    assert state['fontconfig_fonts'][0]['file'] == str(font)


@pytest.mark.parametrize('command', ['office_convert', 'pdf_create'])
def test_both_pandoc_routes_use_confirmed_family(tmp_path, monkeypatch, command):
    from kiyori_office import convert, engines
    from pypdf import PdfWriter
    fontconfig_fixture(tmp_path, monkeypatch)
    source, output = tmp_path / 'paper.md', tmp_path / 'paper.pdf'
    source.write_text('# 中文测试', encoding='utf-8')
    commands = []

    def fake_engine(args, path, *positional, **kwargs):
        commands.append(args)
        writer = PdfWriter(); writer.add_blank_page(width=200, height=200)
        writer.write(path)

    monkeypatch.setattr(convert, 'run_output_command', fake_engine)
    monkeypatch.setattr(engines, 'run_output_command', fake_engine)
    args = {'output_path': str(output), 'engine': 'pandoc'}
    args.update({'from_path': str(source), 'to_format': 'pdf'} if command == 'office_convert' else {'source_path': str(source)})
    result = protocol.run(command, args)
    assert result['ok'], result
    assert 'CJKmainfont=Noto Sans CJK SC' in commands[0]


def test_unregistered_font_file_does_not_select_cid_for_xelatex(tmp_path, monkeypatch):
    source, output = tmp_path / 'paper.md', tmp_path / 'paper.pdf'
    source.write_text('中文', encoding='utf-8')
    font = tmp_path / 'NotoSansCJK-Regular.ttc'; font.write_bytes(b'fixture')
    monkeypatch.setattr(env, 'CJK_FONT_GLOBS', [str(font)])
    monkeypatch.setattr(env.shutil, 'which', lambda name: '/usr/bin/' + name if name in ('xelatex', 'pandoc') else None)
    result = protocol.run('office_convert', {'from_path': str(source), 'output_path': str(output), 'to_format': 'pdf', 'engine': 'pandoc'})
    assert not result['ok'] and result['error']['code'] == 'E_ENV_MISSING', result
    assert not output.exists()


def test_install_plan_contains_chinese_tex_package():
    plan = env.build_plan(4, ['texlive-xetex'])
    assert any('texlive-lang-chinese' in command for command in plan['commands'])


def test_watermark_default_uses_reportlab_font_after_noto_install(tmp_path, monkeypatch):
    from pypdf import PdfWriter, PdfReader
    fontconfig_fixture(tmp_path, monkeypatch)
    source, output = tmp_path / 'paper.pdf', tmp_path / 'watermark.pdf'
    writer = PdfWriter(); writer.add_blank_page(width=595, height=842); writer.write(source)
    result = protocol.run('pdf_watermark', {'path': str(source), 'output_path': str(output), 'text': '测试水印'})
    assert result['ok'], result
    assert result['data']['font'] == 'STSong-Light'
    assert '测试水印' in PdfReader(output).pages[0].extract_text()


@pytest.mark.parametrize('failure', ['timeout', 'exit'])
def test_fontconfig_failure_is_diagnostic_and_never_guesses_family(tmp_path, monkeypatch, failure):
    fontconfig_fixture(tmp_path, monkeypatch)
    def fail(args, **kwargs):
        if failure == 'timeout':
            raise subprocess.TimeoutExpired(args, 10)
        return subprocess.CompletedProcess(args, 1, '', 'fontconfig configuration error')
    monkeypatch.setattr(subprocess, 'run', fail)
    state = env.detect_cjk_fonts()
    assert state['fontconfig_available'] is False
    assert state['fontconfig_detail']
    assert state['system_font_families'] == []
    with pytest.raises(protocol.OfficeError) as error:
        env.select_system_cjk_font(state)
    assert error.value.code == 'E_ENV_MISSING'


def test_custom_family_is_discovered_and_unknown_override_rejected(tmp_path, monkeypatch):
    font = fontconfig_fixture(tmp_path, monkeypatch)
    monkeypatch.setattr(env, 'CJK_FONT_GLOBS', [])
    monkeypatch.setattr(subprocess, 'run', lambda *args, **kwargs: subprocess.CompletedProcess(args[0], 0, 'Custom Chinese Family\t%s\n' % font, ''))
    state = env.detect_cjk_fonts()
    assert state['font_files_available']
    assert env.select_system_cjk_font(state, 'Custom Chinese Family') == 'Custom Chinese Family'
    with pytest.raises(protocol.OfficeError) as error:
        env.select_system_cjk_font(state, 'Noto Sans CJK-Regular')
    assert error.value.code == 'E_INPUT_SCHEMA'


@pytest.mark.parametrize('available', ['tesseract', 'xelatex'])
def test_partial_tier4_stays_incomplete(monkeypatch, available):
    monkeypatch.setattr(env.shutil, 'which', lambda name: '/usr/bin/' + name if name == available else None)
    assert env.detect()['tiers']['tier4']['complete'] is False


def test_watermark_preserves_form_and_metadata(tmp_path):
    from reportlab.pdfgen.canvas import Canvas
    from pypdf import PdfReader
    source, output = tmp_path / 'form.pdf', tmp_path / 'watermarked.pdf'
    canvas = Canvas(str(source))
    canvas.setTitle('Research form')
    canvas.drawString(40, 760, 'Form')
    canvas.acroForm.textfield(name='author', value='Kiyori', x=40, y=700, width=200, height=24)
    canvas.showPage(); canvas.save()
    result = protocol.run('pdf_watermark', {'path': str(source), 'output_path': str(output), 'text': '测试'})
    assert result['ok'], result
    reader = PdfReader(output)
    assert reader.get_fields()['author']['/V'] == 'Kiyori'
    assert reader.metadata.title == 'Research form'
