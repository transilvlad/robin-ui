package com.robin.gateway.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
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
@AnalyzeClasses(packages = "com.robin.gateway")
public class ArchitectureTest {

    @ArchTest
    static final ArchRule services_should_be_in_service_package =
        classes().that().areAnnotatedWith(Service.class)
            .should().resideInAPackage("..service..")
            .as("Services should reside in service package");

    @ArchTest
    static final ArchRule controllers_should_be_in_controller_package =
        classes().that().areAnnotatedWith(RestController.class)
            .or().areAnnotatedWith(Controller.class)
            .should().resideInAPackage("..controller..")
            .as("Controllers should reside in controller package");

    @ArchTest
    static final ArchRule no_field_injection =
        noFields().should().beAnnotatedWith(Autowired.class)
            .as("Field injection is not allowed - use constructor injection");

    @ArchTest
    static final ArchRule services_should_not_depend_on_controllers =
        noClasses().that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAPackage("..controller..")
            .as("Services should not depend on controllers");

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
            .layer("Service").definedBy("..service..")
            .layer("Repository").definedBy("..repository..")
            .layer("Model").definedBy("..model..")
            .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")
            .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")
            .as("Layered architecture should be respected");
}
