import argparse,subprocess,ctypes,os
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--build-dir',required=True);p.add_argument('--ndk');a=p.parse_args()
root=Path(__file__).resolve().parents[1];out=Path(a.build_dir).resolve();out.mkdir(parents=True,exist_ok=True)
java=root/'app/src/main/java/io/oniimai/kanade'
sources=[java/(name+'.java') for name in ['Protocol','PortSelection','FirmwareInfo','SetupDefaults','Io4Input','Io4Output','CommandChannel','ControllerInput','KeyboardState','DashboardLayout','DisplayGeometry','LedChannel','LedFrames','LedOutput','CeilingOutput','AimeProtocol','AimeTrace','AimeChannel','AimeFelica','AimePresence','AimeReader','UiText','UiTextCatalog','UnityStartup']]
sources += [java/'PhoneCardReader.java',java/'PhoneScan.java']
sources += [java/'LicenseText.java']
sources+=sorted((root/'tests').rglob('*.java'))
subprocess.run(['javac','--release','8','-encoding','UTF-8','-d',str(out),*map(str,sources)],check=True)
for name in ['ProtocolTest','Io4OutputTest','HardwareSelectionTest','FirmwareInfoTest','ChannelTest','ControllerInputTest','KeyboardStateTest','DashboardLayoutTest','DisplayGeometryTest','LedTest','LedOutputTest','CeilingOutputTest','AimeProtocolTest','AimeTraceTest','AimeChannelTest','AimePresenceTest','AimeReaderTest','PhoneNfcTest','UiTextTest','UnityStartupTest','LicenseTextTest']:
    subprocess.run(['java','-cp',str(out),'io.oniimai.kanade.'+name],check=True)
if a.ndk:
    llvm=Path(a.ndk).resolve()/'toolchains/llvm/prebuilt'/('windows-x86_64' if os.name=='nt' else 'linux-x86_64')/'bin'
    if os.name=='nt':
        lib=out/'native_state_test.dll'
        obj=out/'native_state_test.obj'
        cmd=[llvm/'clang++.exe','--target=x86_64-pc-windows-msvc','-c','-ffreestanding','-fno-stack-protector']
    else:
        lib=out/'native_state_test.so';cmd=['c++','-shared','-fPIC']
    subprocess.run(list(map(str,[*cmd,'-O2','-std=c++17','-fno-exceptions','-fno-rtti',root/'tests/native_state_test.cpp','-o',obj if os.name=='nt' else lib])),check=True)
    if os.name=='nt':subprocess.run([str(llvm/'ld.lld.exe'),'-flavor','link','/dll','/noentry','/nodefaultlib','/out:'+str(lib),str(obj)],check=True)
    result=ctypes.CDLL(str(lib)).run_tests()
    assert result>0,f'Native state test failed on source line {-result}'
    print(f'PASS: {result} native input/LED/statistics-state checks')
