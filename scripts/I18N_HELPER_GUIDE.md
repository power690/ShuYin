# i18n_helper.py 使用说明

鼠音 App 多语言字符串批量管理工具。

## 一、工具用途

这个脚本用于一键管理 `Strings.kt` 中的多语言字符串，避免手动编辑 3500+ 行的 Kotlin 文件出错。

**主要功能：**
- **add** — 从 JSON 文件批量添加新字符串（支持一次添加多个 key，每个 key 可指定任意语言翻译）
- **list** — 列出所有现有 key 及其语言覆盖情况
- **validate** — 验证 Strings.kt 语法正确性和语言覆盖完整性
- **remove** — 删除指定单个 key
- **remove-batch** — 批量删除多个 key（支持 JSON 文件、命令行 key、或两者组合）
- **app-name** — 更新某语言的桌面图标名（values-xx/strings.xml 中的 app_name）

## 二、环境要求

- Python 3.6+
- 无需安装任何第三方库（只用标准库）

## 三、快速开始

### 1. 添加新字符串

创建一个 JSON 文件（如 `new_strings.json`），格式如下：

```json
{
    "greeting": {
        "zh": "你好",
        "en": "Hello",
        "ja": "こんにちは"
    },
    "song_count": {
        "zh": "共 %1$d 首",
        "en": "%1$d songs"
    }
}
```

然后运行：

```bash
cd ShuYin/scripts
python3 i18n_helper.py add new_strings.json
```

脚本会自动把这两个 key 添加到 `Strings.kt` 末尾（`Strings.kt` 内部按分段组织，见下文规则说明）。

### 2. 试运行（推荐）

实际修改前先试运行，看看会添加什么：

```bash
python3 i18n_helper.py add new_strings.json --dry-run
```

试运行不会修改任何文件。

### 3. 验证结果

添加后验证完整性：

```bash
python3 i18n_helper.py validate
```

### 4. 在代码中使用新 key

```kotlin
// Composable 中
Text(Strings.get("greeting"))

// 带参数
Text(Strings.get("song_count", 42))

// 非 Composable（Service/Manager）
val msg = com.xiaowei.player.i18n.Strings.get("greeting")
```

## 四、命令详解

### `add` — 批量添加字符串

```bash
python3 i18n_helper.py add <json_file> [--dry-run]
```

**JSON 文件格式：**

```json
{
    "key_name": {
        "zh": "中文",
        "zh-TW": "繁體",
        "en": "English",
        "ja": "日本語",
        ...
    }
}
```

**规则：**
- key 名必须以小写字母开头，只允许小写字母、数字、下划线（如 `song_count`、`tab_home`）
- 每个 key 至少提供 `zh` 或 `en` 翻译中的一个
- 未提供翻译的语言会在运行时自动回退到英文，英文没有回退到中文
- 如果 key 已存在，会更新它的翻译（覆盖）
- 字符串中的 `$`、`"`、`\`、换行符会自动转义，无需手动处理
- 占位符 `%1$s`、`%1$d` 直接写，脚本会正确转义 `$`
- `Strings.kt` 采用分段结构：内部由多个 `buildStringsPart1()`、`buildStringsPart2()`… 分段函数组成（每段最多 29 个 key，防止超过 JVM 64KB 单方法上限），并由 `ALL_STRINGS` 汇总行合并。add 会自动追加到最后一个分段末尾，分段满了自动新建分段并更新汇总行——使用者无需关心分段

**示例输出：**
```
准备添加 3 个字符串 key
源文件: new_strings.json

  + 添加 "greeting" (3 种语言)
  + 添加 "song_count" (2 种语言)

OK 已写入 .../Strings.kt
  添加/更新: 2
  跳过: 0
```

### `list` — 列出所有 key

```bash
python3 i18n_helper.py list
```

输出示例：
```
Strings.kt 共有 90 个 key:

  album                          [37种语言] 专辑
  album_cover                    [37种语言] 封面
  all_songs                      [37种语言] 全部歌曲
  app_name                       [37种语言] 鼠音
  ...
