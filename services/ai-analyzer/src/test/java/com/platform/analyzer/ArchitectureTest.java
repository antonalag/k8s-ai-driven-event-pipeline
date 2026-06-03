package com.platform.analyzer;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit tests validating hexagonal architecture constraints.
 * Feature: dynamic-ai-provider-routing.
 * Validates: Requirements 2.1, 2.2, 2.3, 2.4.
 */
@Tag("Feature: dynamic-ai-provider-routing")
class ArchitectureTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void setup() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.platform.analyzer");
    }

    @Test
    void domainShouldNotDependOnSpringFramework() {
        noClasses()
                .that().resideInAnyPackage("com.platform.analyzer.domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..")
                .because("Domain layer must remain framework-independent (Requirement 2.2)")
                .check(importedClasses);
    }

    @Test
    void domainShouldNotReferenceConcreteAdapters() {
        noClasses()
                .that().resideInAnyPackage("com.platform.analyzer.domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.platform.analyzer.infrastructure..")
                .because("Domain must not reference concrete adapter implementations (Requirement 2.4)")
                .check(importedClasses);
    }

    @Test
    void domainShouldNotReferenceConfigLayer() {
        noClasses()
                .that().resideInAnyPackage("com.platform.analyzer.domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.platform.analyzer.config..")
                .because("Domain must not depend on configuration layer (Requirement 2.3)")
                .check(importedClasses);
    }

    @Test
    void domainAndServiceShouldNotDependOnResilience4j() {
        noClasses()
                .that().resideInAnyPackage("com.platform.analyzer.domain..", "com.platform.analyzer.service..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("io.github.resilience4j..")
                .because("Domain and service layers must remain free of resilience infrastructure (Requirement 6.1)")
                .check(importedClasses);
    }
}
