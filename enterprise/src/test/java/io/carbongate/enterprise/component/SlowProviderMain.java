package io.carbongate.enterprise.component;

public final class SlowProviderMain {
    private SlowProviderMain() {}

    public static void main(String[] args) throws Exception {
        Thread.sleep(2_000);
    }
}
