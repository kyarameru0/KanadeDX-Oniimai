#pragma once
namespace Target160 {
// Verified against KanadeDX 1.60 / KanadeDX-260207.0635.apk.1 only.
static const unsigned char BUILD_ID[] = {0x91,0x20,0x99,0x25,0xec,0xcf,0x42,0x0b,0xb6,0xa4,0x85,0x64,0xb5,0xed,0xf9,0x3d,0xce,0x80,0x24,0x36};
static constexpr uintptr_t RVA_TOUCH = 0x27a6fac;
static constexpr uint64_t SIG_TOUCH = 0x630697f0a383980aULL;
static constexpr uintptr_t RVA_FRAME = 0x2633610;
static constexpr uint64_t SIG_FRAME = 0x786675b99e01b4abULL;
static constexpr uintptr_t RVA_RAW = 0x27a1560;
static constexpr uint64_t SIG_RAW = 0xef847809d81d8e18ULL;
static constexpr uintptr_t RVA_DOWN = 0x27a1624;
static constexpr uint64_t SIG_DOWN = 0xef847809d81d8e18ULL;
static constexpr uintptr_t RVA_LED_COLOR = 0x21e4580;
static constexpr uint64_t SIG_LED_COLOR = 0xc0a28fefa75e9c52ULL;
static constexpr uintptr_t RVA_LED_PRESS = 0x21e4464;
static constexpr uint64_t SIG_LED_PRESS = 0x3877e3a120e73c1fULL;
static constexpr uintptr_t RVA_LED_ALL = 0x21e4bcc;
static constexpr uint64_t SIG_LED_ALL = 0xb426360a9523ee2fULL;
static constexpr uintptr_t RVA_LED_OFF = 0x21e42f8;
static constexpr uint64_t SIG_LED_OFF = 0x91cd02170de1bfe7ULL;
static constexpr uintptr_t RVA_LED_FADE = 0x21e4664;
static constexpr uint64_t SIG_LED_FADE = 0xcff623f2d1727a15ULL;
static constexpr uintptr_t RVA_LED_RING = 0x21e43c0;
static constexpr uint64_t SIG_LED_RING = 0x7527c559070534e3ULL;
static constexpr uintptr_t RVA_LED_FET = 0x21e4ad4;
static constexpr uint64_t SIG_LED_FET = 0xa5a3a774e400d5bcULL;
static constexpr uintptr_t RVA_LED_RING_FADE = 0x21e48d4;
static constexpr uint64_t SIG_LED_RING_FADE = 0x682c0385f9991121ULL;
static constexpr uintptr_t RVA_GAME_START = 0x229ec30;
static constexpr uint64_t SIG_GAME_START = 0xf770495f77be3e9fULL;
static constexpr uintptr_t RVA_GAME_RELEASE = 0x22a8f64;
static constexpr uint64_t SIG_GAME_RELEASE = 0xf948478bb0f4d0c6ULL;
static constexpr uintptr_t RVA_RESULT_START = 0x232ffac;
static constexpr uint64_t SIG_RESULT_START = 0x767e081c8f8d18d2ULL;
static constexpr uintptr_t RVA_RESULT_RELEASE = 0x233a6bc;
static constexpr uint64_t SIG_RESULT_RELEASE = 0xa8a35b72fcf1e8c7ULL;
static constexpr uintptr_t RVA_SCORE_UPDATE = 0x2622c58;
static constexpr uint64_t SIG_SCORE_UPDATE = 0x919f08c8c47b4c71ULL;
static constexpr uintptr_t RVA_SCORE_UTAGE = 0x262211c;
static constexpr uint64_t SIG_SCORE_UTAGE = 0x3f13df539502d3b2ULL;
static constexpr uintptr_t RVA_SCORE_FINISH = 0x262dda4;
static constexpr uint64_t SIG_SCORE_FINISH = 0xc99209f613f61bb0ULL;
static constexpr uintptr_t RVA_MUSIC_GET = 0x25e90c8;
static constexpr uint64_t SIG_MUSIC_GET = 0x1505fdfd01b160bfULL;
static constexpr uintptr_t RVA_NOTES_UPDATE = 0x265d988;
static constexpr uint64_t SIG_NOTES_UPDATE = 0x5d2bb2955093d243ULL;
static constexpr uintptr_t RVA_CURRENT_MSEC = 0x265dd44;
static constexpr uint64_t SIG_CURRENT_MSEC = 0xd1fa593ca852598aULL;

// Game control UI/defaults: verified against the same exact APK and metadata.
static constexpr uintptr_t RVA_UI_SETTINGS_CCTOR = 0x21d6b70;
static constexpr uint64_t SIG_UI_SETTINGS_CCTOR = 0x1b6d9244c9d17e1aULL;
static constexpr uintptr_t RVA_UI_SETTINGS_LOAD = 0x21d494c;
static constexpr uint64_t SIG_UI_SETTINGS_LOAD = 0x5b7757f91e52ff35ULL;
static constexpr uintptr_t RVA_UI_CONTROL_UPDATE = 0x21d10e0;
static constexpr uint64_t SIG_UI_CONTROL_UPDATE = 0x01162dd66ca38affULL;
static constexpr uintptr_t RVA_UI_PREFS_GETINT = 0x22fe4f8;
static constexpr uint64_t SIG_UI_PREFS_GETINT = 0x300a0b0a8dab2321ULL;
static constexpr uintptr_t RVA_UI_PREFS_SETINT = 0x22fe43c;
static constexpr uint64_t SIG_UI_PREFS_SETINT = 0x300a0b0a8dab2321ULL;
static constexpr uintptr_t RVA_UI_PREFS_SAVE = 0x22feab8;
static constexpr uint64_t SIG_UI_PREFS_SAVE = 0x75461d589f500aa6ULL;
static constexpr uintptr_t RVA_UI_GROUP_ALPHA_GET = 0x5229340;
static constexpr uint64_t SIG_UI_GROUP_ALPHA_GET = 0x1fc9eb48f84b1302ULL;
static constexpr uintptr_t RVA_UI_GROUP_ALPHA_SET = 0x522937c;
static constexpr uint64_t SIG_UI_GROUP_ALPHA_SET = 0x24a558de12f52cc8ULL;
static constexpr uintptr_t RVA_UI_GROUP_INTERACTABLE_GET = 0x52293c8;
static constexpr uint64_t SIG_UI_GROUP_INTERACTABLE_GET = 0x66c7334920f9f52aULL;
static constexpr uintptr_t RVA_UI_GROUP_RAYCAST_GET = 0x5229404;
static constexpr uint64_t SIG_UI_GROUP_RAYCAST_GET = 0xcceb17495a51bf56ULL;
static constexpr uintptr_t RVA_UI_GROUP_RAYCAST_SET = 0x5229440;
static constexpr uint64_t SIG_UI_GROUP_RAYCAST_SET = 0x565153f42c3dfcbbULL;
static constexpr uintptr_t RVA_UI_OBJECT_ALIVE = 0x50546f8;
static constexpr uint64_t SIG_UI_OBJECT_ALIVE = 0x6fdb464b4672abb7ULL;
static constexpr uintptr_t DATA_UI_SETTINGS_TYPEINFO = 0x5691030;
static constexpr uintptr_t FIELD_UI_MAIN_GROUP = 0x20;

static constexpr uintptr_t RVA_BOOT_UPDATE = 0x52594bc;
static constexpr uint64_t SIG_BOOT_UPDATE = 0x5b7757f91e52ff35ULL;

static constexpr uintptr_t RVA_BOOT_STARTED = 0x22fb79c;
static constexpr uint64_t SIG_BOOT_STARTED = 0x905885a5c1cdcb00ULL;

static constexpr uintptr_t RVA_BOOT_CANVAS = 0x22fa148;
static constexpr uint64_t SIG_BOOT_CANVAS = 0x7a13717ef4cad436ULL;

static constexpr uintptr_t RVA_BOOT_BUTTON_PRESS = 0x50cbc64;
static constexpr uint64_t SIG_BOOT_BUTTON_PRESS = 0xb7aba2078102b8b3ULL;

static constexpr uintptr_t RVA_BOOT_ACTIVE = 0x5263058;
static constexpr uint64_t SIG_BOOT_ACTIVE = 0x131276a73fe6a367ULL;

static constexpr uintptr_t RVA_BOOT_INTERACTABLE = 0x5248898;
static constexpr uint64_t SIG_BOOT_INTERACTABLE = 0xd2b4f32811f74f36ULL;

static constexpr uintptr_t FIELD_BOOT_START_BUTTON = 0x60;
static constexpr uintptr_t FIELD_BOOT_START_CALLBACK = 0xd0;

static constexpr uintptr_t RVA_ALBUM_INSTANCE = 0x2426c2c;
static constexpr uint64_t SIG_ALBUM_INSTANCE = 0xfffc21b2cc5dda8aULL;
static constexpr uintptr_t RVA_ALBUM_JACKET = 0x2426df8;
static constexpr uint64_t SIG_ALBUM_JACKET = 0xe9c238f1aeb6a393ULL;
static constexpr uintptr_t RVA_ALBUM_SELECTED = 0x26186cc;
static constexpr uint64_t SIG_ALBUM_SELECTED = 0xe684be40e67e84f6ULL;

// Physical Aime card scan bridge, exact KanadeDX 1.60 image only.
static constexpr uintptr_t RVA_AIME_UPDATE = 0x27be6cc;
static constexpr uint64_t SIG_AIME_UPDATE = 0x208fab1dcac16604ULL;
static constexpr uintptr_t RVA_AIME_START = 0x27be66c;
static constexpr uint64_t SIG_AIME_START = 0x0df41bab950711cbULL;
static constexpr uintptr_t RVA_AIME_USE = 0x27bee88;
static constexpr uint64_t SIG_AIME_USE = 0x0df41bab950711cbULL;

static constexpr uintptr_t RVA_AIME_MANAGER_EXECUTE = 0x25cad60;
static constexpr uint64_t SIG_AIME_MANAGER_EXECUTE = 0x42b71dc994992ee2ULL;

// Read-only guards for the original recoverable Aime error state transition.
static constexpr uintptr_t RVA_AIME_ERROR_POLL = 0x25cb07c;
static constexpr uint64_t SIG_AIME_ERROR_POLL = 0xcf988a789526ea78ULL;
static constexpr uintptr_t RVA_AIME_ERROR_RESULT = 0x25cb168;
static constexpr uint64_t SIG_AIME_ERROR_RESULT = 0x0cfb208aafbadfefULL;
static constexpr uintptr_t RVA_AIME_ERROR_WINDOW = 0x2560b84;
static constexpr uint64_t SIG_AIME_ERROR_WINDOW = 0x007757b96e3934c6ULL;
static constexpr uintptr_t RVA_AIME_UNIT_START = 0x27bd3f8;
static constexpr uint64_t SIG_AIME_UNIT_START = 0x78743a32f2d02844ULL;

// Independent JVS billboard RGB output; PWM target is exactly one RET.
static constexpr uintptr_t RVA_LED_PWM = 0x27a16ec;
static constexpr uint64_t SIG_LED_PWM = 0x697f03e5867031e2ULL;
static constexpr uintptr_t RVA_LED_BLOCK_COLOR = 0x26ff678;
static constexpr uint64_t SIG_LED_BLOCK_COLOR = 0xb2ccb27dfc7a69ddULL;

// AdvCheck gate for a completed adapter-owned physical read failure.
static constexpr uintptr_t RVA_AIME_ADVCHECK = 0x25cb32c;
static constexpr uint64_t SIG_AIME_ADVCHECK = 0x3b3dd936179896c4ULL;

static constexpr uintptr_t RVA_AIME_ANYREAD = 0x25cb34c;
static constexpr uint64_t SIG_AIME_ANYREAD = 0xc91d5c5366d1fab9ULL;

static constexpr TargetBuild PROFILE = {
    BUILD_ID, sizeof(BUILD_ID), "KanadeDX 1.60 (260207.0635)",
    RVA_TOUCH,
    SIG_TOUCH,
    RVA_FRAME,
    SIG_FRAME,
    RVA_RAW,
    SIG_RAW,
    RVA_DOWN,
    SIG_DOWN,
    RVA_LED_COLOR,
    SIG_LED_COLOR,
    RVA_LED_PRESS,
    SIG_LED_PRESS,
    RVA_LED_ALL,
    SIG_LED_ALL,
    RVA_LED_OFF,
    SIG_LED_OFF,
    RVA_LED_FADE,
    SIG_LED_FADE,
    RVA_LED_RING,
    SIG_LED_RING,
    RVA_LED_FET,
    SIG_LED_FET,
    RVA_LED_RING_FADE,
    SIG_LED_RING_FADE,
    RVA_GAME_START,
    SIG_GAME_START,
    RVA_GAME_RELEASE,
    SIG_GAME_RELEASE,
    RVA_RESULT_START,
    SIG_RESULT_START,
    RVA_RESULT_RELEASE,
    SIG_RESULT_RELEASE,
    RVA_SCORE_UPDATE,
    SIG_SCORE_UPDATE,
    RVA_SCORE_UTAGE,
    SIG_SCORE_UTAGE,
    RVA_SCORE_FINISH,
    SIG_SCORE_FINISH,
    RVA_MUSIC_GET,
    SIG_MUSIC_GET,
    RVA_NOTES_UPDATE,
    SIG_NOTES_UPDATE,
    RVA_CURRENT_MSEC,
    SIG_CURRENT_MSEC,
    RVA_UI_SETTINGS_CCTOR,
    SIG_UI_SETTINGS_CCTOR,
    RVA_UI_SETTINGS_LOAD,
    SIG_UI_SETTINGS_LOAD,
    RVA_UI_CONTROL_UPDATE,
    SIG_UI_CONTROL_UPDATE,
    RVA_UI_PREFS_GETINT,
    SIG_UI_PREFS_GETINT,
    RVA_UI_PREFS_SETINT,
    SIG_UI_PREFS_SETINT,
    RVA_UI_PREFS_SAVE,
    SIG_UI_PREFS_SAVE,
    RVA_UI_GROUP_ALPHA_GET,
    SIG_UI_GROUP_ALPHA_GET,
    RVA_UI_GROUP_ALPHA_SET,
    SIG_UI_GROUP_ALPHA_SET,
    RVA_UI_GROUP_INTERACTABLE_GET,
    SIG_UI_GROUP_INTERACTABLE_GET,
    RVA_UI_GROUP_RAYCAST_GET,
    SIG_UI_GROUP_RAYCAST_GET,
    RVA_UI_GROUP_RAYCAST_SET,
    SIG_UI_GROUP_RAYCAST_SET,
    RVA_UI_OBJECT_ALIVE,
    SIG_UI_OBJECT_ALIVE,
    DATA_UI_SETTINGS_TYPEINFO,
    FIELD_UI_MAIN_GROUP,
    RVA_BOOT_UPDATE,
    SIG_BOOT_UPDATE,
    RVA_BOOT_STARTED,
    SIG_BOOT_STARTED,
    RVA_BOOT_CANVAS,
    SIG_BOOT_CANVAS,
    RVA_BOOT_BUTTON_PRESS,
    SIG_BOOT_BUTTON_PRESS,
    RVA_BOOT_ACTIVE,
    SIG_BOOT_ACTIVE,
    RVA_BOOT_INTERACTABLE,
    SIG_BOOT_INTERACTABLE,
    FIELD_BOOT_START_BUTTON,
    FIELD_BOOT_START_CALLBACK,
    RVA_ALBUM_INSTANCE,
    SIG_ALBUM_INSTANCE,
    RVA_ALBUM_JACKET,
    SIG_ALBUM_JACKET,
    RVA_ALBUM_SELECTED,
    SIG_ALBUM_SELECTED,
    RVA_AIME_UPDATE,
    SIG_AIME_UPDATE,
    RVA_AIME_START,
    SIG_AIME_START,
    RVA_AIME_USE,
    SIG_AIME_USE,
    RVA_AIME_MANAGER_EXECUTE,
    SIG_AIME_MANAGER_EXECUTE,
    RVA_AIME_ERROR_POLL,
    SIG_AIME_ERROR_POLL,
    RVA_AIME_ERROR_RESULT,
    SIG_AIME_ERROR_RESULT,
    RVA_AIME_ERROR_WINDOW,
    SIG_AIME_ERROR_WINDOW,
    RVA_AIME_UNIT_START,
    SIG_AIME_UNIT_START,
    RVA_LED_PWM,
    SIG_LED_PWM,
    RVA_LED_BLOCK_COLOR,
    SIG_LED_BLOCK_COLOR,
    RVA_AIME_ADVCHECK,
    SIG_AIME_ADVCHECK,
    RVA_AIME_ANYREAD,
    SIG_AIME_ANYREAD,
};
}
