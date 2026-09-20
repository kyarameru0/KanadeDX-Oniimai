#include "../app/src/main/cpp/target_fingerprint.h"
#include "../app/src/main/cpp/input_state.h"
#include "../app/src/main/cpp/led_state.h"
#include "../app/src/main/cpp/stats_state.h"
#include "../app/src/main/cpp/game_ui_state.h"
#include "../app/src/main/cpp/boot_input_state.h"
#include "../app/src/main/cpp/game_aime_state.h"
#include "../app/src/main/cpp/game_aime_led_state.h"
#include "../app/src/main/cpp/game_aime_error_state.h"
#include "../app/src/main/cpp/arm64_stub_branch.h"
#define CHECK(x) do { ++checks; if(!(x)) return -__LINE__; } while(0)
#ifdef _WIN32
#define EXPORT __declspec(dllexport)
// Freestanding host DLL: MSVC floating-point marker, no runtime is otherwise used.
extern "C" { int _fltused=0; }
#else
#define EXPORT __attribute__((visibility("default")))
#endif
struct FakeGamePrefs {
    int compact=1,marker=0,saves=0,writes=0;
    int getInt(const char* key,int){return key==GameUiState::MIGRATION_KEY?marker:compact;}
    void setInt(const char* key,int value){++writes;if(key==GameUiState::MIGRATION_KEY)marker=value;else compact=value;}
    void save(){++saves;}
};
struct FakeUiObjects {
    struct Group {bool alive=true;GameUiState::Visibility value{1,true,true};};
    Group groups[2];void* held[4]{};unsigned releases=0,writes=0,invalidWrites=0;
    bool alive(void* p){return p&&static_cast<Group*>(p)->alive;}
    unsigned hold(void* p){for(unsigned i=1;i<4;i++)if(!held[i]){held[i]=p;return i;}return 0;}
    void* resolve(unsigned h){return held[h];}
    void release(unsigned h){held[h]=nullptr;++releases;}
    GameUiState::Visibility read(void* p){return static_cast<Group*>(p)->value;}
    void write(void* p,GameUiState::Visibility v){if(!alive(p)){++invalidWrites;return;}static_cast<Group*>(p)->value=v;++writes;}
};
extern "C" EXPORT int run_tests(){
    int checks=0;InputState s;
    unsigned char fingerprintFixture[16];
    for(unsigned i=0;i<16;i++)fingerprintFixture[i]=static_cast<unsigned char>(i);
    CHECK(targetFingerprint16(fingerprintFixture)==UINT64_C(0x7c84dc9477851775));
    CHECK(matchesTarget(fingerprintFixture,UINT64_C(0x7c84dc9477851775)));
    for(unsigned i=0;i<16;i++){
        fingerprintFixture[i]^=0x80;
        CHECK(!matchesTarget(fingerprintFixture,UINT64_C(0x7c84dc9477851775)));
        fingerprintFixture[i]^=0x80;
    }

    GameAimeLedState cardLed;CHECK(cardLed.sample(0)==-1);
    cardLed.status(-1,10);CHECK(cardLed.sample(10)==-1);cardLed.status(5,10);CHECK(cardLed.sample(10)==-1);
    cardLed.status(1,100);CHECK(cardLed.sample(100)==0xffffff);CHECK(cardLed.sample(10000)==0xffffff);
    cardLed.scanEnded(10001);CHECK(cardLed.sample(10001)==0);
    cardLed.status(2,11000);CHECK(cardLed.sample(11000)==0x0000ff);cardLed.scanEnded(11001);CHECK(cardLed.sample(13999)==0x0000ff);CHECK(cardLed.sample(14000)==0);
    cardLed.status(3,15000);CHECK(cardLed.sample(15000)==0xffeb04);CHECK(cardLed.sample(18000)==0);
    cardLed.status(4,19000);CHECK(cardLed.sample(19000)==0xff0000);CHECK(cardLed.sample(18999)==0);CHECK(cardLed.sample(22000)==0);
    cardLed.status(2,23000);cardLed.status(1,24000);CHECK(cardLed.sample(26000)==0xffffff);
    cardLed.status(0,26001);CHECK(cardLed.sample(26001)==0);
    CHECK(GameAimeLedState::resultStatus(false,0)==2);CHECK(GameAimeLedState::resultStatus(false,2)==2);
    CHECK(GameAimeLedState::resultStatus(true,1)==-1);CHECK(GameAimeLedState::resultStatus(true,2)==3);
    CHECK(GameAimeLedState::resultStatus(true,0)==4);CHECK(GameAimeLedState::resultStatus(true,3)==4);CHECK(GameAimeLedState::resultStatus(true,4)==4);
    GameAimeState card;unsigned char bcd[]={0x12,0x34,0x56,0x78,0x90,0x12,0x34,0x56,0x78,0x90};char scanned[21]{};
    CHECK(card.status(1)==0);CHECK(!card.submit(bcd,10,1));
    card.start();card.update(true,10);CHECK(!card.submit(bcd,10,11));
    card.enable(true);card.update(true,20);CHECK(card.status(20)==1);
    CHECK(!card.submit(nullptr,10,21));CHECK(!card.submit(bcd,9,21));
    unsigned char zero[10]{};CHECK(!card.submit(zero,10,21));
    bcd[0]=0x1a;CHECK(!card.submit(bcd,10,21));bcd[0]=0xa1;CHECK(!card.submit(bcd,10,21));bcd[0]=0x12;
    CHECK(card.submit(bcd,10,21));CHECK(card.status(22)==2);CHECK(!card.submit(bcd,10,22));
    CHECK(card.take(scanned,22));CHECK(scanned[0]=='1'&&scanned[1]=='2'&&scanned[19]=='0'&&scanned[20]==0);
    CHECK(!card.pending&&card.digits[0]==0);CHECK(!card.take(scanned,23));CHECK(!card.submit(bcd,10,23));
    card.update(false,30);CHECK(card.status(30)==0);
    card.start();card.update(true,40);CHECK(card.submit(bcd,10,41));card.enable(false);CHECK(!card.take(scanned,42));CHECK(card.digits[0]==0);
    card.enable(true);card.update(true,50);CHECK(card.submit(bcd,10,51));card.start();card.update(true,52);CHECK(!card.take(scanned,52));CHECK(card.status(52)==1);
    CHECK(card.submit(bcd,10,53));card.update(false,54);CHECK(!card.take(scanned,54));
    card.update(true,60);CHECK(!card.submit(bcd,10,561));CHECK(!card.submit(bcd,10,59));
    card.update(true,600);CHECK(card.submit(bcd,10,601));card.update(true,1352);CHECK(!card.take(scanned,1352));CHECK(card.digits[0]==0);
    card.update(true,1400);CHECK(card.submit(bcd,10,1401));card.consumed();CHECK(!card.take(scanned,1402));CHECK(card.status(1402)==2);
    card.start();card.update(true,1500);CHECK(card.submit(bcd,10,1501));card.clear();CHECK(!card.take(scanned,1502));CHECK(card.status(1502)==1);
    CHECK(card.submit(bcd,10,1503));CHECK(!card.take(scanned,1502));CHECK(card.digits[0]==0);
    // Physical then virtual must not start a second game lookup in this scan.
    card.start();card.update(true,1600);CHECK(card.submit(bcd,10,1601));CHECK(card.take(scanned,1602));
    CHECK(card.physicalCommitted);CHECK(!card.acceptVirtual());CHECK(!card.acceptVirtual());
    card.update(false,1610);card.enable(false);card.clear();card.enable(true);card.update(true,1620);
    CHECK(!card.acceptVirtual()); // Lifecycle toggles do not authorize a second lookup.
    // Starting the game's next scan resets the physical commitment.
    card.start();card.update(true,1700);CHECK(!card.physicalCommitted);CHECK(card.acceptVirtual());CHECK(card.acceptVirtual());
    CHECK(!card.submit(bcd,10,1701));CHECK(!card.take(scanned,1702));
    // Virtual then queued physical cancels pending delivery, without changing
    // the original game's behavior for subsequent virtual-only requests.
    card.start();card.update(true,1800);CHECK(card.submit(bcd,10,1801));CHECK(card.acceptVirtual());
    CHECK(!card.pending&&card.digits[0]==0&&!card.physicalCommitted);CHECK(!card.take(scanned,1802));
    CHECK(card.acceptVirtual());CHECK(!card.submit(bcd,10,1803));
    // USB recovery, UI/lifecycle authorization and RF idle are not new game
    // scans. Only AimeUnitStart may release the reader's per-scan deduplication.
    GameAimeState scanEpoch;CHECK(scanEpoch.generation==0);
    scanEpoch.enable(true);scanEpoch.update(true,1900);scanEpoch.clear();CHECK(scanEpoch.generation==0);
    scanEpoch.start();CHECK(scanEpoch.generation==1);scanEpoch.update(true,1910);
    CHECK(scanEpoch.submit(bcd,10,1911));CHECK(scanEpoch.take(scanned,1912));CHECK(scanEpoch.generation==1);
    scanEpoch.enable(false);scanEpoch.clear();scanEpoch.update(false,1920);CHECK(scanEpoch.generation==1);
    scanEpoch.enable(true);scanEpoch.update(true,1930);CHECK(scanEpoch.generation==1);CHECK(!scanEpoch.submit(bcd,10,1931));
    scanEpoch.start();CHECK(scanEpoch.generation==2);scanEpoch.update(true,1940);
    CHECK(scanEpoch.submit(bcd,10,1941));scanEpoch.clear();CHECK(scanEpoch.generation==2);
    scanEpoch.acceptVirtual();CHECK(scanEpoch.generation==2);scanEpoch.start();CHECK(scanEpoch.generation==3);
    // A long USB transaction may finish after the game has opened another scan.
    // Both successful reads and failures carry the generation at request start.
    GameAimeState failures;failures.enable(true);failures.start();failures.update(true,2000);
    uint64_t request=failures.generation;
    CHECK(!failures.submitForScan(bcd,10,request+1,2001));
    CHECK(!failures.reportError(0,request,2001)); // No card is ordinary polling.
    CHECK(!failures.reportError(-1,request,2001));CHECK(!failures.reportError(6,request,2001));
    CHECK(!failures.reportError(3,request+1,2001));CHECK(!failures.reportError(3,request,1999));
    CHECK(!failures.reportError(3,request,2501));
    CHECK(failures.reportError(3,request,2001));CHECK(failures.status(2002)==2);
    CHECK(!failures.submitForScan(bcd,10,request,2002));CHECK(!failures.reportError(5,request,2002));
    CHECK(!failures.take(scanned,2003));CHECK(failures.takeError(2003)==3);
    CHECK(failures.pendingError==0&&failures.used&&failures.physicalCommitted);
    CHECK(!failures.acceptVirtual());CHECK(failures.takeError(2004)==0);
    failures.update(true,2010);CHECK(!failures.reportError(4,request,2011));
    failures.start();failures.update(true,2020);CHECK(failures.generation!=request);
    CHECK(!failures.reportError(5,request,2021));CHECK(!failures.submitForScan(bcd,10,request,2021));
    request=failures.generation;CHECK(failures.submitForScan(bcd,10,request,2021));
    CHECK(!failures.reportError(5,request,2022));CHECK(failures.take(scanned,2022));
    CHECK(!failures.reportError(5,request,2023)); // Never replace an active server lookup.
    failures.start();failures.update(true,2030);request=failures.generation;
    CHECK(failures.reportError(1,request,2031));CHECK(failures.acceptVirtual());CHECK(failures.takeError(2032)==0);
    failures.start();failures.update(true,2040);request=failures.generation;
    CHECK(failures.reportError(2,request,2041));failures.enable(false);CHECK(failures.takeError(2042)==0);
    failures.enable(true);failures.update(true,2050);CHECK(failures.reportError(4,request,2051));
    failures.clear();CHECK(failures.takeError(2052)==0);CHECK(failures.status(2052)==1);
    CHECK(failures.reportError(5,request,2053));failures.update(false,2054);CHECK(failures.takeError(2054)==0);
    failures.update(true,2060);CHECK(failures.reportError(5,request,2061));
    failures.update(true,2812);CHECK(failures.takeError(2812)==0); // No replay after a stalled frame.
    CHECK(failures.reportError(5,request,2813));CHECK(failures.takeError(2812)==0); // Clock moved backwards.
    failures.update(true,2820);CHECK(failures.reportError(5,request,2821));failures.start();
    failures.update(true,2822);CHECK(failures.takeError(2822)==0);CHECK(failures.status(2822)==1);
    // Error application is only valid before confirmation, result or any game error.
    CHECK(GameAimeErrorState::waiting(true,false,false,false));
    CHECK(!GameAimeErrorState::waiting(false,false,false,false));
    CHECK(!GameAimeErrorState::waiting(true,true,false,false));
    CHECK(!GameAimeErrorState::waiting(true,false,true,false));
    CHECK(!GameAimeErrorState::waiting(true,false,false,true));
    GameAimeErrorState owned;int restored=-1;
    CHECK(!owned.start(1,2,true,false,1,restored));CHECK(restored==-1);
    owned.committed(1,2,0);CHECK(owned.start(1,2,true,false,1,restored));CHECK(restored==0);
    CHECK(!owned.start(1,2,true,false,1,restored)); // Reset at most once, only at Start.
    owned.committed(1,2,0);CHECK(!owned.start(3,2,true,false,1,restored)); // A new unit is not ours.
    owned.committed(1,2,0);CHECK(!owned.start(1,3,true,false,1,restored)); // Game replaced error info.
    owned.committed(1,2,0);CHECK(!owned.start(1,2,true,true,1,restored)); // Game has a result now.
    owned.committed(1,2,0);CHECK(!owned.start(1,2,true,false,2,restored)); // Preserve server/network error.
    owned.committed(1,2,0);CHECK(!owned.start(1,2,false,false,1,restored)); // Game cleared it already.
    owned.committed(1,2,4);CHECK(owned.start(1,2,true,false,1,restored));CHECK(restored==4);
    for(int flags=0;flags<8;flags++)CHECK(GameAimeErrorState::readEvent(flags&1,flags&2,flags&4)==(flags==7));
    GameAimeErrorState gate;
    CHECK(!gate.completedReadFailure(1,2,false,true,false,false,1,9,4));
    gate.committed(1,2,0);
    CHECK(gate.completedReadFailure(1,2,false,true,false,false,1,9,4));
    CHECK(!gate.completedReadFailure(3,2,false,true,false,false,1,9,4));
    CHECK(!gate.completedReadFailure(1,3,false,true,false,false,1,9,4));
    CHECK(!gate.completedReadFailure(1,2,true,true,false,false,1,9,4));
    CHECK(!gate.completedReadFailure(1,2,false,false,false,false,1,9,4));
    CHECK(!gate.completedReadFailure(1,2,false,true,true,false,1,9,4));
    CHECK(!gate.completedReadFailure(1,2,false,true,false,true,1,9,4));
    for(int category=0;category<5;category++)if(category!=1)CHECK(!gate.completedReadFailure(1,2,false,true,false,false,category,9,4));
    for(int state=0;state<9;state++)CHECK(!gate.completedReadFailure(1,2,false,true,false,false,1,state,4));
    for(int result=0;result<7;result++)if(result!=4)CHECK(!gate.completedReadFailure(1,2,false,true,false,false,1,9,result));
    CHECK(gate.start(1,2,true,false,1,restored));
    CHECK(!gate.completedReadFailure(1,2,false,true,false,false,1,9,4));
    s.submit(1,1,0,false,100);s.frame(100);
    CHECK(s.takeTouch(0,100)==0);CHECK(!s.button(2,false,100));
    s.submit(1,1,0,true,110);s.frame(110);
    CHECK(s.takeTouch(0,110)==1);CHECK(s.button(2,false,110));CHECK(s.button(2,true,110));
    CHECK(s.button(2,true,110)); // repeated queries in a frame see the same edge
    s.submit(1,1,0,true,120);s.frame(120);
    CHECK(s.button(2,false,120));CHECK(!s.button(2,true,120));CHECK(s.takeTouch(0,120)==1);
    s.submit(0,0,0,true,130);s.frame(130);CHECK(!s.button(2,false,130));CHECK(s.takeTouch(0,130)==0);
    s.submit(0,1,0,true,131);s.frame(131);CHECK(s.button(2,true,131));
    // A short complete pulse between game frames survives for exactly one frame.
    s.submit(0,0,0,true,140);s.frame(140);
    s.submit(1ull<<33,128,0,true,141);s.submit(0,0,0,true,142);s.frame(150);
    CHECK(s.button(9,false,150));CHECK(s.button(9,true,150));CHECK(s.takeTouch(0,150)==(1ull<<33));
    s.frame(160);CHECK(!s.button(9,false,160));CHECK(s.takeTouch(0,160)==0);
    s.submit((1ull<<34)-1,255,0,true,170);s.frame(170);
    CHECK(s.takeTouch(0,170)==((1ull<<34)-1));
    for(int i=2;i<=9;i++)CHECK(s.button(i,false,170));
    int otherIds[]={0,1,10,11,18,19,-1,20};
    for(int id:otherIds)CHECK(!s.button(id,false,170));
    s.submit(5,129,1,true,180);s.frame(180);
    CHECK(s.takeTouch(0,180)==0);CHECK(s.takeTouch(1,180)==5);
    CHECK(s.button(11,false,180));CHECK(s.button(18,false,180));CHECK(!s.button(2,false,180));
    s.submit(5,129,1,false,181);CHECK(!s.button(11,false,181));CHECK(s.takeTouch(1,181)==0);
    s.submit(~uint64_t{0},~uint32_t{0},0,true,200);s.frame(200);
    CHECK(s.takeTouch(0,200)==((1ull<<34)-1));
    s.frame(701);CHECK(!s.active);CHECK(!s.button(2,false,701));CHECK(s.takeTouch(0,701)==0);
    s.submit(1,1,0,true,800);s.frame(800);s.frame(1);CHECK(!s.active);
    s.submit(0,0,0,true,900);s.frame(900);CHECK(s.rising==0);
    CHECK(s.touchUpdate(0,false));CHECK(!s.touchUpdate(1,false));
    s.submit(0,0,0,false,910);
    CHECK(s.touchUpdate(0,false));CHECK(s.touchUpdate(0,false));CHECK(s.touchUpdate(0,false));
    CHECK(!s.touchUpdate(0,false));CHECK(s.touchUpdate(0,true));
    s.submit(1,0,0,true,920);CHECK(s.touchUpdate(0,false));
    s.submit(1,0,1,true,930);CHECK(s.touchUpdate(0,false));CHECK(s.touchUpdate(1,false));
    CHECK(s.takeTouch(1,1500)==0);CHECK(s.touchUpdate(1,false));CHECK(s.touchUpdate(1,false));CHECK(s.touchUpdate(1,false));CHECK(!s.touchUpdate(1,false));
    // The ninth bit is Kanade's Select button, isolated for each player.
    s.submit(0,256,0,true,1600);s.frame(1600);
    CHECK(s.button(10,false,1600));CHECK(s.button(10,true,1600));CHECK(!s.button(19,false,1600));
    s.frame(1601);CHECK(s.button(10,false,1601));CHECK(!s.button(10,true,1601));
    s.submit(0,0,0,true,1602);s.frame(1602);CHECK(!s.button(10,false,1602));
    s.submit(0,256,1,true,1603);s.frame(1603);CHECK(s.button(19,true,1603));CHECK(!s.button(10,false,1603));
    s.submit(0,512,1,true,1604);s.frame(1604);CHECK(!s.button(20,false,1604));CHECK(!s.button(19,false,1604));
    s.submit(0,256,1,true,1605);s.submit(0,0,1,true,1606);s.frame(1607);CHECK(s.button(19,true,1607));
    s.frame(1608);CHECK(!s.button(19,false,1608));
    StatsLifecycle stats;
    CHECK(!stats.available);stats.startGame(1);CHECK(stats.playing());stats.capture(false);CHECK(stats.available);
    CHECK(stats.releaseGame(1));CHECK(!stats.available); // Aborting an unfinished song clears it.
    stats.startGame(2);stats.capture(true);stats.releaseGame(2);CHECK(stats.available);CHECK(stats.finished);
    stats.startResult(3);CHECK(stats.available);CHECK(stats.result);CHECK(!stats.playing());
    CHECK(!stats.releaseGame(2));CHECK(stats.available); // Overlapping process teardown.
    CHECK(stats.releaseResult(3));CHECK(!stats.available);CHECK(!stats.result);
    stats.startResult(4);CHECK(!stats.available);stats.capture(true);CHECK(stats.available);
    stats.startGame(5);CHECK(!stats.releaseResult(4));CHECK(stats.playing());CHECK(!stats.available);
    stats.capture(false);CHECK(!stats.releaseGame(2));CHECK(stats.available);
    double achievement=-1;
    CHECK(statsDecimal(0,0,101,0,achievement));CHECK(achievement==101);
    CHECK(statsDecimal(4u<<16,0,1001234,0,achievement));CHECK(achievement>100.1233&&achievement<100.1235);
    CHECK(statsDecimal(0,0,0,0,achievement));CHECK(achievement==0);
    CHECK(!statsDecimal(0,0,102,0,achievement));CHECK(!statsDecimal(29u<<16,0,1,0,achievement));
    CHECK(!statsDecimal(1,0,1,0,achievement));CHECK(!statsDecimal(0x80000000u,0,1,0,achievement));
    static LedState led;
    CHECK(led.seen==0);CHECK(led.events==0);
    led.set(0,{1,0,0,1});led.sample(100);CHECK(led.colors[0]==0xff0000);CHECK(led.seen==1);
    led.set(7,{0,1,0,0.5f});led.sample(100);CHECK(led.colors[7]==0x008000);CHECK(led.seen==129);
    led.set(999,{1,1,1,1});CHECK(led.events==2);
    led.set(8,{1,1,1,0.25f});led.sample(100);CHECK(led.colors[8]==0x404040);CHECK(led.seen==385);
    CHECK(LedState::rgb({-1,2,0.5f,1})==0x00ff80);
    CHECK(LedState::rgb({1,1,1,-1})==0);
    CHECK(LedTrack::rgb(LedTrack::unpack(0xff123456))==0x563412);
    CHECK(LedTrack::pack({1,.5f,0,1})==0xff0080ff);
    for(int i=0;i<8;i++){led.set(i,{0,0,1,1});led.sample(110);CHECK(led.colors[i]==255);}
    CHECK(led.seen==511);
    led.pressed(3,{1,1,1,1},1000);led.sample(1000);CHECK(led.colors[3]==0xffffff);
    led.sample(1499);CHECK(led.colors[3]==0xffffff);CHECK(led.colors[2]==255);
    led.sample(1500);CHECK(led.colors[3]==255); // restore the static blue after 500 ms
    LedFadeStep fade[]={{0xff0000ff,0xffff0000,1000,-1}};
    led.fade(fade,1,false,2000);led.sample(2000);for(int i=0;i<8;i++)CHECK(led.colors[i]==0xff0000);
    led.sample(2500);for(int i=0;i<8;i++)CHECK(led.colors[i]==0x800080);
    led.sample(3000);for(int i=0;i<8;i++)CHECK(led.colors[i]==255);
    led.sample(9999);CHECK(led.colors[0]==255);
    LedFadeStep loop[]={{0xff000000,0xffffffff,100,1},{0xffffffff,0xff000000,100,0}};
    led.fade(loop,2,false,10000);led.sample(10050);CHECK(led.colors[0]==0x808080);
    led.sample(10100);CHECK(led.colors[0]==0xffffff);led.sample(10150);CHECK(led.colors[0]==0x808080);
    led.sample(10200);CHECK(led.colors[0]==0);led.sample(10250);CHECK(led.colors[0]==0x808080);
    led.pressed(0,{0,1,0,1},10250);led.sample(10250);CHECK(led.colors[0]==0x00ff00);CHECK(led.colors[1]==0x808080);
    led.sample(10750);CHECK(led.colors[0]==255); // static base is separate from loop colors
    led.all({.25f,0,.5f,1});led.sample(11000);for(int i=0;i<8;i++)CHECK(led.colors[i]==0x400080);
    LedFadeStep ring[]={{0x00000000,0x000000ff,1000,-1}};led.fade(ring,1,true,12000);
    led.sample(12500);CHECK(led.colors[8]==0x808080);CHECK(led.colors[0]==0x400080);
    led.sample(13000);CHECK(led.colors[8]==0xffffff);
    led.all({0,0,0,1});led.sample(14000);for(int i=0;i<8;i++)CHECK(led.colors[i]==0);CHECK(led.colors[8]==0xffffff);
    uint32_t before=led.events;led.fade(nullptr,1,false,15000);led.fade(loop,0,false,15000);led.fade(loop,33,false,15000);CHECK(led.events==before);
    LedFadeStep invalid[]={{0xff000000,0xff00ff00,0,99}};led.fade(invalid,1,false,16000);led.sample(16000);CHECK(led.colors[0]==0x00ff00);
    LedFadeStep zeroLoop[]={{0xff000000,0xffffffff,0,0}};led.fade(zeroLoop,1,false,17000);led.sample(999999);CHECK(led.colors[0]==0xffffff);
    led.fade(fade,1,false,20000);led.sample(19000);CHECK(led.colors[0]==0xff0000);led.sample(19500);CHECK(led.colors[0]==0x800080);
    static LedState cabinet;
    cabinet.all({1,0,0,1});cabinet.set(LedState::BILLBOARD,{0,1,0,1});
    cabinet.allFet(0xff804020);cabinet.sample(100);
    CHECK(cabinet.colors[8]==0x202020);CHECK(cabinet.colors[9]==0x404040);CHECK(cabinet.colors[10]==0x808080);
    CHECK(cabinet.colors[0]==0xff0000);CHECK(cabinet.colors[11]==0x00ff00);CHECK(cabinet.seen==4095);
    cabinet.setFet(8,255);cabinet.setFet(9,16);cabinet.setFet(10,64);cabinet.setFet(11,255);cabinet.sample(110);
    CHECK(cabinet.colors[8]==0xffffff);CHECK(cabinet.colors[9]==0x101010);CHECK(cabinet.colors[10]==0x404040);CHECK(cabinet.colors[11]==0x00ff00);
    LedFadeStep fetFade[]={{0x00ff0000,0x000080ff,1000,-1}};
    cabinet.fade(fetFade,1,true,1000);cabinet.sample(1500);
    CHECK(cabinet.colors[8]==0x808080);CHECK(cabinet.colors[9]==0x404040);CHECK(cabinet.colors[10]==0x808080);
    CHECK(cabinet.colors[0]==0xff0000);CHECK(cabinet.colors[11]==0x00ff00);
    cabinet.sample(2000);CHECK(cabinet.colors[8]==0xffffff);CHECK(cabinet.colors[9]==0x808080);CHECK(cabinet.colors[10]==0);
    cabinet.allOff();cabinet.sample(2100);for(int i=0;i<11;i++)CHECK(cabinet.colors[i]==0);
    CHECK(cabinet.colors[11]==0x00ff00); // BD15070 all-off never fabricates a JVS billboard command.
    static LedState noCeiling;noCeiling.allFet(0xffffffff);noCeiling.allOff();CHECK((noCeiling.seen&(1u<<11))==0);
    uint32_t branch=0;
    CHECK(Arm64StubBranch::encode(0x10000000,0x10000004,branch));CHECK(branch==0x14000001);
    CHECK(Arm64StubBranch::encode(0x10000000,0x0ffffffc,branch));CHECK(branch==0x17ffffff);
    CHECK(Arm64StubBranch::encode(0x10000000,0x08000000,branch));CHECK(branch==0x16000000);
    CHECK(Arm64StubBranch::encode(0x10000000,0x17fffffc,branch));CHECK(branch==0x15ffffff);
    CHECK(!Arm64StubBranch::encode(0x10000000,0x18000000,branch));CHECK(!Arm64StubBranch::encode(0x10000000,0x07fffffc,branch));
    CHECK(!Arm64StubBranch::encode(0x10000001,0x10000004,branch));CHECK(!Arm64StubBranch::encode(0x10000000,0x10000002,branch));
    // The patch changes exactly one instruction; adjacent RET and ctor bytes stay intact.
    uint32_t neighbors[]={Arm64StubBranch::RET,Arm64StubBranch::RET,0xf81f0ffe,0xa9014ff4};
    CHECK(Arm64StubBranch::encode(0x10000000,0x10001000,branch));neighbors[0]=branch;
    CHECK(neighbors[0]==0x14000400);CHECK(neighbors[1]==Arm64StubBranch::RET);CHECK(neighbors[2]==0xf81f0ffe);CHECK(neighbors[3]==0xa9014ff4);
    Arm64StubBranch::Thunk thunk;thunk.target=0x1234567890abcdef;
    CHECK(thunk.load==0x58000050);CHECK(thunk.branch==0xd61f0200);CHECK(thunk.target==0x1234567890abcdef);
    FakeGamePrefs prefs;
    CHECK(GameUiState::migrateCompact(prefs));CHECK(prefs.compact==0);CHECK(prefs.marker==1);CHECK(prefs.saves==1);CHECK(prefs.writes==2);
    prefs.compact=1;CHECK(!GameUiState::migrateCompact(prefs));CHECK(prefs.compact==1);CHECK(prefs.saves==1);CHECK(prefs.writes==2);
    prefs.marker=0;prefs.compact=0;CHECK(GameUiState::migrateCompact(prefs));CHECK(prefs.compact==0);CHECK(prefs.saves==2);
    FakeUiObjects objects;GameUiState::TemporaryHide hide;auto* first=&objects.groups[0];auto* second=&objects.groups[1];
    hide.update(objects,first,false);CHECK(hide.handle==0);CHECK(objects.writes==0);
    first->value={.4f,false,true};hide.update(objects,first,true);
    CHECK(hide.handle!=0);CHECK(first->value.alpha==0);CHECK(!first->value.interactable);CHECK(!first->value.raycasts);
    hide.update(objects,first,true);hide.update(objects,first,false);
    CHECK(hide.handle==0);CHECK(first->value.alpha==.4f);CHECK(!first->value.interactable);CHECK(first->value.raycasts);CHECK(objects.releases==1);
    // Changing the scene/group restores the old group before saving the new one.
    second->value={.75f,true,false};hide.update(objects,first,true);hide.update(objects,second,true);
    CHECK(first->value.alpha==.4f);CHECK(second->value.alpha==0);CHECK(!second->value.raycasts);CHECK(!second->value.interactable);
    hide.update(objects,second,false);CHECK(second->value.alpha==.75f);CHECK(second->value.interactable);CHECK(!second->value.raycasts);
    hide.update(objects,first,true);first->alive=false;hide.update(objects,nullptr,false);
    CHECK(hide.handle==0);CHECK(objects.invalidWrites==0);CHECK(objects.releases==4);
    hide.update(objects,nullptr,true);CHECK(hide.handle==0);CHECK(objects.invalidWrites==0);
    BootInputState boot;
    CHECK(!boot.submit(1,0,false,100));CHECK(!boot.take(true,100));
    CHECK(boot.submit(0,0,true,110));CHECK(!boot.take(true,110));
    CHECK(boot.submit(1,0,true,120));CHECK(!boot.take(false,120));
    CHECK(boot.submit(1,0,true,130));CHECK(!boot.take(true,130)); // held input is not a new press
    CHECK(boot.submit(0,0,true,140));CHECK(boot.submit(1,0,true,150));CHECK(boot.take(true,150));
    boot.started();CHECK(boot.completed);CHECK(!boot.submit(1,0,true,160));CHECK(!boot.take(true,160));
    CHECK(boot.submit(0,0,true,170));CHECK(boot.submit(2,0,true,180));CHECK(!boot.take(true,180));
    BootInputState bootRing;bootRing.submit(0,256,true,200);CHECK(bootRing.take(true,200)); // P1 and every ring button
    bootRing.started();CHECK(!bootRing.submit(0,256,true,210));CHECK(!bootRing.submit(4,256,true,220));
    CHECK(bootRing.submit(0,0,true,230));CHECK(bootRing.submit(0,1,true,240));CHECK(!bootRing.take(true,240));
    BootInputState stale;stale.submit(1,0,true,1);CHECK(!stale.take(true,502));
    stale.submit(0,0,true,510);stale.submit(1,0,true,520);stale.submit(0,0,true,530);CHECK(stale.take(true,540)); // short tap retained
    BootInputState manual;manual.started();manual.submit(1,1,true,10);CHECK(!manual.take(true,10));
    BootInputState blocked;blocked.submit(1,1,true,10);blocked.submit(1,1,false,11);CHECK(!blocked.take(true,12));
    BootInputState rewind;rewind.submit(1,0,true,100);CHECK(!rewind.take(true,99));
    return checks;
}
