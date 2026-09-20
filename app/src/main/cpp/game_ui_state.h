#pragma once
#include <stdint.h>

namespace GameUiState {
static constexpr const char* COMPACT_KEY="KndI-KanadeDXInGameSettingcompactMode";
static constexpr const char* MIGRATION_KEY="Oniimai-CompactDefaultOff-v1";

// Upgrade once, then respect explicit choices made in the game's settings.
template<class Prefs> bool migrateCompact(Prefs& prefs){
    if(prefs.getInt(MIGRATION_KEY,0)==1)return false;
    prefs.setInt(COMPACT_KEY,0);
    prefs.setInt(MIGRATION_KEY,1);
    prefs.save();
    return true;
}

struct Visibility {float alpha;bool interactable,raycasts;};
// A managed handle keeps the snapshot's object rooted; the adapter separately
// checks Unity's native-object lifetime before reading or restoring it.
struct TemporaryHide {
    uint32_t handle=0;
    Visibility saved{};
    template<class Objects> void update(Objects& objects,void* current,bool hide){
        if(handle){
            void* previous=objects.resolve(handle);
            if(!hide||previous!=current||!objects.alive(previous)){
                if(objects.alive(previous))objects.write(previous,saved);
                objects.release(handle);handle=0;
            }
        }
        if(!hide||!objects.alive(current))return;
        if(!handle){
            saved=objects.read(current);handle=objects.hold(current);
            if(!handle)return;
        }
        objects.write(current,{0,false,false});
    }
};
}
