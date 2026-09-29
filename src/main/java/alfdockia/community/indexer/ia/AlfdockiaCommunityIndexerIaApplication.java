package alfdockia.community.indexer.ia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AlfdockiaCommunityIndexerIaApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlfdockiaCommunityIndexerIaApplication.class, args);
    }
}
