package io.carbongate.security;

public final class SecretScannerTest {
    public static void run() {
        SecretScanner scanner = new SecretScanner();
        var clean = scanner.scan("hello world");
        assert !clean.containsSecrets();
        var assigned = scanner.scan("password=correct-horse-battery-staple");
        assert assigned.containsSecrets();
        assert !assigned.redacted().contains("correct-horse");
        assert assigned.redacted().contains("<SECRET:ASSIGNED_SECRET:1>");
        // Split the synthetic token so repository secret scanners do not treat
        // test data as a committed credential.
        var key = scanner.scan("sk-" + "1234567890abcdefghijklmnopqrstuv");
        assert key.findings().contains("OPENAI_API_KEY");
    }
}
