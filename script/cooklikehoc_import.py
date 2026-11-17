#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
开源菜谱库 → 吃啥 CSV 转换器。

将两个开源菜谱仓库转换为 SmartWash「吃啥」菜谱导入 CSV（契约见
docs/吃啥菜谱库改造方案.md §四），产物落 static/dining/：

  - cooklikehoc.csv   CookLikeHOC 15 分类 + 酱料（无 LICENSE，产物仅本地导入，不入库）
  - howtocook.csv     HowToCook 10 分类（Public Domain）
  - ingredients.csv   配料主数据核对清单（导入时后端自动注册为主，此表仅核对）
  - migration.sql     幂等 ALTER 迁移脚本（配料两表 + 菜谱营养列）
  - covers/           封面图：CookLikeHOC 原名 / HowToCook htc_ 前缀
  - README.md         手动导入步骤说明

解析要点：
  - 节标题容错：配料/原料（±冒号/制表符）、步骤、营养成分（每 100g 表）
  - 供应商剥离：（来自…）/（供应商：…）整段删除；括号内含顿号列举（如调味酱配料构成）一并去掉主干括号
  - 克数挖掘：CookLikeHOC 从步骤文本挖（量词在名前）；HowToCook 从「计算」节直接解析
  - 同名菜去重：CookLikeHOC 优先（营养表+实拍图更全），HowToCook 仅补充缺失
