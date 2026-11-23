package dimabug.qa.otp;

import cl.casol.backend.identidad.application.port.out.EnvioCodigoAccesoPort;
import cl.casol.backend.identidad.domain.CodigoAcceso;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;

/** QA delivery only. No HTTP, SMTP, Gmail, database writes, logger, or public code getter. */
public final class QaOtpAdapter implements EnvioCodigoAccesoPort {
    public static final Set<String> RECIPIENTS=Set.of("admin@qa.dimabug.test","tecnico@qa.dimabug.test",
        "soporte@qa.dimabug.test","usuario@qa.dimabug.test","usuario-otro@qa.dimabug.test",
        "tecnico-otro@qa.dimabug.test","admin-respaldo@qa.dimabug.test","usuario-inactivo@qa.dimabug.test");
    private final Path directory;private final byte[] key;private final String run;private final Clock clock;
    private final SecureRandom random=new SecureRandom();
    public QaOtpAdapter(Path directory,byte[] key,String run,Clock clock){
        if(key.length!=32 || !run.matches("[a-f0-9]{32}") || !Files.isDirectory(directory) || Files.isSymbolicLink(directory))throw fail();
        this.directory=directory.toAbsolutePath().normalize();this.key=key.clone();this.run=run;this.clock=clock;
    }
    private static IllegalStateException fail(){return new IllegalStateException("QA_OTP_CONTROL_FAILED");}
    @Override public void enviar(String e,String n,String c,int m){throw fail();}
    @Override public void enviarRecuperacion(String e,String n,String c,int m){throw fail();}
    @Override public void encolar(CodigoAcceso record,String email,String name,String code,int minutes){
        if(record.id()==null || record.id()<=0 || !RECIPIENTS.contains(email) || !code.matches("[0-9]{6}") ||
            !TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.isSynchronizationActive())throw fail();
        // Strings remain transient in managed memory; they are never logged or sent to a network.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            private byte[] pending=(record.id()+"\n"+email+"\n"+record.expiraEn().toInstant(ZoneOffset.UTC).toEpochMilli()+"\n"+code).getBytes(StandardCharsets.UTF_8);
            @Override public void afterCommit(){
                try{
                    if(!record.expiraEn().toInstant(ZoneOffset.UTC).isAfter(clock.instant()))return;
                    publish(record.id(),pending);
                }catch(Exception e){
                    // The DB commit already succeeded: never claim rollback or fall back to email.
                    try{Files.writeString(directory.resolve(record.id()+".failed"),"QA_DELIVERY_FAILED",StandardOpenOption.CREATE_NEW);}catch(Exception ignored){}
                }
            }
            @Override public void afterCompletion(int status){Arrays.fill(pending,(byte)0);pending=null;}
        });
    }
    private void publish(long id,byte[] plain)throws Exception{
        String recipient=new String(plain,StandardCharsets.UTF_8).split("\n")[1];
        // Remove expired/replaced deliveries after commit; never retain an unbounded inbox.
        try(var files=Files.newDirectoryStream(directory,"*.otp")){
            int count=0;
            for(Path old:files){
                if(++count>160 || Files.isSymbolicLink(old) || Files.size(old)>4096)throw fail();
                String name=old.getFileName().toString();if(!name.matches("[1-9][0-9]*\\.otp"))throw fail();
                byte[] raw=Files.readAllBytes(old);if(raw.length<35 || !new String(raw,0,6,StandardCharsets.US_ASCII).equals("DQOTP1"))throw fail();
                String oldId=name.substring(0,name.length()-4);
                Cipher decoder=Cipher.getInstance("AES/GCM/NoPadding");decoder.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,Arrays.copyOfRange(raw,6,18)));
                decoder.updateAAD(("dimabug-qa-otp-v1|"+run+"|"+oldId).getBytes(StandardCharsets.UTF_8));
                byte[] payload=decoder.doFinal(Arrays.copyOfRange(raw,18,raw.length));
                try{
                    String[] fields=new String(payload,StandardCharsets.UTF_8).split("\n");
                    if(fields.length!=4 || !fields[0].equals(oldId))throw fail();
                    if(fields[1].equals(recipient) || Long.parseLong(fields[2])<=clock.instant().toEpochMilli())Files.delete(old);
                }finally{Arrays.fill(payload,(byte)0);}
            }
        }
        byte[] nonce=new byte[12];random.nextBytes(nonce);
        Cipher aes=Cipher.getInstance("AES/GCM/NoPadding");aes.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
        aes.updateAAD(("dimabug-qa-otp-v1|"+run+"|"+id).getBytes(StandardCharsets.UTF_8));
        byte[] encrypted=aes.doFinal(plain);
        byte[] file=ByteBuffer.allocate(6+12+encrypted.length).put("DQOTP1".getBytes(StandardCharsets.US_ASCII)).put(nonce).put(encrypted).array();
        Path temp=directory.resolve("pending-"+UUID.randomUUID()+".tmp");
        try{
            Files.write(temp,file,StandardOpenOption.CREATE_NEW);
            Files.move(temp,directory.resolve(id+".otp"),StandardCopyOption.ATOMIC_MOVE);
        }finally{Files.deleteIfExists(temp);Arrays.fill(file,(byte)0);Arrays.fill(encrypted,(byte)0);}
    }
}