```

### `validate` — 验证完整性

```bash
python3 i18n_helper.py validate
```

检查：
- 语法错误（引号/括号不匹配）
- 每个 key 的语言覆盖情况（哪些 key 缺少哪些语言）

输出示例：
```
Strings.kt 验证报告
==================================================
总 key 数: 90
支持语言数: 37

OK 语法检查通过

语言覆盖检查:
  OK 所有 key 都包含全部 37 种语言翻译
```

### `remove` — 删除单个 key

```bash
python3 i18n_helper.py remove <key_name>
```

若删除后某个分段变为空，脚本会自动清理该分段函数及汇总行引用，不留死代码。

示例：
```bash
python3 i18n_helper.py remove greeting
```

### `remove-batch` — 批量删除多个 key

支持三种方式（可组合使用）：

**方式 1：从 JSON 文件批量删除**

JSON 文件支持两种格式：

数组格式（推荐，简洁）：
```json
["key1", "key2", "key3"]
```

对象格式（兼容 add 命令的 JSON 格式，会自动提取 key 名，翻译内容忽略）：
```json
{
    "key1": {},
    "key2": {}
}
```

```bash
python3 i18n_helper.py remove-batch keys_to_delete.json
```

**方式 2：命令行直接传 key 名**

```bash
python3 i18n_helper.py remove-batch key1 key2 key3
```

**方式 3：JSON 文件 + 命令行 key 组合**

```bash
python3 i18n_helper.py remove-batch keys_to_delete.json extra_key another_key
```

**试运行（推荐先试运行确认）：**
```bash
python3 i18n_helper.py remove-batch keys_to_delete.json --dry-run
```

**示例输出：**
```
准备删除 3 个 key
源文件: keys_to_delete.json

  - 删除 "greeting"
  - 删除 "farewell"
  ? 跳过 "old_key" (不存在)

OK 已写入 .../Strings.kt
  删除: 2
  未找到: 1
```

### `app-name` — 更新桌面图标名

```bash
python3 i18n_helper.py app-name <lang> <name>
```

更新 `values-<lang>/strings.xml` 中的 `app_name`（桌面启动器显示的 App 名称）。

示例：
```bash
python3 i18n_helper.py app-name ja シューイン
python3 i18n_helper.py app-name en ShuYin
python3 i18n_helper.py app-name zh-TW 鼠音
```

## 五、支持的语言（37 种）

| 语言代码 | 语言 | 语言代码 | 语言 |
|---------|------|---------|------|
| zh | 中文（简体）| pl | 波兰语 |
| zh-TW | 中文（台湾繁体）| uk | 乌克兰语 |
| zh-HK | 中文（香港繁体）| nl | 荷兰语 |
| zh-MO | 中文（澳门繁体）| sv | 瑞典语 |
| en | 英语 | cs | 捷克语 |
| ja | 日语 | hu | 匈牙利语 |
| ko | 韩语 | el | 希腊语 |
| fr | 法语 | ro | 罗马尼亚语 |
| de | 德语 | fi | 芬兰语 |
| es | 西班牙语 | da | 丹麦语 |
| ru | 俄语 | nb | 挪威语 |
| pt | 葡萄牙语 | ms | 马来语 |
| it | 意大利语 | tl | 他加禄语（菲律宾）|
| ar | 阿拉伯语 | ug | 维吾尔语 |
| hi | 印地语 | mn | 蒙古语 |
| th | 泰语 | fa | 波斯语 |
| vi | 越南语 | ur | 乌尔都语 |
| in | 印尼语 | bn | 孟加拉语 |
| tr | 土耳其语 | | |

## 六、完整工作流示例

假设要添加一个"设置"相关的字符串，包含 3 种语言：

### 步骤 1：创建 JSON 文件

```bash
cat > /tmp/settings_strings.json << 'EOF'
{
    "settings_title": {
        "zh": "设置",
        "en": "Settings",
        "ja": "設定"
    },
    "settings_dark_mode": {
        "zh": "深色模式",
        "en": "Dark Mode",
        "ja": "ダークモード"
    }
}
EOF
```

### 步骤 2：试运行

```bash
cd ShuYin/scripts
python3 i18n_helper.py add /tmp/settings_strings.json --dry-run
```

确认输出正确。

### 步骤 3：实际添加

```bash
python3 i18n_helper.py add /tmp/settings_strings.json
```

### 步骤 4：验证

```bash
python3 i18n_helper.py validate
```

### 步骤 5：编译 APK

```bash
cd ShuYin
./gradlew :app:assembleDebug
```

### 步骤 6：在代码中使用

```kotlin
// 在 Composable 中
Text(Strings.get("settings_title"))

