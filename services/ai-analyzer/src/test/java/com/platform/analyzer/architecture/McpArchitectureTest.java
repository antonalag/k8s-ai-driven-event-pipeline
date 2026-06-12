package com.platform.analyzer.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit tests validating MCP Intelligence Layer architecture compliance.
 * Ensures Clean Architecture rules are enforced for all MCP-related classes.
 *
 * Validates: Requirements 5.1, 6.1
 */
@Tag("Feature: mcp-intelligence-layer")
class McpArchitectureTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void setup() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.platform.analyzer");
    }

    @Test
    void mcpContextPortShouldResideInDomainPortsPackage() {
        classes()
                .that().haveSimpleName("McpContextPort")
                .should().resideInAPackage("com.platform.analyzer.domain.ports")
                .because("McpContextPort is a domain port and must reside in domain.ports (Requirement 5.1)")
                .check(importedClasses);
    }

    @Test
    void enrichedContextShouldResideInDomainModelPackage() {
        classes()
                .that().haveSimpleName("EnrichedContext")
                .should().resideInAPackage("com.platform.analyzer.domain.model")
                .because("EnrichedContext is a domain value object and must reside in domain.model (Requirement 5.1)")
                .check(importedClasses);
    }

    @Test
    void domainLayerShouldNotImportInfrastructure() {
        noClasses()
                .that().resideInAnyPackage("com.platform.analyzer.domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.platform.analyzer.infrastructure..")
                .because("Domain layer must not depend on infrastructure — Clean Architecture (Requirement 5.1)")
                .check(importedClasses);
    }

    @Test
    void mcpClientAdapterShouldImplementMcpContextPort() {
        classes()
                .that().haveSimpleName("McpClientAdapter")
                .should().implement(com.platform.analyzer.domain.ports.McpContextPort.class)
                .because("McpClientAdapter is the infrastructure adapter for McpContextPort (Requirement 5.1)")
                .check(importedClasses);
    }

    @Test
    void mcpClientAdapterShouldResideInInfrastructureClientMcpPackage() {
        classes()
                .that().haveSimpleName("McpClientAdapter")
                .should().resideInAPackage("com.platform.analyzer.infrastructure.client.mcp")
                .because("McpClientAdapter is an infrastructure adapter and must reside in infrastructure.client.mcp (Requirement 5.1)")
                .check(importedClasses);
    }

    @Test
    void resilientMcpContextAdapterShouldResideInConfigPackage() {
        classes()
                .that().haveSimpleName("ResilientMcpContextAdapter")
                .should().resideInAPackage("com.platform.analyzer.config")
                .because("ResilientMcpContextAdapter follows the existing ResilienceConfig pattern in the config layer (Requirement 6.1)")
                .check(importedClasses);
    }
}
