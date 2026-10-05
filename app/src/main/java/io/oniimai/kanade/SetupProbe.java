package io.oniimai.kanade;

/**
 * What first-run setup may read from, and ask of, the running session. Until setup reaches its connection
 * step the session leaves USB alone, as before; from then on setup may let it look for the controller and
 * ask for USB permission, so the connection step can show what really happens.
 */
interface SetupProbe {
    /** As {@link DashboardHost#diagnostic()}: buttons (bits 0-7 ring), the 34 touch bits, armed, readiness (bit 0 touch, bit 1 buttons). */
    long[] diagnostic();
    /** Whether setup's lighting reaches the controller: the ring LED board is linked, or the ceiling light's IO4 port is open. */
    boolean ledLinked();
    /**
     * Whether a presentation display (the controller's built-in screen) is attached. It says nothing about
     * output: before setup is finished, or with output turned off, nothing is shown on it.
     */
    boolean external();
    /** Whether the game is being shown on that display right now. */
    boolean externalActive();
    /**
     * What setup is showing, for the controller itself: its lights follow the step, and its built-in screen
     * shows setup's page for the step in front of whatever was there (which turns with the rotation choice too).
     */
    void setupView(SetupView view);
    /**
     * Setup's five shared choices as the detailed settings show them: output and auto-connect as saved, the
     * rotation as the controller's screen is turned now (setup's preview included), and LED and Aime as running.
     */
    SetupChoices setupChoices();
    /** One settings tab (0 connection, 1 screen, 2 buttons, 3 LED) for setup's detailed settings; changes save at once. */
    java.util.List<NativeSettings.Group> setupSettings(int tab);
    /** Lets the session look for the controller and ask for USB permission while setup is still open. */
    void setupSearch();
    /**
     * A {@link SetupLink} state: 0 looking, 1 waiting for USB permission, 2 connected with every expected input
     * open, 3 permission refused or the ports failed to open, 4 connected but touch or buttons did not open;
     * -1 not looking.
     */
    int setupLink();
    /** The inputs open right now, from the channels that really opened: bit 0 touch, bit 1 buttons. */
    int setupInputs();
    /** Forgets a refused permission or a failed opening and looks again. */
    void setupRetry();
}
