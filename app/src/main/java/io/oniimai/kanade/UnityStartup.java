package io.oniimai.kanade;

import java.util.Locale;

/** Startup-only workaround for the reproduced Unity Vulkan swapchain crash. */
final class UnityStartup {
    private UnityStartup() {}
    static boolean needsGles(int sdk, String manufacturer, String brand) {
        return sdk >= 36 && (xiaomi(manufacturer) || xiaomi(brand));
    }
    private static boolean xiaomi(String name) {
        if (name == null) return false;
        String value = name.trim().toLowerCase(Locale.ROOT);
        return value.equals("xiaomi") || value.equals("redmi") || value.equals("poco");
    }
    static String commandLine(String original) {
        String args = original == null ? "" : original;
        // Replace only standalone Unity renderer switches; retain unrelated arguments verbatim.
        args = args.replaceAll("(?<!\\S)-force-(?:vulkan|gles(?:20|30|31|31aep|32)?)(?!\\S)", "").trim();
        return args.isEmpty() ? "-force-gles30" : args + " -force-gles30";
    }
}