"""

import argparse
import csv
import re
import shutil
import sys
import unicodedata
from pathlib import Path
from collections import OrderedDict

# ---------------------------------------------------------------------------
# 常量
# ---------------------------------------------------------------------------

CSV_COLUMNS = [
    "分类名", "菜名", "食材名列表", "食材明细列表", "步骤",
    "封面对象键", "封面链接",
    "热量每100g", "整份热量", "蛋白质g", "脂肪g", "碳水g", "钠mg",
]

# HowToCook 分类 → 吃啥分类名（与 CookLikeHOC 分类合并同名，condiment 并入酱料）
HOWTOCOOK_CATEGORY_MAP = {
    "aquatic": "水产",
    "breakfast": "早餐",
    "condiment": "酱料",
    "dessert": "甜品",
    "drink": "饮品",
    "meat_dish": "荤菜",
    "soup": "汤",
    "staple": "主食",
    "vegetable_dish": "素菜",
    "semi-finished": "半成品",
}

# 单位归一：写库口径 g / ml / 其余原样
UNIT_NORMALIZE = {"克": "g", "g": "g", "千克": "g", "kg": "g",
                  "毫升": "ml", "ml": "ml", "升": "ml", "L": "ml",
                  "大卡": "kcal", "千卡": "kcal"}
UNIT_SCALE_TO_G = {"g": 1.0, "kg": 1000.0}

# 克数/单位挖掘用（CookLikeHOC 步骤文本、HowToCook 计算节共用）
UNIT_PATTERN = r"(克|千克|毫升|升|g|kg|ml|L|个|根|片|包|瓣|条|块|只|盒|袋|斤|两|颗|枚|滴|碗|杯|把|勺|张|卷|份|段|丁|丝|粒|朵|杆|支|罐|瓶)"
AMOUNT_RE = re.compile(r"(\d+(?:\.\d+)?)\s*" + UNIT_PATTERN)

# 工具关键词（HowToCook「必备原料和工具」里剔除工具行/工具词）
TOOL_KEYWORDS = (
    "工具", "刀", "砧板", "锅", "铲", "勺子", "碗", "盘", "碟", "筷",
    "烤箱", "微波炉", "电饭煲", "空气炸锅", "厨房纸", "保鲜膜", "锡纸",
    "油纸", "打蛋器", "分蛋器", "榨汁机", "料理机", "滤网", "漏勺", "夹子",
    "剪刀", "削皮", "刷子", "牙签", "手套", "围裙", "杯子", "水壶", "电子秤",
    "厨房秤", "容器", "簸箕", "塑料杯", "乐扣", "量杯", "擀面杖", "磨具",
    "模具", "烤盘", "油壶", "调料瓶", "密封罐", "储物罐", "案板", "案刀",
    "削皮器", "刨丝器", "压蒜器", "蒜泥器", "搅拌器", "和面机", "面条机",
    "榨汁", "料理棒", "破壁机", "豆浆机", "咖啡机", "奶泡机", "冰块模具",
)

# 整句/备注特征词：配料名里出现即整条剔除（HowToCook 的「注：」「不要xx」「根据个人口味」等）
SENTENCE_HINTS = (
    "不要", "记得", "上午", "下午", "建议", "用于", "根据", "比如", "或者",
    "可选", "推荐", "产地", "品牌", "推介", "选用了", "选用", "均可", "即可",
    "左右", "适量", "少许", "若干", "个人口味", "让店家", "帮忙", "杀好",
    "吐沙", "沥干", "备用", "最后", "首先", "然后", "接着", "注：", "PS",
)

# 前导量词（HowToCook 计算节「1 袋 半成品意大利面」→ 半成品意大利面）
LEADING_QUANTITY_RE = re.compile(
    r"^\s*\d+(?:\.\d+)?\s*[-~]?\s*(?:袋|个|包|盒|瓶|罐|根|瓣|片|块|条|把|勺|杯|碗|份|段|张|卷|斤|两|只|颗|枚|粒|朵|支|杆|mL|ml|L|g|kg|克|毫升|升)\s*",
    re.IGNORECASE,
)
# 残留括号片段（跨行剥离后的「3-4 个）」「50g）」）
TRAILING_PAREN_RE = re.compile(r"^[)）]\s*$|^[（(][^）)]*[)）]$")

# 供应商标注（全角/半角括号）
SUPPLIER_RE = re.compile(r"[（(][^（）()]*?(?:来自|供应商)[^（）()]*?[)）]")

# 括号内为顿号列举且以"等"收尾 → 视作构成说明，剥离（如 宫保鸡丁调味酱（生姜、蒜、番茄酱、醋等））
COMPOSITION_RE = re.compile(r"[（(][^（）()]*、[^（）()]*等[)）]")

NUTRI_HEADER_ALIASES = {
    "热量": "kcal", "能量": "kcal",
    "蛋白质": "protein", "脂肪": "fat",
    "碳水化合物": "carbs", "碳水": "carbs", "钠": "sodium",
}

STEP_TITLE_RE = re.compile(r"^#{1,6}\s*(.*)$")
SKIP_FILE_NAMES = {"readme.md", "index.md", "template.md"}

MAX_NAME = 64
MAX_CATEGORY = 32
MAX_STEPS = 20000


# ---------------------------------------------------------------------------
# 工具函数
# ---------------------------------------------------------------------------

def norm(text):
    """归一化：NFKC + 去空白，用于去重/匹配。"""
    return unicodedata.normalize("NFKC", text or "").replace(" ", "").replace("　", "")


def clean_ingredient_name(raw):
    """配料名清洗（入库主数据质量闸门）：
    剥 md 标记/反引号 → 剥【所有】括注（厂家/产地/别称/构成/规格，一律取括号前主干）
    → 剥前导量词 → 斜杠/or 分支取第一段 → 整句/备注/工具/超长剔除。
    返回 None 表示该条应丢弃（脏数据，不进配料库）。"""
    name = (raw or "").strip().lstrip("-•·").strip()
    if not name:
        return None
    # md 语法与反引号
    name = re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", name)
    name = name.replace("`", "").replace("*", "")
    name = re.sub(r"^\[[^\]]*\]\s*", "", name)  # [可选] xxx → xxx（[可选] 本身也会被整句特征命中）
    # 剥所有括注（跨行已合并；括号内是厂家/产地/别称/构成/规格等说明，主干才是配料名）
    name = re.sub(r"[（(][^（）()]*[)）]", "", name)
    if TRAILING_PAREN_RE.match(name.strip()):
        return None
    # 前导量词（1 袋 半成品意大利面 → 半成品意大利面；500g/袋共三片 → 残留再过滤）
    name = LEADING_QUANTITY_RE.sub("", name)
    # 分支表达取第一段（黑虎虾 or 明虾 / 手工挂面/手擀面）
    name = re.split(r"\s+or\s+|/|、|，|,", name)[0]
    name = name.strip(" \t：:，,、；;。…等")

    # 质量闸门
    if not name or len(name) > 10:            # 中文配料名上限（宫保鸡丁调味酱 7 字）
        return None
    if any(ch.isdigit() for ch in name):      # 残留数字 = 量词未剥净或规格混入
        return None
    if any(w in name for w in SENTENCE_HINTS):  # 整句备注特征
        return None
    if is_tool_token(name):
        return None
    if any(ch in name for ch in "。；：、！？\"'“”‘’[]【】()（）"):
        return None
    return name


def clean_ingredient_line(raw):
    """整行清洗（先剥括号类标注与 md 链接，再按顿号切分），返回配料名列表。
    顺序关键：**必须先整行剥括号再切分**，否则 `调味酱（生姜、蒜、醋等）（来自xx）`
    会被顿号切碎括号，主干丢失且括号碎片混入（token 级剥括号已救不了切碎的括号）。"""
    line = (raw or "").strip().lstrip("-•·").strip()
    line = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", line)  # [剁椒酱](/配料/剁椒酱.md) → 剁椒酱
    line = line.replace("`", "").replace("*", "")
    line = re.sub(r"^\[[^\]]*\]\s*", "", line)
    # 整行剥所有括注（厂家/产地/别称/构成/规格）——切分前完成，保主干
    line = re.sub(r"[（(][^（）()]*[)）]", "", line)
    names = []
    for token in re.split(r"[、，,]", line):
        nm = clean_ingredient_name(token)
        if nm:
            names.append(nm)
    return names


def strip_title_suffix(title):
    """HowToCook 标题「红烧鲤鱼的做法」→「红烧鲤鱼」。"""
    title = title.strip()
    for suffix in ("的做法", "做法"):
        if title.endswith(suffix):
            title = title[: -len(suffix)]
    return title.strip()


def parse_amount(raw_amount):
    """'575g'/'2斤'/'3-4 个'/'大约 2 斤' → (value, unit) 或 None。"""
    if not raw_amount:
        return None
    text = raw_amount.replace("大约", "").replace("约", "").replace("左右", "").strip()
    m = re.match(r"(\d+(?:\.\d+)?)(?:\s*[-~到]\s*\d+(?:\.\d+)?)?\s*([克千克毫升升gkgmlL个根片包瓣条块只盒袋斤两颗枚滴碗杯把勺张卷份段丁丝粒朵杆支罐瓶]+)",
                 text)
    if not m:
        return None
    value = float(m.group(1))
    unit_raw = m.group(2)
    # 归一 g/ml；其余单位原样（个/根/…）
    unit = UNIT_NORMALIZE.get(unit_raw, unit_raw)
    if unit in UNIT_SCALE_TO_G and unit == "kg":
        value *= 1000.0
        unit = "g"
    return round(value, 2), unit


def mine_amount_from_steps(name, steps_text):
    """CookLikeHOC：在步骤文本中挖配料克数。量词在名前（'200g 大豆油'）优先，
    允许名后缀（块/片/丁/丝/末/条/段/液/泥/蓉/碎/粒/花/皮/粉）。首个命中即返回。"""
    if not steps_text or not name:
        return None
    tokens = AMOUNT_RE.finditer(steps_text)
    suffixes = "块片丁丝末条段液泥蓉碎粒花皮粉酱"
    for m in tokens:
        after = steps_text[m.end(): m.end() + 12]
        after = re.split(r"[，。；、！？\n]", after)[0]
        after_norm = norm(after)
        name_norm = norm(name)
        if after_norm.startswith(name_norm) or (
            after_norm.startswith(name_norm[:2]) and len(after_norm) <= len(name_norm) + 2
            and after_norm[len(name_norm):len(name_norm) + 1] in suffixes
        ):
            return parse_amount(m.group(1) + m.group(2))
        # 名前有修饰短语（如 '下485g 土鸡块'）——after 开头即命中已覆盖；再试 before 窗口
        before = steps_text[max(0, m.start() - 14): m.start()]
        if name_norm and name_norm in norm(before.split("，")[-1].split("。")[-1]):
            # '下 485g 土鸡' 命中，但 before 是数字前的动词——仅当 before 里含名字才判命中
            pass
    return None


def parse_sections(lines):
    """把 md 行切成 {节标题: [正文行]}，节标题容错 '## 配料：' / '## 原料\t'。
    只有 h1/h2 开新节；h3+（'### 1. 剁椒鸡丁炒制'、'### 最终步骤'）是步骤内部子标题，
    归入当前节正文——否则步骤节被切空。
    正文行先做跨行括号合并：'黄瓜（湖北广鹏嘉业' + '江苏甜甜雨）' 拼一行再剥，
    否则跨行括注（厂家/产地）剥离失败混进配料名。"""
    sections = OrderedDict()
    current = None
    pending = None  # 括号未闭合的行缓冲
    for line in lines:
        if re.match(r"^#{1,2}\s", line):
            title = norm(line.lstrip("#").strip()).rstrip(":：\t ")
            current = title
            sections.setdefault(current, [])
            pending = None
            continue
        text = pending + " " + line.strip() if pending else line
        pending = None
        open_cnt = text.count("（") + text.count("(")
        close_cnt = text.count("）") + text.count(")")
        if open_cnt > close_cnt:
            pending = text
            continue
        if current is not None:
            sections[current].append(text)
        else:
            sections.setdefault("_preamble", []).append(text)
    if pending:
        target = sections[current] if current is not None else sections.setdefault("_preamble", [])
        target.append(pending)
    return sections


def bullets(section_lines):
    """取节内 bullet 行（- 开头），去标记。"""
    out = []
    for line in section_lines:
        s = line.strip()
        if s.startswith(("-", "*", "•")):
            out.append(s.lstrip("-*•").strip())
    return out


def parse_nutrition_table(section_lines):
    """营养成分表（| 项目 | 每 100g 含量 |）→ {alias: value}。"""
    result = {}
    for line in section_lines:
        s = line.strip()
        if not s.startswith("|") or set(s) <= {"|", " ", "-", ":"}:
            continue
        cells = [c.strip() for c in s.strip("|").split("|")]
        if len(cells) < 2:
            continue
        label = norm(cells[0])
        for alias, key in NUTRI_HEADER_ALIASES.items():
            if label.startswith(alias):
                m = re.search(r"(\d+(?:\.\d+)?)", cells[1])
                if m:
                    result[key] = float(m.group(1))
                break
    return result


def join_steps(section_lines):
    """步骤节 → 多行文本（保留序号）。"""
    steps = []
    for line in section_lines:
        s = line.strip()
        if not s or s.startswith(("<!--", "```")):
            continue
        steps.append(s)
    return "\n".join(steps)[:MAX_STEPS]


# ---------------------------------------------------------------------------
# CookLikeHOC 解析
# ---------------------------------------------------------------------------

def parse_cooklikehoc(root, cover_objects):
    """CookLikeHOC → recipes list + ingredient union。cover_objects: {菜名: 对象键}。"""
    recipes = []
    ingredients = set()
    skip_dirs = {"images", "docs", "docker_support", ".vitepress", ".git"}

    for cat_dir in sorted(p for p in root.iterdir() if p.is_dir() and p.name not in skip_dirs):
        category = "酱料" if cat_dir.name == "配料" else cat_dir.name[:MAX_CATEGORY]
        for md_path in sorted(cat_dir.glob("*.md")):
            if md_path.name.lower() in SKIP_FILE_NAMES:
                continue
            text = md_path.read_text(encoding="utf-8", errors="replace")
            if "AUTO-GENERATED" in text[:400]:
                continue
            sections = parse_sections(text.splitlines())
            # 菜名：一级标题（去「的做法」），兜底文件名
            title = None
            for line in text.splitlines():
                if line.startswith("# ") and not line.startswith("## "):
                    title = line[2:].strip()
                    break
            name = strip_title_suffix(title or md_path.stem)
            if not name or len(name) > MAX_NAME:
                continue

            # 配料/原料节（容错 '配料'/'原料' 前缀；酱料配方用「已知成分」节）
            raw_ingredients = []
            for key, lines in sections.items():
                if key.startswith("配料") or key.startswith("原料") or key.startswith("已知成分") or key.startswith("成分"):
                    raw_ingredients = bullets(lines)
            cleaned = []
            for raw in raw_ingredients:
                # 整行先剥（供应商/构成）括号再切顿号，防括号被切碎
                for nm in clean_ingredient_line(raw):
                    cleaned.append(nm)
                    ingredients.add(nm)

            # 步骤
            steps = ""
            for key, lines in sections.items():
                if key.startswith("步骤") or key.startswith("做法"):
                    steps = join_steps(lines)

            # 营养成分（每 100g）
            nutri = {}
            for key, lines in sections.items():
                if key.startswith("营养成分") or key.startswith("营养"):
                    nutri = parse_nutrition_table(lines)

            # 克数挖掘：配料名在步骤文本中的量化
            detail_pairs = []
            for nm in cleaned:
                amt = mine_amount_from_steps(nm, steps) if steps else None
                if amt:
                    detail_pairs.append(f"{nm}:{amt[0]}{amt[1]}")
                else:
                    detail_pairs.append(f"{nm}:")

            recipes.append({
                "category": category,
                "name": name,
                "ingredients": cleaned,
                "detail": detail_pairs,
                "steps": steps,
                "coverKey": cover_objects.get(norm(name), ""),
                "kcal100": nutri.get("kcal"),
                "kcalTotal": None,
                "protein": nutri.get("protein"),
                "fat": nutri.get("fat"),
                "carbs": nutri.get("carbs"),
                "sodium": nutri.get("sodium"),
                "source": "CookLikeHOC",
            })
    return recipes, ingredients


# ---------------------------------------------------------------------------
# HowToCook 解析
# ---------------------------------------------------------------------------

def is_tool_token(token):
    n = norm(token)
    return any(kw in n for kw in TOOL_KEYWORDS)


def parse_howtocook(root, cover_objects, existing_names):
    """HowToCook → recipes（跳过与 CookLikeHOC 同名者）+ ingredient union。"""
    recipes = []
    ingredients = set()
    dishes_root = root / "dishes"
    if not dishes_root.is_dir():
        return recipes, ingredients

    for cat_dir in sorted(p for p in dishes_root.iterdir() if p.is_dir()):
        if cat_dir.name == "template":
            continue
        category = HOWTOCOOK_CATEGORY_MAP.get(cat_dir.name, cat_dir.name)[:MAX_CATEGORY]
        for md_path in sorted(cat_dir.rglob("*.md")):
            if md_path.name.lower() in SKIP_FILE_NAMES:
                continue
            text = md_path.read_text(encoding="utf-8", errors="replace")
            title = None
            for line in text.splitlines():
                if line.startswith("# ") and not line.startswith("## "):
                    title = line[2:].strip()
                    break
            name = strip_title_suffix(title or md_path.stem)
            if not name or len(name) > MAX_NAME:
                continue
            if norm(name) in existing_names:
                continue  # CookLikeHOC 优先，跳过同名

            sections = parse_sections(text.splitlines())

            # 预估卡路里（整份口径）
            kcal_total = None
            m = re.search(r"预估卡路里[:：]\s*(\d+(?:\.\d+)?)", text)
            if m:
                kcal_total = float(m.group(1))

            # 必备原料和工具 → 配料名（整行先剥括号再切分，剔工具）
            raw_ingredients = []
            for key, lines in sections.items():
                if "必备原料" in key or key.startswith("原料") or key.startswith("配料"):
                    raw_ingredients = bullets(lines)
            cleaned = []
            for raw in raw_ingredients:
                for nm in clean_ingredient_line(raw):
                    if not is_tool_token(nm):
                        cleaned.append(nm)
            cleaned = list(dict.fromkeys(cleaned))  # 保序去重
            ingredients.update(cleaned)

            # 计算节 → 用量（括号修饰词先剥离再解析：'- 鲤鱼 （大约 2 斤）'）
            amounts = {}
            for key, lines in sections.items():
                if key.startswith("计算"):
                    for line in bullets(lines):
                        stripped = re.sub(r"[（(][^）)]*[)）]", " ", line).strip()
                        m = re.match(r"^(.+?)\s+([\d.]+(?:\s*[-~到]\s*[\d.]+)?)\s*(克|千克|g|kg|毫升|ml|升|L|个|根|片|包|瓣|条|块|只|盒|袋|斤|两|颗|枚|滴|碗|杯|把|勺|张|卷|份|段|丁|丝|粒|朵|罐|瓶)\s*$",
                                     stripped)
                        if m:
                            nm = clean_ingredient_name(m.group(1))
                            amt = parse_amount(m.group(2) + m.group(3))
                            if nm and amt:
                                amounts[norm(nm)] = amt
                        else:
                            nm = clean_ingredient_name(stripped)
                            if nm and not is_tool_token(nm) and norm(nm) not in amounts:
                                amounts[norm(nm)] = None
                if amounts:
                    break

            # 步骤（操作节）
            steps = ""
            for key, lines in sections.items():
                if key.startswith("操作") or key.startswith("做法") or key.startswith("步骤"):
                    steps = join_steps(lines)

            # 明细：用量优先，无用量列名
            detail_pairs = []
            for nm in cleaned:
                amt = amounts.get(norm(nm))
                detail_pairs.append(f"{nm}:{amt[0]}{amt[1]}" if amt else f"{nm}:")

            recipes.append({
                "category": category,
                "name": name,
                "ingredients": cleaned,
                "detail": detail_pairs,
                "steps": steps,
                "coverKey": cover_objects.get(norm("htc_" + name), ""),
                "kcal100": None,
                "kcalTotal": kcal_total,
                "protein": None, "fat": None, "carbs": None, "sodium": None,
                "source": "HowToCook",
            })
    return recipes, ingredients


# ---------------------------------------------------------------------------
# 封面对象键与产物
# ---------------------------------------------------------------------------

def collect_cover_objects(cook_root, how_root, covers_dir):
    """收集封面：CookLikeHOC images/<菜名>.(jpg|png) 原名拷贝；HowToCook 菜目录图 htc_ 前缀拷贝。
    返回 {菜名/htc_菜名: 对象键}。"""
    covers_dir.mkdir(parents=True, exist_ok=True)
    objects = {}

    hoc_images = cook_root / "images"
    if hoc_images.is_dir():
        for img in sorted(hoc_images.iterdir()):
            if img.suffix.lower() in (".jpg", ".jpeg", ".png", ".webp"):
                dish_name = img.stem
                dest = covers_dir / img.name
                if not dest.exists():
                    shutil.copy2(img, dest)
                # 全半角括号等价归一（农家小炒肉(鸡蛋干版本).png ↔ 农家小炒肉（鸡蛋干版本））
                objects[norm(dish_name)] = f"dining/covers/{img.name}"

    if (how_root / "dishes").is_dir():
        for md_path in sorted((how_root / "dishes").rglob("*.md")):
            dish_dir = md_path.parent
            if dish_dir.name == "template" or dish_dir == dish_dir.parent:
                continue
            title = None
            try:
                for line in md_path.read_text(encoding="utf-8", errors="replace").splitlines()[:5]:
                    if line.startswith("# "):
                        title = line[2:].strip()
                        break
            except OSError:
                continue
            name = strip_title_suffix(title or md_path.stem)
            img = None
            for candidate in (f"{name}.jpg", f"{name}.png", "成品.jpg", "成品.png"):
                p = dish_dir / candidate
                if p.exists():
                    img = p
                    break
            if img is None:
                imgs = [p for p in dish_dir.iterdir()
                        if p.suffix.lower() in (".jpg", ".jpeg", ".png") and p.is_file()] if dish_dir.is_dir() else []
                img = sorted(imgs)[0] if imgs else None
            if img is not None:
                dest_name = f"htc_{name}{img.suffix.lower()}"
                dest = covers_dir / dest_name
                if not dest.exists():
                    shutil.copy2(img, dest)
                objects[norm(f"htc_{name}")] = f"dining/covers/{dest_name}"
    return objects


INGREDIENT_CATEGORY_RULES = [
    ("SEASONING", ("油", "盐", "糖", "酱", "醋", "料酒", "鸡精", "味精", "椒", "粉", "汁", "料",
                   "醋", "生抽", "老抽", "蚝油", "香油", "麻油", "豆瓣酱", "番茄酱", "淀粉",
                   "胡椒", "八角", "桂皮", "香叶", "花椒", "辣椒", "孜然", "芥末", "蜂蜜")),
    ("MAIN", ("肉", "蛋", "鸡", "牛", "猪", "羊", "鱼", "虾", "蟹", "鸭", "贝", "排", "翅",
              "丸", "火腿", "培根", "豆腐", "奶", "奶酪")),
]


def guess_ingredient_category(name):
    n = norm(name)
    for cat, keywords in INGREDIENT_CATEGORY_RULES:
        if any(kw in n for kw in keywords):
            return cat
    return "AUX" if any(kw in n for kw in ("菜", "菇", "笋", "瓜", "茄", "葱", "姜", "蒜", "豆", "米", "面", "粉条")) else "OTHER"


CATEGORY_CN = {"MAIN": "主料", "AUX": "辅料", "SEASONING": "调料", "OTHER": "其他"}


def write_csvs(out_dir, hoc_recipes, htc_recipes, all_ingredients):
    out_dir.mkdir(parents=True, exist_ok=True)

    def rows_of(recipes):
        rows = []
        for r in recipes:
            # 无实拍图 → 分类占位图兜底（PS 生成，category_<分类名>.png），四端展示不空窗
            cover = r["coverKey"] or f"dining/covers/category_{r['category']}.png"
            rows.append({
                "分类名": r["category"],
                "菜名": r["name"],
                "食材名列表": "、".join(r["ingredients"]),
                "食材明细列表": "、".join(r["detail"]),
                "步骤": r["steps"],
                "封面对象键": cover,
                "封面链接": "",
                "热量每100g": r["kcal100"] if r["kcal100"] is not None else "",
                "整份热量": r["kcalTotal"] if r["kcalTotal"] is not None else "",
                "蛋白质g": r["protein"] if r["protein"] is not None else "",
                "脂肪g": r["fat"] if r["fat"] is not None else "",
                "碳水g": r["carbs"] if r["carbs"] is not None else "",
                "钠mg": r["sodium"] if r["sodium"] is not None else "",
            })
        return rows

    def write_one(filename, recipes):
        path = out_dir / filename
        with path.open("w", encoding="utf-8-sig", newline="") as f:
            writer = csv.DictWriter(f, fieldnames=CSV_COLUMNS)
            writer.writeheader()
            writer.writerows(rows_of(recipes))
        return path

    p1 = write_one("cooklikehoc.csv", hoc_recipes)
    p2 = write_one("howtocook.csv", htc_recipes)

    ing_path = out_dir / "ingredients.csv"
    with ing_path.open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["配料名", "分类枚举", "分类中文"])
        for name in sorted(all_ingredients):
            cat = guess_ingredient_category(name)
            writer.writerow([name, cat, CATEGORY_CN[cat]])

    return p1, p2, ing_path


def write_migration_sql(out_dir):
    sql = """-- 吃啥配料库 + 营养列 迁移脚本（幂等，MySQL 8）
