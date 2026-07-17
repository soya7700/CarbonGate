package io.carbongate.enterprise;

import io.carbongate.enterprise.component.ComponentManagerTest;

public final class EnterpriseAllTests {
    public static void main(String[] args) throws Exception {
        ComponentManagerTest.run();
        System.out.println("All CarbonGate Enterprise Component Host tests passed.");
    }
}
