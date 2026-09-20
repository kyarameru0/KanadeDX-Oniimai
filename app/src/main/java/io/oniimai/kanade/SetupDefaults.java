package io.oniimai.kanade;

import java.util.LinkedHashMap;
import java.util.Map;

/** Apply absent defaults only; user choices survive both upgrades and first-run setup. */
final class SetupDefaults {
    static Map<String,Object> missing(Map<String,?> saved) {
        Map<String,Object> patch=new LinkedHashMap<>();
        if(!saved.containsKey("setup_complete"))patch.put("setup_complete",!saved.isEmpty());
        if(!saved.containsKey("touch_command"))patch.put("touch_command",false);
        if(!saved.containsKey("button_mode"))patch.put("button_mode",2);
        if(!saved.containsKey("led_enabled"))patch.put("led_enabled",true);
        if(!saved.containsKey("aime_enabled"))patch.put("aime_enabled",true);
        if(!saved.containsKey("aime_led_enabled"))patch.put("aime_led_enabled",true);
        if(!saved.containsKey("led_ceiling"))patch.put("led_ceiling",true);
        if(!saved.containsKey("external_enabled"))patch.put("external_enabled",true);
        if(!saved.containsKey("auto_connect"))patch.put("auto_connect",true);
        return patch;
    }
}
