package org.folio.tags;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class ModTagsApplication {

  public static void main(String[] args) {
    SpringApplication.run(ModTagsApplication.class, args);
  }
}
