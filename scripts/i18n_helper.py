#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
i18n_helper.py — 鼠音 App 多语言字符串批量添加工具

用途：一键给 Strings.kt 添加新的字符串 key（支持所有 37 种语言），
      同时可选地给 values-xx/strings.xml 添加 app_name 等资源。

使用方法：
    python3 i18n_helper.py add strings.json
    python3 i18n_helper.py add strings.json --dry-run
    python3 i18n_helper.py list
    python3 i18n_helper.py validate
    python3 i18n_helper.py remove key_name
    python3 i18n_helper.py app-name ja シューイン

strings.json 格式示例：
{
  "new_key_name": {
    "zh": "中文翻译",
    "zh-TW": "繁體翻譯",
    "en": "English text",
    "ja": "日本語テキスト"
  }
}

未提供翻译的语言会自动用英文兜底（英文也没有用中文）。

详见 I18N_HELPER_GUIDE.md。
"""

import argparse
import json
import os
import re
import sys

# ============================================================
# 配置：项目路径和语言列表
# ============================================================

# 项目根目录（脚本所在目录的上一级）
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
STRINGS_KT_PATH = os.path.join(
    PROJECT_ROOT, 'app', 'src', 'main', 'kotlin',
    'com', 'xiaowei', 'player', 'i18n', 'Strings.kt'
)
RES_DIR = os.path.join(PROJECT_ROOT, 'app', 'src', 'main', 'res')

MAX_KEYS_PER_PART = 29

# 所有支持的语言代码（与 Strings.kt 中 SUPPORTED_LANGS 保持一致）
ALL_LANGS = [
    'zh', 'zh-TW', 'zh-HK', 'zh-MO',
    'en', 'ja', 'ko', 'fr', 'de', 'es', 'ru',
    'pt', 'it', 'ar', 'hi', 'th', 'vi', 'in', 'tr',
    'ug', 'mn', 'fa', 'ur', 'bn', 'pl', 'uk', 'nl',
    'sv', 'cs', 'hu', 'el', 'ro', 'fi', 'da', 'nb',
    'ms', 'tl',
]

# ============================================================
# 工具函数
# ============================================================

def escape_kotlin_string(text):
    """转义 Kotlin 字符串字面量中的特殊字符"""
    # 顺序很重要：先转义反斜杠
    text = text.replace('\\', '\\\\')
    # 转义双引号
    text = text.replace('"', '\\"')
    # 转义 $（Kotlin 字符串模板插值）
    text = text.replace('$', '\\$')
    # 把真换行符转义成 \n（Kotlin 字符串不支持多行）
    text = text.replace('\n', '\\n')
    # 制表符
    text = text.replace('\t', '\\t')
    return text


def parse_strings_kt(content):
    """
    解析 Strings.kt 中的所有 STRINGS_PART 字典。
    返回 dict: { key: { lang: text } }
    """
    result = {}
    part_pattern = re.compile(
        r'private fun buildStringsPart\d+\(\)[^=]*=\s*mapOf\((.*?)\n    \)',
        re.DOTALL
    )
    for pm in part_pattern.finditer(content):
        body = pm.group(1)
        key_pattern = re.compile(
            r'"([a-z_][a-z0-9_]*)" to mapOf\(\n(.*?)\n        \),',
            re.DOTALL
        )
        for km in key_pattern.finditer(body):
            key = km.group(1)
            block = km.group(2)
            lang_map = {}
            lang_pattern = re.compile(r'"([a-z]{2}(?:-[A-Z]{2})?)" to "((?:[^"\\]|\\.)*)"')
            for lm in lang_pattern.finditer(block):
                lang = lm.group(1)
                text = lm.group(2)
                text = text.replace('\\n', '\n')
                text = text.replace('\\"', '"')
                text = text.replace('\\$', '$')
                text = text.replace('\\\\', '\\')
                lang_map[lang] = text
            result[key] = lang_map
    return result


def build_key_block(key, lang_map):
    """构建一个 key 的 Kotlin 代码块"""
    lines = [f'        "{key}" to mapOf(']
    for lang in ALL_LANGS:
        if lang in lang_map:
            text = escape_kotlin_string(lang_map[lang])
            lines.append(f'            "{lang}" to "{text}",')
    lines.append('        ),')
    return '\n'.join(lines)


def insert_key_into_content(content, key, lang_map):
    """
    在最后一个 STRINGS_PART 字典的末尾插入新 key。
    如果 key 已存在，先删除旧的。
    如果最后一个 PART 已满，新建一个 PART 并更新 ALL_STRINGS 汇总。
    """
    content = remove_key_from_content(content, key)

    part_positions = [(m.start(), m.end()) for m in re.finditer(
        r'private fun buildStringsPart\d+\(\)[^=]*=\s*mapOf\(.*?\n    \)', content, re.DOTALL)]
    if not part_positions:
        raise RuntimeError('找不到 STRINGS_PART 字典')

    last_start, last_end = part_positions[-1]
    last_part = content[last_start:last_end]
    key_count = len(re.findall(r'"[a-z_][a-z0-9_]*" to mapOf\(', last_part))

    if key_count >= MAX_KEYS_PER_PART:
        part_numbers = [int(m.group(1)) for m in re.finditer(r'buildStringsPart(\d+)', content)]
        next_num = max(part_numbers) + 1 if part_numbers else 1
        new_part = '\n\n    private fun buildStringsPart%d(): Map<String, Map<String, String>> = mapOf(\n%s\n    )' % (next_num, build_key_block(key, lang_map))
        insert_pos = last_end
        new_content = content[:insert_pos] + new_part + content[insert_pos:]
        sum_pattern = re.compile(r'private val ALL_STRINGS: Map<String, Map<String, String>> = [^\n]*')
        sum_match = sum_pattern.search(new_content)
        if sum_match:
            new_content = new_content[:sum_match.end()] + ' + buildStringsPart%d()' % next_num + new_content[sum_match.end():]
        return new_content

    close_matches = list(re.finditer(r'\n    \)', content[last_start:last_end]))
    if not close_matches:
        raise RuntimeError('找不到最后一个 STRINGS_PART 的结束位置')
    insert_pos = last_start + close_matches[-1].start()
    new_block = '\n' + build_key_block(key, lang_map)
    return content[:insert_pos] + new_block + content[insert_pos:]


def remove_key_from_content(content, key):
    """从 STRINGS_PART 字典中删除指定 key"""
    pattern = re.compile(
        r'\n        "' + re.escape(key) + r'" to mapOf\(\n.*?\n        \),',
        re.DOTALL
    )
    return pattern.sub('', content, count=1)


def cleanup_empty_parts(content):
    """
    删除空分段函数（buildStringsPartN 内已无 key），
    并同步从 ALL_STRINGS 汇总行移除对应引用。
    返回 (content, 清理的分段数)。
    """
    empty_pattern = re.compile(
        r'\n\n    private fun buildStringsPart(\d+)\(\): Map<String, Map<String, String>> = mapOf\(\n    \)'
    )
    removed_nums = []

    def _collect(m):
        removed_nums.append(m.group(1))
        return ''

    content = empty_pattern.sub(_collect, content)
    for num in removed_nums:
        content = content.replace(' + buildStringsPart%s()' % num, '')

    # 防御：若所有分段都被删空，汇总行指向空 mapOf() 保证 Kotlin 仍可编译
    if removed_nums and not re.search(r'private fun buildStringsPart\d+\(\)', content):
        content = re.sub(
            r'private val ALL_STRINGS: Map<String, Map<String, String>> = [^\n]*',
            'private val ALL_STRINGS: Map<String, Map<String, String>> = mapOf()',
            content
        )
    return content, len(removed_nums)


def read_strings_kt():
    """读取 Strings.kt 内容"""
    with open(STRINGS_KT_PATH, 'r', encoding='utf-8') as f:
        return f.read()


def write_strings_kt(content):
    """写入 Strings.kt"""
    with open(STRINGS_KT_PATH, 'w', encoding='utf-8') as f:
        f.write(content)


def validate_kotlin_syntax(content):
    """
    简单验证 Kotlin 语法：
    - 检查引号配对
    - 检查括号配对
    """
    errors = []

    # 检查字符串字面量内是否有未闭合的引号
    lines = content.split('\n')
    for i, line in enumerate(lines, 1):
        stripped = line.strip()
        if stripped.startswith('//') or stripped.startswith('*'):
            continue
        if ' to "' in line:
            # 排除转义的 \" 后数引号
            clean = line.replace('\\"', '')
            quote_count = clean.count('"')
            if quote_count % 2 != 0:
                errors.append(f'第 {i} 行引号未闭合: {line.strip()[:80]}')

    # 检查括号配对
    depth = 0
    for char in content:
        if char == '(':
            depth += 1
        elif char == ')':
            depth -= 1
            if depth < 0:
                errors.append('括号不匹配：多余的 )')
                break
    if depth > 0:
        errors.append(f'括号不匹配：缺少 {depth} 个 )')

    return errors


# ============================================================
# 命令实现
# ============================================================

def cmd_add(json_path, dry_run=False):
    """添加新字符串"""
    if not os.path.exists(json_path):
        print(f'错误: 文件不存在 {json_path}')
        return 1

    with open(json_path, 'r', encoding='utf-8') as f:
        new_strings = json.load(f)

    if not isinstance(new_strings, dict):
        print('错误: JSON 根元素必须是对象 {key: {lang: text}}')
        return 1

    print(f'准备添加 {len(new_strings)} 个字符串 key')
    print(f'源文件: {json_path}')
    print()

    content = read_strings_kt()
    existing = parse_strings_kt(content)

    added = 0
    skipped = 0
    for key, lang_map in new_strings.items():
        # 验证 key 格式
        if not re.match(r'^[a-z][a-z0-9_]*$', key):
            print(f'  X 跳过 "{key}": key 必须以小写字母开头，只允许小写字母/数字/下划线')
            skipped += 1
            continue

        if not isinstance(lang_map, dict):
            print(f'  X 跳过 "{key}": 值必须是对象 {{lang: text}}')
            skipped += 1
            continue

        # 检查必须的语言
        if 'zh' not in lang_map and 'en' not in lang_map:
            print(f'  X 跳过 "{key}": 至少需要 zh 或 en 翻译')
            skipped += 1
            continue

        is_update = key in existing
        action = '更新' if is_update else '添加'
        lang_count = len(lang_map)
        print(f'  + {action} "{key}" ({lang_count} 种语言)')

        if not dry_run:
            content = insert_key_into_content(content, key, lang_map)

        added += 1

    if dry_run:
        print(f'\n[试运行] 未实际修改文件。将添加 {added} 个，跳过 {skipped} 个。')
        return 0

    # 写入
    if added > 0:
        errors = validate_kotlin_syntax(content)
        if errors:
            print('\n警告: 语法检查发现问题:')
            for e in errors[:10]:
                print(f'  - {e}')
            print('请手动检查 Strings.kt')

        write_strings_kt(content)
        print(f'\nOK 已写入 {STRINGS_KT_PATH}')
        print(f'  添加/更新: {added}')
        print(f'  跳过: {skipped}')
    else:
        print(f'\n没有需要添加的 key（跳过 {skipped} 个）')

    return 0


def cmd_list():
    """列出所有现有 key"""
    content = read_strings_kt()
    existing = parse_strings_kt(content)

    print(f'Strings.kt 共有 {len(existing)} 个 key:\n')
    try:
        for key in sorted(existing.keys()):
            lang_count = len(existing[key])
            zh = existing[key].get('zh', '')
            en = existing[key].get('en', '')
            preview = zh or en or '(空)'
            if len(preview) > 40:
                preview = preview[:37] + '...'
            print(f'  {key:<30} [{lang_count}种语言] {preview}')
    except BrokenPipeError:
        # 输出被管道提前关闭（如 head），正常退出
        pass

    return 0


def cmd_validate():
    """验证 Strings.kt 语法和完整性"""
    content = read_strings_kt()
    existing = parse_strings_kt(content)

    print(f'Strings.kt 验证报告')
    print(f'=' * 50)
    print(f'总 key 数: {len(existing)}')
    print(f'支持语言数: {len(ALL_LANGS)}')
    print()

    # 语法检查
    errors = validate_kotlin_syntax(content)
    if errors:
        print(f'X 语法错误 ({len(errors)} 个):')
        for e in errors[:20]:
            print(f'  - {e}')
    else:
        print('OK 语法检查通过')

    print()
    # 检查每个 key 的语言覆盖
    print('语言覆盖检查:')
    incomplete = []
    for key in sorted(existing.keys()):
        missing = [lang for lang in ALL_LANGS if lang not in existing[key]]
        if missing:
            incomplete.append((key, missing))

    if incomplete:
        print(f'  {len(incomplete)} 个 key 缺少部分语言翻译:')
        for key, missing in incomplete[:20]:
            print(f'    {key}: 缺 {", ".join(missing)}')
        if len(incomplete) > 20:
            print(f'    ... 还有 {len(incomplete) - 20} 个')
    else:
        print('  OK 所有 key 都包含全部 37 种语言翻译')

    return 0 if not errors else 1


def cmd_remove(key):
    """删除一个 key"""
    content = read_strings_kt()
    existing = parse_strings_kt(content)

    if key not in existing:
        print(f'错误: key "{key}" 不存在')
        return 1

    new_content = remove_key_from_content(content, key)
    new_content, _ = cleanup_empty_parts(new_content)
    write_strings_kt(new_content)
    print(f'OK 已删除 key "{key}"')
    return 0


def cmd_remove_batch(json_path=None, keys=None, dry_run=False):
    """
    批量删除 key。
    支持两种输入：
    - json_path: JSON 文件，格式为 ["key1", "key2", ...] 或 {"key1": {...}, "key2": {...}}
                 （会自动提取 key 名，翻译内容忽略）
    - keys: 命令行直接传入的 key 列表
    """
    # argparse 的 json_path 是 nargs='?'，可能误把 key 当成文件名
    # 如果 json_path 存在但不是文件，把它当成 key
    if json_path and not os.path.exists(json_path):
        # 不是文件，当成 key
        if keys is None:
            keys = []
        keys = [json_path] + keys
        json_path = None

    # 收集所有要删除的 key
    keys_to_remove = []
    if keys:
        keys_to_remove.extend(keys)
    if json_path:
        with open(json_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
        if isinstance(data, list):
            # 数组格式: ["key1", "key2", ...]
            keys_to_remove.extend(data)
        elif isinstance(data, dict):
            # 对象格式: {"key1": {...}, "key2": {...}}，提取 key 名
            keys_to_remove.extend(data.keys())
        else:
            print(f'错误: JSON 文件必须是数组或对象')
            return 1

    if not keys_to_remove:
        print('错误: 没有指定要删除的 key')
        print('用法: python3 i18n_helper.py remove-batch <json_file> [key1 key2 ...]')
        return 1

    # 去重
    keys_to_remove = list(dict.fromkeys(keys_to_remove))

    print(f'准备删除 {len(keys_to_remove)} 个 key')
    if json_path:
        print(f'源文件: {json_path}')
    print()

    content = read_strings_kt()
    existing = parse_strings_kt(content)

    removed = 0
    not_found = 0
    for key in keys_to_remove:
        if key in existing:
            print(f'  - 删除 "{key}"')
            if not dry_run:
                content = remove_key_from_content(content, key)
            removed += 1
        else:
            print(f'  ? 跳过 "{key}" (不存在)')
            not_found += 1

    if dry_run:
        print(f'\n[试运行] 未实际修改文件。将删除 {removed} 个，未找到 {not_found} 个。')
        return 0

    if removed > 0:
        content, cleaned_parts = cleanup_empty_parts(content)
        # 验证语法
        errors = validate_kotlin_syntax(content)
        if errors:
            print('\n警告: 语法检查发现问题:')
            for e in errors[:10]:
                print(f'  - {e}')

        write_strings_kt(content)
        print(f'\nOK 已写入 {STRINGS_KT_PATH}')
        print(f'  删除: {removed}')
        print(f'  未找到: {not_found}')
        if cleaned_parts > 0:
            print(f'  清理空分段: {cleaned_parts} 个')
    else:
        print(f'\n没有需要删除的 key（未找到 {not_found} 个）')

    return 0


def cmd_add_app_name(lang, name):
    """给 values-xx/strings.xml 添加/更新 app_name"""
    lang_dir = os.path.join(RES_DIR, f'values-{lang}')
    os.makedirs(lang_dir, exist_ok=True)
    fpath = os.path.join(lang_dir, 'strings.xml')
    with open(fpath, 'w', encoding='utf-8') as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n')
        f.write('<resources>\n')
        f.write(f'    <string name="app_name">{name}</string>\n')
        f.write('</resources>\n')
    print(f'OK 已更新 values-{lang}/strings.xml: app_name = {name}')
    return 0


# ============================================================
# 主入口
# ============================================================

def main():
    parser = argparse.ArgumentParser(
        description='鼠音 App 多语言字符串批量管理工具',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog='''
示例:
  # 添加新字符串（从 JSON 文件批量添加）
  python3 i18n_helper.py add new_strings.json

  # 试运行（不实际修改文件）
  python3 i18n_helper.py add new_strings.json --dry-run

  # 列出所有现有 key
  python3 i18n_helper.py list

  # 验证 Strings.kt 完整性
  python3 i18n_helper.py validate

  # 删除一个 key
  python3 i18n_helper.py remove some_key

  # 批量删除：从 JSON 文件（数组或对象格式都支持）
  python3 i18n_helper.py remove-batch keys_to_delete.json

  # 批量删除：直接在命令行传 key 名
  python3 i18n_helper.py remove-batch key1 key2 key3

  # 批量删除：JSON 文件 + 命令行 key 组合
  python3 i18n_helper.py remove-batch keys.json extra_key

  # 批量删除试运行
  python3 i18n_helper.py remove-batch keys_to_delete.json --dry-run

  # 更新某个语言的 app_name（桌面图标名）
  python3 i18n_helper.py app-name ja シューイン

JSON 文件格式示例 (new_strings.json):
{
    "greeting": {
        "zh": "你好",
        "en": "Hello",
        "ja": "こんにちは"
    },
    "farewell": {
        "zh": "再见",
        "en": "Goodbye"
    }
}

批量删除 JSON 文件格式示例 (keys_to_delete.json):
  ["greeting", "farewell", "old_key"]
  或
  {"greeting": {}, "farewell": {}}  (会自动提取 key 名)

未提供翻译的语言会自动用英文兜底（英文没有用中文）。
        '''
    )

    sub = parser.add_subparsers(dest='command', help='子命令')

    p_add = sub.add_parser('add', help='从 JSON 文件批量添加字符串')
    p_add.add_argument('json_file', help='JSON 文件路径')
    p_add.add_argument('--dry-run', action='store_true', help='试运行，不实际修改文件')

    sub.add_parser('list', help='列出所有现有 key')

    sub.add_parser('validate', help='验证 Strings.kt 语法和完整性')

    p_remove = sub.add_parser('remove', help='删除一个 key')
    p_remove.add_argument('key', help='要删除的 key 名')

    p_remove_batch = sub.add_parser('remove-batch', help='批量删除多个 key')
    p_remove_batch.add_argument('json_file', nargs='?', help='JSON 文件路径（数组或对象格式）')
    p_remove_batch.add_argument('keys', nargs='*', help='要删除的 key 名（可多个，与 JSON 文件二选一或组合）')
    p_remove_batch.add_argument('--dry-run', action='store_true', help='试运行，不实际修改文件')

    p_appname = sub.add_parser('app-name', help='更新某语言的 app_name (桌面图标名)')
    p_appname.add_argument('lang', help='语言代码 (如 en, ja, zh-TW)')
    p_appname.add_argument('name', help='app_name 值')

    args = parser.parse_args()

    if args.command == 'add':
        return cmd_add(args.json_file, args.dry_run)
    elif args.command == 'list':
        return cmd_list()
    elif args.command == 'validate':
        return cmd_validate()
    elif args.command == 'remove':
        return cmd_remove(args.key)
    elif args.command == 'remove-batch':
        return cmd_remove_batch(args.json_file, args.keys, args.dry_run)
    elif args.command == 'app-name':
        return cmd_add_app_name(args.lang, args.name)
    else:
        parser.print_help()
        return 0


if __name__ == '__main__':
    sys.exit(main())
