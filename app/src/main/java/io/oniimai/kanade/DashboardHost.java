package io.oniimai.kanade;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.function.IntConsumer;

/** Game-session boundary for the in-app dashboard; no input edges are consumed by the UI. */
interface DashboardHost {
    Activity activity();
    /** The launcher preview has its own preferences and does not control a game session. */
    default boolean demo(){return false;}
    /** Module-owned asset context, even when the views live inside the hooked game's Activity. */
    Context assetContext();
    SharedPreferences prefs();
    /** buttons (8 ring bits + P1 bit 8), 34 touch bits, neutral flag, readiness bits (touch=1/buttons=2). */
    long[] diagnostic();
    String connectionDescription();
    String ledDescription();
    String displayDescription();
    /** Status tones for the device widget (NativeSettings.INFO/OK/WAIT); NONE lets the widget fall back to the text. */
    default int inputTone(){return NativeSettings.NONE;}
    default int ledTone(){return NativeSettings.NONE;}
    default int displayTone(){return NativeSettings.NONE;}
    boolean externalActive();
    default boolean requestStartup(){return false;}
    /** Shared button lighting for the external welcome screen; null when the host drives no LEDs. */
    default LobbyLights lobbyLights(){return null;}
    boolean ledEnabled();
    void showSettings();
    /** Called on the UI thread about every 150 ms. Supply current stats using view.update(). */
    void updateDashboard(DashboardView view);
    /** Suspend controller-to-game input while rearranging widgets, without stopping USB diagnostics. */
    void setDashboardEditing(boolean editing);
    /** Present a protected choice dialog; invoke action only for a selected option. */
    void choose(String title,String[] options,int selected,IntConsumer action);
}