// 在 Service/Manager 中
val title = com.xiaowei.player.i18n.Strings.get("settings_title")
```

## 七、注意事项

1. **运行前备份**：脚本会直接修改 `Strings.kt`，建议用 Git 管理或手动备份。

2. **手工编辑注意**：`Strings.kt` 的字符串存放在 `buildStringsPart1~N()` 分段函数内，`ALL_STRINGS` 那一行只是各分段的汇总引用——手工加 key 时应加到最后一个分段的 `mapOf(...)` 末尾，不要加到汇总行；拿不准就用 `add` 命令代替手工编辑。

3. **key 命名规范**：
   - 用小写字母 + 下划线（snake_case）
   - 按功能分组前缀，如 `tab_`、`search_`、`playlist_`、`mine_`
   - 名称要有意义，避免 `a`、`b`、`tmp` 这种

4. **占位符**：
   - 用 `%1$s`、`%1$d`、`%2$s` 等格式
   - `$` 字符脚本会自动转义，直接写即可
   - 调用时用 `Strings.get("key", arg1, arg2)`

5. **JSON 文件编码**：必须用 UTF-8 编码（默认就是）。

6. **添加新语言**：如果要新增一种语言（如希伯来语 `he`）：
   - 在 `Strings.kt` 的 `SUPPORTED_LANGS` 中加 `"he"`
   - 在 `i18n_helper.py` 的 `ALL_LANGS` 列表中加 `"he"`
   - 给每个 key 添加 `"he"` 翻译（可用脚本批量添加）
   - 创建 `values-he/strings.xml` 放 app_name

## 八、常见问题

### Q: 添加后编译报错怎么办？

A: 运行 `python3 i18n_helper.py validate` 检查语法。常见问题：
- JSON 文件中的字符串包含未转义的双引号 → 脚本会自动转义，但 JSON 本身需要转义
- key 名包含大写字母或特殊字符 → 改成小写+下划线

### Q: 如何批量给已有的 key 补全缺失语言？

A: 创建一个 JSON 文件，里面是已存在的 key 加上新的语言翻译，然后 `add`。脚本会更新（覆盖）该 key。

### Q: 添加后 App 内不显示新字符串？

A: 检查代码中是否用 `Strings.get("key")` 引用，而不是直接写中文字符串。

### Q: 如何删除一批 key？

A: 用 `remove-batch` 命令批量删除，支持三种方式：

```bash
# 方式 1: 命令行直接传多个 key
python3 i18n_helper.py remove-batch key1 key2 key3

# 方式 2: 从 JSON 文件（数组格式）
echo '["key1", "key2", "key3"]' > to_delete.json
python3 i18n_helper.py remove-batch to_delete.json

# 方式 3: 组合（JSON 文件 + 命令行 key）
python3 i18n_helper.py remove-batch to_delete.json extra_key

# 先试运行确认
python3 i18n_helper.py remove-batch to_delete.json --dry-run
```

也可以逐个用 `remove` 命令删除：`python3 i18n_helper.py remove <key>`
