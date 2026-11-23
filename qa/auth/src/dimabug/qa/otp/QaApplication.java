package dimabug.qa.otp;

/** External QA artifact entry point. Never used by the production BackendApplication. */
public final class QaApplication {
    public static void main(String[] args){
        var app=new org.springframework.boot.SpringApplication(cl.casol.backend.BackendApplication.class,QaOtpConfiguration.class);
        app.setDefaultProperties(java.util.Map.of("spring.mvc.log-request-details","false","server.error.include-message","never",
            "server.error.include-binding-errors","never","server.error.include-stacktrace","never"));
        app.addListeners((org.springframework.context.ApplicationListener<org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent>)
            event->QaOtpConfiguration.validate(event.getEnvironment()));
        app.run(args);
    }
}
