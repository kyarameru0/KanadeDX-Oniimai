"""Validate both UiText's catalogue and inline tr(Korean, Simplified Chinese) pairs."""
import json,re,subprocess,sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
# Read Java literals while ignoring comments and character literals. Preserve source offsets.
TOKEN=re.compile(r'//[^\n]*|/\*.*?\*/|\'(?:[^\'\\]|\\.)*\'|"(?:[^"\\]|\\.)*"',re.S)
HANGUL=re.compile('[가-힣]')
TR_CALL=re.compile(r'(?<![\w$])(?:[A-Za-z_$][\w$]*\.)?tr\s*\(\s*$')
CAT_CALL=re.compile(r'(?<![\w$])UiText\s*\.\s*t\s*\(\s*$')
AUTONYMS={'한국어'}


def inspect(source,filename):
    masked=list(source);literals=[]
    for match in TOKEN.finditer(source):
        token=match.group()
        if token.startswith('"'):
            literals.append((match.start(),match.end(),json.loads(token)))
        elif token.startswith(('//','/*')):
            masked[match.start():match.end()]=['\n' if char=='\n' else ' ' for char in token]
    masked=''.join(masked);used=set();sites=0;pairs=0;paired=set()
    for index,(start,end,value) in enumerate(literals):
        if not TR_CALL.search(masked[:start]):continue
        assert index+1<len(literals),(filename,'tr requires a Chinese string',value)
        next_start,next_end,chinese=literals[index+1]
        assert re.fullmatch(r'\s*,\s*',masked[end:next_start]),(filename,'tr Chinese argument must be a literal',value)
        assert re.match(r'\s*\)',masked[next_end:]),(filename,'tr must contain exactly two string arguments',value)
        assert chinese.strip(),(filename,'empty Chinese translation',value)
        # Language labels can include their recognizable autonym inside a translated sentence.
        translated=chinese
        for autonym in AUTONYMS:translated=translated.replace(autonym,'')
        assert not HANGUL.search(translated),(filename,'Chinese argument still contains Korean',chinese)
        assert value.count('\n')==chinese.count('\n'),(filename,'translation line-break mismatch',value)
        if HANGUL.search(value):
            assert re.search('[\u3400-\u9fff]',chinese),(filename,'Chinese translation missing',value,chinese)
        paired.update((index,index+1));pairs+=1
    for index,(start,end,value) in enumerate(literals):
        if index in paired or not HANGUL.search(value):continue
        if value in AUTONYMS:continue
        assert CAT_CALL.search(masked[:start]),(filename,'unlocalized Korean literal',value)
        used.add(value);sites+=1
    return used,sites,pairs


def self_test():
    used,sites,pairs=inspect('UiText.t("확인"); GameUi.tr("취소", "取消"); tr("한국어 선택", "选择 한국어");', 'self-test')
    assert used=={'확인'} and sites==1 and pairs==2
    for invalid in ['label("번역 누락");','tr("테스트", "중국어 누락");','tr("테스트", "");','tr("테스트", variable);']:
        try:inspect(invalid,'self-test');raise RuntimeError('Missing-locale check accepted invalid source')
        except AssertionError:pass
    assert inspect('// "번역할 UI 아님"\n/* "주석" */', 'self-test')==(set(),0,0)


def main():
    self_test()
    subprocess.run([sys.executable,str(ROOT/'scripts/generate_locales.py'),'--check'],check=True)
    entries=json.loads((ROOT/'locales/ko-zh-Hans.json').read_text(encoding='utf-8'))
    used=set();sites=0;pairs=0
    for source in (ROOT/'app/src/main/java/io/oniimai/kanade').glob('*'):
        if source.name=='UiTextCatalog.java' or source.suffix not in {'.java','.kt'}:continue
        found,n,p=inspect(source.read_text(encoding='utf-8'),source.name)
        used.update(found);sites+=n;pairs+=p
    assert not used-entries.keys(),('missing catalogue translations',used-entries.keys())
    # Retired labels are retained for compatible older panels and diagnostics.
    print(f'UI locale coverage: {sites} catalogue call sites; {len(used)} entries; {pairs} inline bilingual pairs; no uncovered Korean UI literals')

if __name__=='__main__':main()
