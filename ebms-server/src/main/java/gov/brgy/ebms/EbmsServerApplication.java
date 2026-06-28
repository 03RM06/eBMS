package gov.brgy.ebms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class EbmsServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EbmsServerApplication.class, args);
    }
}
