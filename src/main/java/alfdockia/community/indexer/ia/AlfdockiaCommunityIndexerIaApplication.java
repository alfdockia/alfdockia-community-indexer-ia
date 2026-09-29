/*
 * Copyright (c) 2026 AIgen Technologies S.L.
 *
 * La propiedad intelectual de este modulo pertenece a AIgen Technologies S.L.
 * Consulte el archivo LICENSE en la raiz del repositorio para conocer los
 * terminos de licencia aplicables.
 */

package alfdockia.community.indexer.ia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Arranca la aplicacion Spring Boot y habilita las tareas programadas.
 */
@SpringBootApplication
@EnableScheduling
public class AlfdockiaCommunityIndexerIaApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlfdockiaCommunityIndexerIaApplication.class, args);
    }
}
