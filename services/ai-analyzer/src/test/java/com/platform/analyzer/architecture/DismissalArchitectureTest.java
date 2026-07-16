package com.platform.analyzer.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit tests enforcing Clean Architecture compliance for the analysis-dismissal feature.
 * Validates: Requirements 8.1, 8.2, 8.3
 */
@Tag("Feature: analysis-dismissal")
class DismissalArchitectureTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.platform.analyzer");
    }

    @Test
    @DisplayName("Domain model has zero Spring framework imports")
    void domainModelHasNoSpringDependencies() {
        noClasses()
                .that().resideInAPackage("..domain.model..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..")
                .because("Domain model must remain framework-independent (Requirement 8.1)")
                .check(importedClasses);
    }

    @Test
    @DisplayName("Domain ports have zero Spring framework imports")
    void domainPortsHaveNoSpringDependencies() {
        noClasses()
                .that().resideInAPackage("..domain.ports..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..")
                .because("Domain ports must remain framework-independent (Requirement 8.1)")
                .check(importedClasses);
    }

    @Test
    @DisplayName("Service layer does not depend on infrastructure layer")
    void serviceLayerDoesNotDependOnInfrastructure() {
        noClasses()
                .that().resideInAPackage("..service..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..infrastructure..")
                .because("Service layer must depend only on domain ports, not infrastructure (Requirement 8.2)")
                .check(importedClasses);
    }

    @Test
    @DisplayName("Domain layer does not depend on service or infrastructure layers")
    void domainDoesNotDependOnOuterLayers() {
        noClasses()
                .that().resideInAnyPackage("..domain.model..", "..domain.ports..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..service..", "..infrastructure..")
                .because("Domain layer must not reference outer layers (Requirement 8.1)")
                .check(importedClasses);
    }

    @Test
    @DisplayName("OpenSearchLifecycleRepositoryAdapter implements AnalysisLifecycleRepositoryPort")
    void lifecycleRepositoryAdapterImplementsDomainPort() {
        classes()
                .that().haveSimpleName("OpenSearchLifecycleRepositoryAdapter")
                .should().implement(com.platform.analyzer.domain.ports.AnalysisLifecycleRepositoryPort.class)
                .because("Persistence adapters must implement domain repository ports (Requirement 8.3)")
                .check(importedClasses);
    }

    @Test
    @DisplayName("KafkaLifecycleMessagingAdapter implements LifecycleMessagingPort")
    void lifecycleMessagingAdapterImplementsDomainPort() {
        classes()
                .that().haveSimpleName("KafkaLifecycleMessagingAdapter")
                .should().implement(com.platform.analyzer.domain.ports.LifecycleMessagingPort.class)
                .because("Messaging adapters must implement domain messaging ports (Requirement 8.3)")
                .check(importedClasses);
    }

    @Test
    @DisplayName("KafkaMessagingAdapter implements LmMessagingPort")
    void messagingAdapterImplementsDomainPort() {
        classes()
                .that().haveSimpleName("KafkaMessagingAdapter")
                .should().implement(com.platform.analyzer.domain.ports.LmMessagingPort.class)
                .because("Messaging adapters must implement domain messaging ports (Requirement 8.3)")
                .check(importedClasses);
    }

    @Test
    @DisplayName("OpenSearchAnalysisRepository implements AiAnalysisRepositoryPort")
    void analysisRepositoryAdapterImplementsDomainPort() {
        classes()
                .that().haveSimpleName("OpenSearchAnalysisRepository")
                .should().implement(com.platform.analyzer.domain.ports.AiAnalysisRepositoryPort.class)
                .because("Persistence adapters must implement domain repository ports (Requirement 8.3)")
                .check(importedClasses);
    }

    @Test
    @DisplayName("OpenSearchAnalysisQueryAdapter implements AiAnalysisQueryPort")
    void queryAdapterImplementsDomainPort() {
        classes()
                .that().haveSimpleName("OpenSearchAnalysisQueryAdapter")
                .should().implement(com.platform.analyzer.domain.ports.AiAnalysisQueryPort.class)
                .because("Query adapters must implement domain query ports (Requirement 8.3)")
                .check(importedClasses);
    }
}
