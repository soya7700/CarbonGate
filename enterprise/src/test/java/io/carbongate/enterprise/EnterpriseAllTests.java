package io.carbongate.enterprise;

import io.carbongate.enterprise.component.ComponentManagerTest;
import io.carbongate.enterprise.component.ComponentPackageBuilderTest;
import io.carbongate.enterprise.component.PackDocumentTest;
import io.carbongate.enterprise.component.EnterpriseGuardPipelineTest;
import io.carbongate.enterprise.component.ComponentTrustStoreTest;

public final class EnterpriseAllTests {
    public static void main(String[] args) throws Exception {
        ComponentManagerTest.run();
        PackDocumentTest.run();
        ComponentPackageBuilderTest.run();
        EnterpriseGuardPipelineTest.run();
        ComponentTrustStoreTest.run();
        System.out.println("All CarbonGate Enterprise Component Host tests passed.");
    }
}
