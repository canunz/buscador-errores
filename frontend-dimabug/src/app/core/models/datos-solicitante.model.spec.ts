import { contactoSolicitanteValido, equipoSolicitanteValido } from './datos-solicitante.model';
describe('Datos declarados del solicitante',()=>{
 it('acepta teléfonos chilenos y anexos habituales',()=>{
  for(const v of ['+56 9 1234 5678','(+56) 65 222 3344','anexo 1234','1234','ext. 1234','600 123 4567','+56 2 2345 6789 anexo 1234'])expect(contactoSolicitanteValido(v)).withContext(v).toBeTrue();
  for(const v of ['','9','contacto@example.invalid','llamar','<script>','1'.repeat(19)])expect(contactoSolicitanteValido(v)).withContext(v).toBeFalse();
 });
 it('permite IP y AnyDesk vacíos y valida los literales proporcionados',()=>{
  for(const v of ['','192.168.1.10','0.0.0.0','255.255.255.255','2001:db8::1','::1','::','123 456 789','1234567890'])expect(equipoSolicitanteValido(v)).withContext(v).toBeTrue();
  for(const v of ['999.168.0.1','192.168.1','01.2.3.4','2001:::1',':1::','::1:','1:2:3','1:2:3:4:5:6:7:8:9','ejemplo.cl','https://192.168.1.1','usuario@ad','fe80::1%eth0'])expect(equipoSolicitanteValido(v)).withContext(v).toBeFalse();
 });
});
