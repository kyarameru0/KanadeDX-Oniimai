package io.oniimai.kanade;

public final class UnityStartupTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("check " + checks); }
    public static void main(String[] args) {
        check(UnityStartup.needsGles(36,"Xiaomi","Redmi"));
        check(UnityStartup.needsGles(36,"unknown","POCO"));
        check(!UnityStartup.needsGles(35,"Xiaomi","Redmi"));
        check(!UnityStartup.needsGles(36,"samsung","samsung"));
        check(!UnityStartup.needsGles(36,null,null));
        check(!UnityStartup.needsGles(36,"notxiaomi","other"));
        check(UnityStartup.commandLine(null).equals("-force-gles30"));
        check(UnityStartup.commandLine("-force-vulkan").equals("-force-gles30"));
        check(UnityStartup.commandLine("-force-gles31aep").equals("-force-gles30"));
        check(UnityStartup.commandLine("-logFile \"a b\" -force-vulkan").equals("-logFile \"a b\" -force-gles30"));
        check(UnityStartup.commandLine("--custom-force-vulkan").equals("--custom-force-vulkan -force-gles30"));
        String first=UnityStartup.commandLine("-screen-fullscreen 1 -force-vulkan");
        check(UnityStartup.commandLine(first).equals(first));
        System.out.println("PASS: " + checks + " Unity startup regression checks");
    }
}
