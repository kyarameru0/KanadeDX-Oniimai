"""Compose API 102 builder. Android SDK 36, NDK r27c, JDK17+ and Gradle 9.1.0 required."""
import argparse,os,subprocess,zipfile,shutil
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument('--platform',required=True);p.add_argument('--build-tools',required=True);p.add_argument('--ndk',required=True)
p.add_argument('--api-aar',help='Legacy option, API 102 is resolved through Gradle');p.add_argument('--java-home',required=True);p.add_argument('--build-dir',required=True);p.add_argument('--output',required=True)
p.add_argument('--gradle-home');a=p.parse_args();root=Path(__file__).resolve().parents[1];src=root/'app/src/main'
build=Path(a.build_dir).resolve();build.mkdir(parents=True,exist_ok=True)
tools=Path(a.build_tools).resolve();jar=Path(a.platform).resolve()/'android.jar'
exe='.exe' if os.name=='nt' else ''
def jdk(name):return Path(a.java_home)/'bin'/(name+exe)
def tool(name):return tools/(name+exe)
def run(args):
    print('Running',Path(str(args[0])).name,flush=True);subprocess.run(list(map(str,args)),check=True)

host='windows-x86_64' if os.name=='nt' else ('darwin-x86_64' if __import__('sys').platform=='darwin' else 'linux-x86_64')
llvm=Path(a.ndk).resolve()/'toolchains/llvm/prebuilt'/host
native=build/'liboniimai_kanade.so'
run([llvm/'bin'/('clang++'+exe),'--target=aarch64-linux-android28','-shared','-fPIC','-O2','-std=c++17','-fvisibility=hidden','-fno-exceptions','-fno-rtti','-static-libstdc++','-Wl,--build-id=sha1','-Wl,-z,max-page-size=16384',src/'cpp/bridge.cpp','-llog','-ldl','-o',native])

# Compose requires Kotlin compiler plugins and AAR resource merging. Gradle owns
# the Android pipeline; keep the native bridge flags/signing identity unchanged.
key=build/'debug.keystore'
if not key.exists():run([jdk('keytool'),'-genkeypair','-keystore',key,'-storepass','android','-keypass','android','-alias','androiddebugkey','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Oniimai Kanade Development, O=Local Development, C=KR'])
staging=root/'app/build/native-libs/arm64-v8a'
staging.mkdir(parents=True,exist_ok=True);shutil.copyfile(native,staging/native.name)
gradle=Path(a.gradle_home).resolve() if a.gradle_home else Path(a.ndk).absolute().parent/'Tools/gradle'
if not (gradle/'lib').is_dir():raise SystemExit('Gradle 9.1.0 required: pass --gradle-home')
sdk=Path(a.platform).absolute().parent.parent
env=os.environ.copy();env['ANDROID_HOME']=str(sdk);env['JAVA_HOME']=str(Path(a.java_home).resolve())
subprocess.run(list(map(str,[jdk('java'),'-cp',gradle/'lib/*','org.gradle.launcher.GradleMain','-p',root,':app:assembleRelease','-PoniimaiKeystore='+str(key),'--console=plain'])),env=env,check=True)
output=Path(a.output);output.parent.mkdir(parents=True,exist_ok=True)
shutil.copyfile(root/'app/build/outputs/apk/release/app-release.apk',output)
signer=[jdk('java'),'-jar',tools/'lib/apksigner.jar']
run([*signer,'verify','--verbose','--print-certs',output]);run([tool('zipalign'),'-c','-P','16','4',output])
print('APK:',output.resolve())
