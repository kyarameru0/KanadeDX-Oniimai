"""Validate the i18n setup: catalogues, generated code and source call sites.

- locales/<language>.json share one key set, placeholders and line counts (generate_locales.py);
- Msg.java / I18nCatalog.java are up to date;
- every message key is used by the code, and every Msg.X used exists;
- no UI text is hard-coded: string literals in module sources contain no Korean or Chinese
  (the generated catalogue and I18n's autonyms are the only exceptions);
- the retired two-language helpers (tr(ko, zh), UiText) are gone.
"""
import json,re,subprocess,sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'app/src/main/java/io/oniimai/kanade'
GENERATED={'I18nCatalog.java','Msg.java'}
ALLOWED_LITERALS={'I18n.java':{'English','한국어','简体中文','Language · 언어 · 语言','文'}}
TOKEN=re.compile(r'//[^\n]*|/\*.*?\*/|\'(?:[^\'\\]|\\.)*\'|"""(?:.|\n)*?"""|"(?:[^"\\\n]|\\.)*"',re.S)
CJK=re.compile('[가-힣ᄀ-ᇿ㄰-㆏㐀-鿿]')
USE=re.compile(r'\bMsg\.([A-Z][A-Z0-9_]*)\b')
LEGACY=re.compile(r'(?<![\w$])(?:UiText|UiTextCatalog)\b|(?<![\w$.])(?:GameUi\.)?tr\s*\(\s*"')


def literals(source):
    """String literals in Java/Kotlin source, ignoring comments and char literals."""
    for match in TOKEN.finditer(source):
        token=match.group()
        if token.startswith('"'):yield token


def inspect(name,source,constants):
    problems=[];used=set()
    masked=TOKEN.sub(lambda m:m.group() if m.group().startswith('"') else ' ',source)
    for token in literals(source):
        if CJK.search(token):
            value=token.strip('"')
            if value not in ALLOWED_LITERALS.get(name,set()):problems.append(f'{name}: hard-coded UI text {token[:60]}')
    for match in USE.finditer(masked):
        if match.group(1) not in constants:problems.append(f'{name}: unknown message Msg.{match.group(1)}')
        used.add(match.group(1))
    if LEGACY.search(masked):problems.append(f'{name}: legacy tr()/UiText call; use I18n.t(Msg.KEY)')
    return problems,used


def self_test():
    constants={'COMMON_SAVE'}
    ok,used=inspect('X.java','I18n.t(Msg.COMMON_SAVE); // "주석"\n/* "注释" */ String s="ASCII";',constants)
    assert not ok and used=={'COMMON_SAVE'}
    for bad in ['String s="저장";','String s="保存";','I18n.t(Msg.MISSING);','tr("a","b");','UiText.t("a");']:
        assert inspect('X.java',bad,constants)[0],bad
    assert not inspect('I18n.java','String[] n={"한국어","简体中文"};',constants)[0]


def main():
    self_test()
    subprocess.run([sys.executable,str(ROOT/'scripts/generate_locales.py'),'--check'],check=True)
    catalogue=json.loads((ROOT/'locales/en.json').read_text(encoding='utf-8'))
    constants={key.upper().replace('.','_') for key in catalogue}
    problems=[];used=set();files=0
    for path in sorted(SRC.glob('*')):
        if path.suffix not in('.java','.kt') or path.name in GENERATED:continue
        files+=1;found,names=inspect(path.name,path.read_text(encoding='utf-8'),constants)
        problems+=found;used|=names
    unused=sorted(constants-used)
    if unused:problems.append('unused message keys: '+', '.join(unused))
    if problems:raise SystemExit('\n'.join(problems))
    print(f'i18n: {len(constants)} keyed messages used across {files} sources; no hard-coded Korean/Chinese UI text')


if __name__=='__main__':main()
