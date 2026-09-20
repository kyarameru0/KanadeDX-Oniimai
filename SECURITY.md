# Security and privacy

Do not attach card numbers, UIDs, raw NFC blocks, credentials, signing keys or game dumps to issues. Reports should include the module version, device/Android version, connection type and redacted error category.

Card results are delivered to the current game session. Module diagnostics do not record their identities or access codes. The game's own account and network behavior remains governed by the game. Running inside its process does not provide isolation from that process.

The phone NFC service checks Binder caller UIDs against an installed-package allowlist. This is package-name-based validation, not authentication of the original game developer's signing certificate. Do not install an untrusted game with the same package name. Compatibility fingerprints are also not a security boundary.

Use GitHub's private vulnerability-reporting route if enabled. Otherwise, request a private contact route from the repository owner without posting sensitive details. No independent security audit has been completed.
