package cl.casol.backend.tickets.infrastructure.persistence;
import cl.casol.backend.tickets.domain.*;
import cl.casol.backend.tickets.infrastructure.persistence.entity.TicketEntity;
import cl.casol.backend.tickets.infrastructure.persistence.mapper.TicketMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import java.util.*;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
class DatosSolicitantePersistenciaTest {
 @Test void migracionAditivaConservaTicketLegadoYRechazaSnapshotsParciales() {
  var ds=new DriverManagerDataSource("jdbc:h2:mem:sol_migration;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
  var jdbc=new JdbcTemplate(ds);jdbc.execute("CREATE TABLE ticket(id BIGINT PRIMARY KEY,solicitante_id INT NOT NULL,titulo VARCHAR(150))");
  jdbc.update("INSERT INTO ticket VALUES(1,7,'Ticket legado')");
  new ResourceDatabasePopulator(new ClassPathResource("db/migration/V20261116_01__ticket_datos_solicitante.sql")).execute(ds);
  assertEquals("Ticket legado",jdbc.queryForObject("SELECT titulo FROM ticket WHERE id=1",String.class));
  assertNull(jdbc.queryForObject("SELECT sol_nombre_completo FROM ticket WHERE id=1",String.class));
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("UPDATE ticket SET sol_contacto='1234' WHERE id=1"));
  jdbc.update("UPDATE ticket SET sol_nombre_completo='Persona sintética',sol_sucursal_id=1,sol_sucursal_nombre='Sucursal QA',sol_departamento_id=2,sol_departamento_nombre='Área QA',sol_contacto='anexo 1234' WHERE id=1");
  assertEquals(7,jdbc.queryForObject("SELECT solicitante_id FROM ticket WHERE id=1",Integer.class));
 }
 @Test void jpaYMapperConservanSnapshotYTicketsAnterioresTrasCambiarEstado() {
  var ds=new DriverManagerDataSource("jdbc:h2:mem:sol_roundtrip;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
  var bean=new LocalContainerEntityManagerFactoryBean();bean.setDataSource(ds);
  bean.setPackagesToScan("cl.casol.backend.tickets.infrastructure.persistence.entity");bean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
  bean.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","create-drop","hibernate.show_sql","false"));bean.afterPropertiesSet();
  var factory=bean.getObject();assertNotNull(factory);var em=factory.createEntityManager();
  try {
   var datos=new DatosSolicitante("Persona sintética",1,"Sucursal QA",2,"Área QA","+56 9 1234 5678","192.168.1.10","QA-123");
   var ticket=Ticket.crear("TK-SNAPSHOT","T","D",PrioridadTicket.MEDIA,7,null,null,null,LocalDateTime.now());ticket.registrarDatosSolicitante(datos);
   em.getTransaction().begin();var entity=TicketMapper.toEntity(ticket);em.persist(entity);em.getTransaction().commit();Long id=entity.getId();em.clear();
   var leido=TicketMapper.toDomain(em.find(TicketEntity.class,id),List.of(),List.of());assertEquals(datos,leido.getDatosSolicitante());assertEquals(7,leido.getSolicitanteId());
   assertEquals(datos,new cl.casol.backend.tickets.application.port.in.TicketVista(leido,List.of(),Map.of(),null,null,List.of()).paraBasico().ticket().getDatosSolicitante());
   leido.cambiarPrioridad(PrioridadTicket.ALTA,8,"QA",LocalDateTime.now());
   em.getTransaction().begin();em.merge(TicketMapper.toEntity(leido));em.getTransaction().commit();em.clear();
   assertEquals(datos,TicketMapper.toDomain(em.find(TicketEntity.class,id),List.of(),List.of()).getDatosSolicitante());
   var legado=Ticket.crear("TK-LEGADO","Anterior","D",PrioridadTicket.MEDIA,7,null,null,null,LocalDateTime.now());
   em.getTransaction().begin();var viejo=TicketMapper.toEntity(legado);em.persist(viejo);em.getTransaction().commit();em.clear();
   assertNull(TicketMapper.toDomain(em.find(TicketEntity.class,viejo.getId()),List.of(),List.of()).getDatosSolicitante());
  } finally { if(em.getTransaction().isActive())em.getTransaction().rollback();em.close();bean.destroy(); }
 }
 @Test void consultaJdbcRapidaConservaLosDatosDelSolicitante() throws Exception {
  var ds=org.mockito.Mockito.mock(javax.sql.DataSource.class);
  var connection=org.mockito.Mockito.mock(java.sql.Connection.class);
  var statement=org.mockito.Mockito.mock(java.sql.PreparedStatement.class);
  var rs=org.mockito.Mockito.mock(java.sql.ResultSet.class);
  org.mockito.Mockito.when(ds.getConnection()).thenReturn(connection);
  org.mockito.Mockito.when(connection.prepareStatement(org.mockito.ArgumentMatchers.anyString())).thenReturn(statement);
  org.mockito.Mockito.when(statement.executeQuery()).thenReturn(rs);
  org.mockito.Mockito.when(rs.next()).thenReturn(true);
  org.mockito.Mockito.when(rs.getLong(1)).thenReturn(1L);
  org.mockito.Mockito.when(rs.getString(5)).thenReturn("ABIERTO");org.mockito.Mockito.when(rs.getString(6)).thenReturn("MEDIA");
  org.mockito.Mockito.when(rs.getString(29)).thenReturn("Persona QA");
  org.mockito.Mockito.when(rs.getInt(30)).thenReturn(1);org.mockito.Mockito.when(rs.getString(31)).thenReturn("Sucursal QA");
  org.mockito.Mockito.when(rs.getInt(32)).thenReturn(2);org.mockito.Mockito.when(rs.getString(33)).thenReturn("Área QA");
  org.mockito.Mockito.when(rs.getString(34)).thenReturn("anexo 1234");
  var result=new cl.casol.backend.tickets.infrastructure.persistence.adapter.DetalleTicketJdbcAdapter(ds).buscar(1L).orElseThrow();
  assertEquals("Persona QA",result.ticket().getDatosSolicitante().nombreCompleto());
  assertEquals(1,result.ticket().getDatosSolicitante().sucursalId());
 }

}
