package cl.casol.backend.tickets.arquitectura;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Reglas de arquitectura del módulo de tickets (monolito modular + hexagonal). */
class TicketsArquitecturaTest {
    private static final String BASE = "cl.casol.backend";
    private static JavaClasses clases;

    @BeforeAll
    static void importar() {
        clases = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE);
    }

    @Test
    void elDominioNoDependeDeSpringNiJpa() {
        noClasses().that().resideInAPackage(BASE + ".tickets.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta.persistence..", "jakarta.validation..",
                        "org.hibernate..", "lombok..")
                .because("el dominio de tickets debe ser Java puro")
                .check(clases);
    }

    @Test
    void elDominioNoDependeDeAplicacionNiInfraestructura() {
        noClasses().that().resideInAPackage(BASE + ".tickets.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".tickets.application..", BASE + ".tickets.infrastructure..")
                .check(clases);
    }

    @Test
    void laAplicacionNoDependeDeInfraestructura() {
        noClasses().that().resideInAPackage(BASE + ".tickets.application..")
                .should().dependOnClassesThat().resideInAPackage(BASE + ".tickets.infrastructure..")
                .check(clases);
    }

    @Test
    void ticketsNoEntraAlDominioNiInfraestructuraDeOtrosModulos() {
        noClasses().that().resideInAPackage(BASE + ".tickets..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".identidad.domain..", BASE + ".identidad.application..", BASE + ".identidad.infrastructure..",
                        BASE + ".catalogo.domain..", BASE + ".catalogo.application..", BASE + ".catalogo.infrastructure..",
                        BASE + ".conocimiento.domain..", BASE + ".conocimiento.application..", BASE + ".conocimiento.infrastructure..",
                        BASE + ".procedimiento..", BASE + ".ejecucion..", BASE + ".seguimiento..")
                .because("tickets solo habla con otros módulos a través de su API pública (paquete api)")
                .check(clases);
    }

    @Test
    void soloLosAdaptadoresDeIntegracionUsanLasApisPublicas() {
        noClasses().that().resideInAPackage(BASE + ".tickets..")
                .and().resideOutsideOfPackage(BASE + ".tickets.infrastructure.integracion..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".identidad.api..", BASE + ".catalogo.api..", BASE + ".conocimiento.api..")
                .check(clases);
    }

    @Test
    void ningunModuloDependeDelInteriorDeTickets() {
        noClasses().that().resideOutsideOfPackage(BASE + ".tickets..")
                .and().resideOutsideOfPackage(BASE + ".shared..")
                .should().dependOnClassesThat().resideInAPackage(BASE + ".tickets..")
                .check(clases);
    }
}