-- 用法：mysql -u<user> -p smartwash < static/dining/migration.sql
-- 说明：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，用 information_schema 条件拼 SQL 实现幂等。

-- 1) 配料库两表（CREATE TABLE IF NOT EXISTS 天然幂等）
CREATE TABLE IF NOT EXISTS `dining_ingredient` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '配料 ID',
  `name`       VARCHAR(32) NOT NULL COMMENT '配料名（唯一；供应商信息不入库）',
  `category`   VARCHAR(16) NOT NULL DEFAULT 'OTHER' COMMENT '配料分类：MAIN=主料/AUX=辅料/SEASONING=调料/OTHER=其他',
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='吃啥-配料库';

CREATE TABLE IF NOT EXISTS `dining_recipe_ingredient` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '关联 ID',
  `recipe_id`     BIGINT        NOT NULL COMMENT '菜谱 ID(dining_recipe.id)',
  `ingredient_id` BIGINT        NOT NULL COMMENT '配料 ID(dining_ingredient.id)',
  `amount_value`  DECIMAL(10,2) NULL COMMENT '数量数值（如 575g -> 575），NULL=来源未给出',
  `amount_unit`   VARCHAR(16)   NULL COMMENT '单位（g/kg/ml/L/个/根/片/包/勺等），NULL=未给出',
  `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recipe_ingredient` (`recipe_id`,`ingredient_id`),
  KEY `idx_ingredient` (`ingredient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='吃啥-菜谱配料关联（买菜清单聚合依据）';

-- 2) dining_recipe 营养列（存在即跳过）
SET @schema := DATABASE();
SET @add := (
  SELECT CONCAT('ALTER TABLE `dining_recipe` ',
    CONCAT_WS(', ',
      IF(NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='dining_recipe' AND COLUMN_NAME='calories_per_100g'), 'ADD COLUMN `calories_per_100g` DECIMAL(8,2) NULL COMMENT \'热量 Kcal/100g（CookLikeHOC 营养表口径）\'', NULL),
      IF(NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='dining_recipe' AND COLUMN_NAME='calories_total'), 'ADD COLUMN `calories_total` DECIMAL(10,2) NULL COMMENT \'整份估算热量 Kcal（HowToCook 预估卡路里口径）\'', NULL),
      IF(NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='dining_recipe' AND COLUMN_NAME='protein_g'), 'ADD COLUMN `protein_g` DECIMAL(6,2) NULL COMMENT \'蛋白质 g/100g\'', NULL),
      IF(NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='dining_recipe' AND COLUMN_NAME='fat_g'), 'ADD COLUMN `fat_g` DECIMAL(6,2) NULL COMMENT \'脂肪 g/100g\'', NULL),
      IF(NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='dining_recipe' AND COLUMN_NAME='carbs_g'), 'ADD COLUMN `carbs_g` DECIMAL(6,2) NULL COMMENT \'碳水化合物 g/100g\'', NULL),
      IF(NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='dining_recipe' AND COLUMN_NAME='sodium_mg'), 'ADD COLUMN `sodium_mg` DECIMAL(8,2) NULL COMMENT \'钠 mg/100g\'', NULL)
    ))
);
SET @stmt := IF(@add IS NOT NULL AND @add <> '', @add, 'SELECT ''dining_recipe 营养列已存在，跳过''');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;
"""
    path = out_dir / "migration.sql"
    path.write_text(sql, encoding="utf-8")
    return path


def write_readme(out_dir, stats):
    readme = f"""# 吃啥菜谱库导入材料（本目录不入库，含无 LICENSE 内容）

生成时间：{stats['generated_at']}
来源：CookLikeHOC（无 LICENSE，仅本地使用）× HowToCook（Public Domain）

## 产物

| 文件 | 说明 |
|---|---|
| cooklikehoc.csv | {stats['hoc_rows']} 行（15 分类 + 酱料），营养表口径 |
| howtocook.csv | {stats['htc_rows']} 行（10 分类），整份热量口径 |
| ingredients.csv | {stats['ingredient_count']} 个配料（核对用，导入自动注册为主） |
| migration.sql | 幂等迁移脚本（配料两表 + 菜谱营养 6 列） |
| covers/ | {stats['cover_count']} 张封面图（CookLikeHOC 原名 / HowToCook htc_ 前缀） |

## 导入步骤

1. **先跑迁移**：`mysql -u<user> -p <库名> < static/dining/migration.sql`
2. **Web 后台 → 吃啥管理 → 菜谱导入**：上传 cooklikehoc.csv → 预览核对 → 确认导入（≤500 行/文件，两个文件分别导入）
3. 同法导入 howtocook.csv
4. **封面**：本目录 covers/ 的图已由脚本直传 MinIO（若上传脚本已执行）；否则在 Web 后台菜谱编辑中逐张上传（对象键规则 `dining/covers/<文件名>`）
5. **校验**：菜谱库抽查营养徽标与封面；发起一单试算买菜清单聚合与人均卡路里

## 口径说明

- 一份热量 = 整份热量（HowToCook）?: 热量每100g × 250g（份量假设）
- 买菜清单按（配料名, 归一单位）聚合求和；无用量配料仅按名去重
"""
    path = out_dir / "README.md"
    path.write_text(readme, encoding="utf-8")
    return path


# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description="开源菜谱库 → 吃啥 CSV 转换器")
    parser.add_argument("--cooklikehoc", default=str(Path.home() / "Desktop/work/CookLikeHOC"))
    parser.add_argument("--howtocook", default=str(Path.home() / "Desktop/work/HowToCook"))
    parser.add_argument("--out", default="static/dining")
    parser.add_argument("--no-covers", action="store_true", help="跳过封面拷贝")
    args = parser.parse_args()

    cook_root = Path(args.cooklikehoc)
    how_root = Path(args.howtocook)
    out_dir = Path(args.out)
    covers_dir = out_dir / "covers"

    cover_objects = {} if args.no_covers else collect_cover_objects(cook_root, how_root, covers_dir)

    hoc_recipes, hoc_ingredients = parse_cooklikehoc(cook_root, cover_objects)
    hoc_names = {norm(r["name"]) for r in hoc_recipes}
    htc_recipes, htc_ingredients = parse_howtocook(how_root, cover_objects, hoc_names)

    all_ingredients = sorted(hoc_ingredients | htc_ingredients)

    import datetime
    stats = {
        "generated_at": datetime.datetime.now().strftime("%Y-%m-%d %H:%M"),
        "hoc_rows": len(hoc_recipes),
        "htc_rows": len(htc_recipes),
        "ingredient_count": len(all_ingredients),
        "cover_count": len(list(covers_dir.iterdir())) if covers_dir.is_dir() else 0,
    }

    p1, p2, p3 = write_csvs(out_dir, hoc_recipes, htc_recipes, all_ingredients)
    p4 = write_migration_sql(out_dir)
    p5 = write_readme(out_dir, stats)

    # 行数约束检查（导入器 500 行上限）
    for p, n in ((p1, len(hoc_recipes)), (p2, len(htc_recipes))):
        if n > 500:
            print(f"[警告] {p.name} 有 {n} 行，超过导入器 500 行上限，需拆分", file=sys.stderr)

    kcal100 = sum(1 for r in hoc_recipes if r["kcal100"] is not None)
    kcal_total = sum(1 for r in htc_recipes if r["kcalTotal"] is not None)
    print(f"[完成] CookLikeHOC {stats['hoc_rows']} 行（营养表 {kcal100} 行）→ {p1}")
    print(f"[完成] HowToCook {stats['htc_rows']} 行（整份热量 {kcal_total} 行）→ {p2}")
    print(f"[完成] 配料 {stats['ingredient_count']} 个 → {p3}")
    print(f"[完成] 迁移脚本 → {p4}")
    print(f"[完成] 封面 {stats['cover_count']} 张 → {covers_dir}")
    print(f"[完成] 导入说明 → {p5}")


if __name__ == "__main__":
    main()
