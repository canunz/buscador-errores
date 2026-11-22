package cl.casol.backend.whatsapp.infrastructure.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.context.annotation.Profile;
@Profile(PerfilesWhatsApp.DURABLE)
public interface EventoWhatsAppRepository extends JpaRepository<EventoWhatsAppEntity,Long> { }
