#pragma once
namespace Target165 {
// Verified against KanadeDX 1.65 / KanadeDX-260721.1649.apk.1 only.
static const unsigned char BUILD_ID[] = {0xa2,0x6b,0xb6,0xce,0xee,0x64,0xf1,0xc9,0x81,0x47,0x82,0xc8,0x8c,0x5d,0x59,0x3b,0xb3,0x26,0x9c,0xd6};
static constexpr uintptr_t RVA_TOUCH = 0x275a088;
static constexpr uint64_t SIG_TOUCH = 0x630697f0a383980aULL;
static constexpr uintptr_t RVA_FRAME = 0x25ee1b8;
static constexpr uint64_t SIG_FRAME = 0x786675b99e01b4abULL;
static constexpr uintptr_t RVA_RAW = 0x275463c;
static constexpr uint64_t SIG_RAW = 0x46b336fa29660ab8ULL;
static constexpr uintptr_t RVA_DOWN = 0x2754700;
static constexpr uint64_t SIG_DOWN = 0x46b336fa29660ab8ULL;
static constexpr uintptr_t RVA_LED_COLOR = 0x22d1270;
static constexpr uint64_t SIG_LED_COLOR = 0x9975daf9c746921cULL;
static constexpr uintptr_t RVA_LED_PRESS = 0x22d1154;
static constexpr uint64_t SIG_LED_PRESS = 0x3877e3a120e73c1fULL;
static constexpr uintptr_t RVA_LED_ALL = 0x22d1900;
static constexpr uint64_t SIG_LED_ALL = 0xb426360a9523ee2fULL;
static constexpr uintptr_t RVA_LED_OFF = 0x22d0ec4;
static constexpr uint64_t SIG_LED_OFF = 0xdd3982341de151b8ULL;
static constexpr uintptr_t RVA_LED_FADE = 0x22d1350;
static constexpr uint64_t SIG_LED_FADE = 0xcff623f2d1727a15ULL;
static constexpr uintptr_t RVA_LED_RING = 0x22d0ff4;
static constexpr uint64_t SIG_LED_RING = 0xefbe7c66e88e4559ULL;
static constexpr uintptr_t RVA_LED_FET = 0x22d180c;
static constexpr uint64_t SIG_LED_FET = 0xa5a3a774e400d5bcULL;
static constexpr uintptr_t RVA_LED_RING_FADE = 0x22d15b4;
static constexpr uint64_t SIG_LED_RING_FADE = 0x682c0385f9991121ULL;
static constexpr uintptr_t RVA_GAME_START = 0x225ba24;
static constexpr uint64_t SIG_GAME_START = 0xf770495f77be3e9fULL;
static constexpr uintptr_t RVA_GAME_RELEASE = 0x2265128;
static constexpr uint64_t SIG_GAME_RELEASE = 0xf948478bb0f4d0c6ULL;
static constexpr uintptr_t RVA_RESULT_START = 0x22e5ad4;
static constexpr uint64_t SIG_RESULT_START = 0x767e081c8f8d18d2ULL;
static constexpr uintptr_t RVA_RESULT_RELEASE = 0x22efa0c;
static constexpr uint64_t SIG_RESULT_RELEASE = 0xc24365155903f627ULL;
static constexpr uintptr_t RVA_SCORE_UPDATE = 0x25bd438;
static constexpr uint64_t SIG_SCORE_UPDATE = 0x919f08c8c47b4c71ULL;
static constexpr uintptr_t RVA_SCORE_UTAGE = 0x25bc908;
static constexpr uint64_t SIG_SCORE_UTAGE = 0x3f13df539502d3b2ULL;
static constexpr uintptr_t RVA_SCORE_FINISH = 0x25c82e0;
static constexpr uint64_t SIG_SCORE_FINISH = 0xc99209f613f61bb0ULL;
static constexpr uintptr_t RVA_MUSIC_GET = 0x258fd88;
static constexpr uint64_t SIG_MUSIC_GET = 0x0975e006d29cdf63ULL;
static constexpr uintptr_t RVA_NOTES_UPDATE = 0x2610518;
static constexpr uint64_t SIG_NOTES_UPDATE = 0x5d2bb2955093d243ULL;
static constexpr uintptr_t RVA_CURRENT_MSEC = 0x26108d4;
static constexpr uint64_t SIG_CURRENT_MSEC = 0x91ba00f5f784296aULL;

// Game control UI/defaults: verified against the same exact APK and metadata.
static constexpr uintptr_t RVA_UI_SETTINGS_CCTOR = 0x21c59b8;
static constexpr uint64_t SIG_UI_SETTINGS_CCTOR = 0xe4634d83398d0e4aULL;
static constexpr uintptr_t RVA_UI_SETTINGS_LOAD = 0x21c3b74;
static constexpr uint64_t SIG_UI_SETTINGS_LOAD = 0x5b7757f91e52ff35ULL;
static constexpr uintptr_t RVA_UI_CONTROL_UPDATE = 0x21c01a4;
static constexpr uint64_t SIG_UI_CONTROL_UPDATE = 0x3ec6c0b75c4b6cd5ULL;
static constexpr uintptr_t RVA_UI_PREFS_GETINT = 0x22deaac;
static constexpr uint64_t SIG_UI_PREFS_GETINT = 0x62ae4f20388ae283ULL;
static constexpr uintptr_t RVA_UI_PREFS_SETINT = 0x22de9f0;
static constexpr uint64_t SIG_UI_PREFS_SETINT = 0x62ae4f20388ae283ULL;
static constexpr uintptr_t RVA_UI_PREFS_SAVE = 0x22df06c;
static constexpr uint64_t SIG_UI_PREFS_SAVE = 0xdc0724a1ad62deb1ULL;
static constexpr uintptr_t RVA_UI_GROUP_ALPHA_GET = 0x51d6170;
static constexpr uint64_t SIG_UI_GROUP_ALPHA_GET = 0xf71dc97f7a076f0dULL;
static constexpr uintptr_t RVA_UI_GROUP_ALPHA_SET = 0x51d61ac;
static constexpr uint64_t SIG_UI_GROUP_ALPHA_SET = 0x4e5a99ea13ae1ca8ULL;
static constexpr uintptr_t RVA_UI_GROUP_INTERACTABLE_GET = 0x51d61f8;
static constexpr uint64_t SIG_UI_GROUP_INTERACTABLE_GET = 0x687b5180dcb88875ULL;
static constexpr uintptr_t RVA_UI_GROUP_RAYCAST_GET = 0x51d6234;
static constexpr uint64_t SIG_UI_GROUP_RAYCAST_GET = 0xd06d35811798aed9ULL;
static constexpr uintptr_t RVA_UI_GROUP_RAYCAST_SET = 0x51d6270;
static constexpr uint64_t SIG_UI_GROUP_RAYCAST_SET = 0x640375ed688a32e4ULL;
static constexpr uintptr_t RVA_UI_OBJECT_ALIVE = 0x50014ec;
static constexpr uint64_t SIG_UI_OBJECT_ALIVE = 0x58745e537e93ad6eULL;
static constexpr uintptr_t DATA_UI_SETTINGS_TYPEINFO = 0x563a000;
static constexpr uintptr_t FIELD_UI_MAIN_GROUP = 0x20;

static constexpr uintptr_t RVA_BOOT_UPDATE = 0x52062ec;
static constexpr uint64_t SIG_BOOT_UPDATE = 0x5b7757f91e52ff35ULL;

static constexpr uintptr_t RVA_BOOT_STARTED = 0x22dc6b0;
static constexpr uint64_t SIG_BOOT_STARTED = 0x905885a5c1cdcb00ULL;

static constexpr uintptr_t RVA_BOOT_CANVAS = 0x22db154;
static constexpr uint64_t SIG_BOOT_CANVAS = 0x2155fd953b4a39f5ULL;

static constexpr uintptr_t RVA_BOOT_BUTTON_PRESS = 0x5078a94;
static constexpr uint64_t SIG_BOOT_BUTTON_PRESS = 0xe4f9624a1026514eULL;

static constexpr uintptr_t RVA_BOOT_ACTIVE = 0x520fe88;
static constexpr uint64_t SIG_BOOT_ACTIVE = 0xd51536e91d3a536eULL;

static constexpr uintptr_t RVA_BOOT_INTERACTABLE = 0x51f56c8;
static constexpr uint64_t SIG_BOOT_INTERACTABLE = 0xd2b4f32811f74f36ULL;

static constexpr uintptr_t FIELD_BOOT_START_BUTTON = 0x60;
static constexpr uintptr_t FIELD_BOOT_START_CALLBACK = 0xd0;

static constexpr uintptr_t RVA_ALBUM_INSTANCE = 0x24cadc8;
static constexpr uint64_t SIG_ALBUM_INSTANCE = 0x49d5ac67e3fb87baULL;
static constexpr uintptr_t RVA_ALBUM_JACKET = 0x24caf94;
static constexpr uint64_t SIG_ALBUM_JACKET = 0x423cd412c71b3b4bULL;
static constexpr uintptr_t RVA_ALBUM_SELECTED = 0x25b2f5c;
static constexpr uint64_t SIG_ALBUM_SELECTED = 0xd24d4db273a5dd16ULL;

// Physical Aime card scan bridge, exact KanadeDX 1.65 image only.
static constexpr uintptr_t RVA_AIME_UPDATE = 0x2770ccc;
static constexpr uint64_t SIG_AIME_UPDATE = 0x208fab1dcac16604ULL;
static constexpr uintptr_t RVA_AIME_START = 0x2770c6c;
static constexpr uint64_t SIG_AIME_START = 0x8cbb65000687602bULL;
static constexpr uintptr_t RVA_AIME_USE = 0x2771488;
static constexpr uint64_t SIG_AIME_USE = 0x254632d83cdf666bULL;

static constexpr uintptr_t RVA_AIME_MANAGER_EXECUTE = 0x258764c;
static constexpr uint64_t SIG_AIME_MANAGER_EXECUTE = 0x4966c7e9edabc0dfULL;

// Read-only guards for the original recoverable Aime error state transition.
static constexpr uintptr_t RVA_AIME_ERROR_POLL = 0x2587968;
static constexpr uint64_t SIG_AIME_ERROR_POLL = 0xcf988a789526ea78ULL;
static constexpr uintptr_t RVA_AIME_ERROR_RESULT = 0x2587a54;
static constexpr uint64_t SIG_AIME_ERROR_RESULT = 0x0cfb208aafbadfefULL;
static constexpr uintptr_t RVA_AIME_ERROR_WINDOW = 0x251ce38;
static constexpr uint64_t SIG_AIME_ERROR_WINDOW = 0x007757b96e3934c6ULL;
static constexpr uintptr_t RVA_AIME_UNIT_START = 0x276f9f8;
static constexpr uint64_t SIG_AIME_UNIT_START = 0x0385d52360792804ULL;

// Independent JVS billboard RGB output; PWM target is exactly one RET.
static constexpr uintptr_t RVA_LED_PWM = 0x27547c8;
static constexpr uint64_t SIG_LED_PWM = 0x697f03e5867031e2ULL;
static constexpr uintptr_t RVA_LED_BLOCK_COLOR = 0x26b0a24;
static constexpr uint64_t SIG_LED_BLOCK_COLOR = 0xb2ccb27dfc7a69ddULL;

// AdvCheck gate for a completed adapter-owned physical read failure.
static constexpr uintptr_t RVA_AIME_ADVCHECK = 0x2587c18;
static constexpr uint64_t SIG_AIME_ADVCHECK = 0x3b3dd936179896c4ULL;

static constexpr uintptr_t RVA_AIME_ANYREAD = 0x2587c38;
static constexpr uint64_t SIG_AIME_ANYREAD = 0xc91d5c5366d1fab9ULL;

static constexpr TargetBuild PROFILE = {
    BUILD_ID, sizeof(BUILD_ID), "KanadeDX 1.65 (260721.1649)",
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
