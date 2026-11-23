package dimabug.qa;

/** Offline classloader smoke test. Does NOT invoke Spring or connect to MySQL. */
public class VerifyLoader {
    public static void main(String[] args) throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class.forName("com.mysql.cj.jdbc.Driver", false, loader);
        Class.forName("cl.casol.backend.BackendApplication", false, loader);
        if (loader.getResources("application.properties").hasMoreElements() ||
            loader.getResources("application-staging.properties").hasMoreElements() ||
            loader.getResources("application-gmail.properties").hasMoreElements()) {
            throw new IllegalStateException("QA_UNEXPECTED_CONFIG_RESOURCE");
        }
        if (!loader.getResources("application-qa-local.properties").hasMoreElements()) {
            throw new IllegalStateException("QA_PROFILE_MISSING");
        }
        new PinnedSocketFactory();
        System.out.println("QA_LOADER_ISOLATION_OK_NO_SPRING_NO_CONNECTION");
    }
}
