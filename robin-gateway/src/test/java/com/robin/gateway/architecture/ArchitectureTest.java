package com.robin.gateway.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Architecture tests for Robin Gateway.
 *
 * Validates architectural patterns:
 * - Package structure
 * - Dependency rules
 * - Layered architecture
 * - Naming conventions
 * - Spring best practices
 *
 * @author Robin Gateway Team
 */
@Tag("architecture")
@AnalyzeClasses(packages = "com.robin.gateway")
public class ArchitectureTest {

    @ArchTest
    static final ArchRule services_should_be_in_service_package =
        classes().that().areAnnotatedWith(Service.class)
            .should().resideInAnyPackage("..service..", "..auth..")
            .as("Services should reside in service or auth package");

    @ArchTest
    static final ArchRule controllers_should_be_in_controller_package =
        classes().that().areAnnotatedWith(RestController.class)
            .or().areAnnotatedWith(Controller.class)
            .should().resideInAnyPackage("..controller..", "..auth..")
            .as("Controllers should reside in controller or auth package");

    @ArchTest
    static final ArchRule no_field_injection =
        fields().that().areDeclaredInClassesThat().resideOutsideOfPackages("..integration..", "..performance..")
            .should().notBeAnnotatedWith(Autowired.class)
            .as("Field injection is not allowed in production code - use constructor injection");

    @ArchTest
    static final ArchRule services_should_not_depend_on_controllers =
        noClasses().that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAPackage("..controller..")
            .because("Services should not depend on controllers to maintain strict layering")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule repositories_should_not_depend_on_services =
        noClasses().that().resideInAPackage("..repository..")
            .should().dependOnClassesThat().resideInAPackage("..service..")
            .as("Repositories should not depend on services");

    @ArchTest
    static final ArchRule layered_architecture_respected =
        layeredArchitecture()
            .consideringAllDependencies()
            .layer("Controller").definedBy("..controller..")
            .layer("Auth").definedBy("..auth..")
            .layer("Service").definedBy("..service..")
            .layer("Repository").definedBy("..repository..")
            .layer("Model").definedBy("..model..", "..model.dto..")
            .layer("Config").definedBy("..config..")
            .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
            .whereLayer("Auth").mayOnlyBeAccessedByLayers("Controller", "Config")
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller", "Auth", "Config")
            .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service", "Auth", "Controller")
            .whereLayer("Config").mayNotBeAccessedByAnyLayer()
            .as("Layered architecture should be respected");
}
