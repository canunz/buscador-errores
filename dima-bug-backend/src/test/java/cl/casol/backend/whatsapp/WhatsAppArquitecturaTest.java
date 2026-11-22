package cl.casol.backend.whatsapp;
import com.tngtech.archunit.core.importer.*;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
class WhatsAppArquitecturaTest {
 @Test void dominioSinInfraestructuraNiFramework(){
  var clases=new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests()).importPackages("cl.casol.backend.whatsapp");
  noClasses().that().resideInAPackage("..whatsapp.domain..")
   .should().dependOnClassesThat().resideInAnyPackage("..application..","..infrastructure..","org.springframework..","tools.jackson..").check(clases);
 }
 @Test void aplicacionSoloDependeDePuertosYDominio(){
  var clases=new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests()).importPackages("cl.casol.backend.whatsapp");
  noClasses().that().resideInAPackage("..whatsapp.application..")
   .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..","org.springframework..","tools.jackson..").check(clases);
 }
}
