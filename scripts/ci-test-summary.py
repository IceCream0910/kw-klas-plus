#!/usr/bin/env python3
import glob
import json
import os
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

kind, *patterns = sys.argv[1:]
rows = []
errors = []
if kind == 'xcresult':
    path = Path(patterns[0])
    if path.exists():
        result = subprocess.run(['xcrun', 'xcresulttool', 'get', 'test-results', 'tests', '--path', str(path), '--compact'], capture_output=True, text=True)
        if result.returncode:
            errors.append('XCTest 결과를 읽지 못했습니다. 실패 아티팩트를 확인하세요.')
        else:
            def visit(node):
                if node.get('nodeType') == 'Test Case':
                    rows.append((node.get('nodeIdentifier', node.get('name', '')), node.get('result', 'Unknown'), node.get('durationInSeconds')))
                for child in node.get('children', []):
                    visit(child)
            for node in json.loads(result.stdout).get('testNodes', []):
                visit(node)
else:
    for pattern in patterns:
        for name in sorted(glob.glob(pattern, recursive=True)):
            try:
                root = ET.parse(name).getroot()
                for case in root.iter('testcase'):
                    result = 'Failed' if case.find('failure') is not None or case.find('error') is not None else 'Skipped' if case.find('skipped') is not None else 'Passed'
                    rows.append((f"{case.get('classname', '')}.{case.get('name', '')}".strip('.'), result, case.get('time')))
            except (ET.ParseError, OSError):
                errors.append(f'결과 파일을 읽지 못했습니다: {name}')

with open(os.environ['GITHUB_STEP_SUMMARY'], 'a', encoding='utf-8') as summary:
    summary.write(f'\n### {"XCTest" if kind == "xcresult" else "JUnit 테스트"}\n\n')
    if rows:
        summary.write('| 테스트 | 결과 | 시간 |\n| --- | --- | ---: |\n')
        for name, result, duration in rows:
            safe_name = str(name).replace('|', '\\|').replace('\n', ' ')
            try:
                seconds = f'{float(duration):.2f}초'
            except (TypeError, ValueError):
                seconds = '—'
            summary.write(f'| {safe_name} | {result} | {seconds} |\n')
    else:
        summary.write('테스트 결과가 없습니다. 이전 단계 실패 또는 실행 중단 여부를 확인하세요.\n')
    for error in errors:
        summary.write(f'\n{error}\n')
