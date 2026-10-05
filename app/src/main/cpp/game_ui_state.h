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

// KanadeDX lays out its main camera and top screen from Screen size, and redoes that only when
// Screen.orientation differs from the value it cached (KanadeDXGameControl.prevRot). Moving the
// output to or from the monitor changes the surface's aspect but not its orientation, so for a
// short while afterwards the cache is invalidated every few frames and the game re-runs its layout
// once Unity reports the new surface size.
struct Relayout {
    static constexpr int FRAMES=120,EVERY=4;
    bool external=false;
    int frames=0;
    // Called once per control Update; true when the cached orientation should be invalidated now.
    bool frame(bool nowExternal){
        if(nowExternal!=external){external=nowExternal;frames=FRAMES;}
        if(frames<=0)return false;
        bool due=frames%EVERY==0;--frames;return due;
    }
};

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
