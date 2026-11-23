package dimabug.qa.otp;

/** Explicit future fixture preparation only: writes unusable-password hashes to a private file, never stdout. */
public class PrepareFixtureHashes {
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("QA_HASH_ARGUMENTS");
        var target=java.nio.file.Path.of(args[0]).toAbsolutePath().normalize();
        if(!target.toString().replace('\\','/').contains("/qa/runtime/fixture-runs/") || java.nio.file.Files.isSymbolicLink(target.getParent()))throw new IllegalArgumentException("QA_HASH_PRIVATE_PATH");
        var encoder=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();var random=new java.security.SecureRandom();
        var entries=new java.util.ArrayList<String>();
        for(String email:QaOtpAdapter.RECIPIENTS){
            byte[] seed=new byte[32];random.nextBytes(seed);
            String hash=encoder.encode(java.util.Base64.getEncoder().encodeToString(seed));java.util.Arrays.fill(seed,(byte)0);
            entries.add("\""+email.split("@")[0]+"\":\""+hash+"\"");
        }
        java.nio.file.Files.writeString(target,"{"+String.join(",",entries)+"}",java.nio.file.StandardOpenOption.CREATE_NEW);
        System.out.println("QA_FIXTURE_HASHES_PREPARED_NO_SQL");
    }
}
