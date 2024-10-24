package org.folio.tags.support;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableAutoConfiguration
@ContextConfiguration
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext
public abstract class ApiTest {

  protected static final String TENANT = "test";
  protected static final String TOKEN = "test-jwt-token";
  protected static final String USER_ID = "77777777-7777-7777-7777-777777777777";

  @Container
  static PostgreSQLContainer<?> postgreDBContainer = Objects.toString(System.getenv("TESTCONTAINERS_POSTGRES_IMAGE"), "postgres:16-alpine"))

  static {
    postgreDBContainer.start();
    OBJECT_MAPPER = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
  }

  private static final ObjectMapper OBJECT_MAPPER;

  @Autowired
  protected MockMvc mockMvc;
  @Autowired
  protected DatabaseHelper databaseHelper;

  @BeforeAll
  static void beforeAll(@Autowired MockMvc mockMvc) {
    setUpTenant(mockMvc);
  }

  @AfterAll
  static void afterAll() {
    postgreDBContainer.stop();
  }

  @SneakyThrows
  protected static void setUpTenant(MockMvc mockMvc) {
    mockMvc.perform(post("/_/tenant").content(asJsonString(new TenantAttributes().moduleTo("mod-tags")))
        .headers(defaultHeaders())
        .contentType(APPLICATION_JSON))
      .andExpect(status().isNoContent());
  }

  @SneakyThrows
  public static String asJsonString(Object value) {
    return OBJECT_MAPPER.writeValueAsString(value);
  }

  public static HttpHeaders defaultHeaders() {
    final HttpHeaders httpHeaders = new HttpHeaders();

    httpHeaders.setContentType(APPLICATION_JSON);
    httpHeaders.add(XOkapiHeaders.TENANT, TENANT);
    httpHeaders.add(XOkapiHeaders.TOKEN, TOKEN);
    httpHeaders.add(XOkapiHeaders.USER_ID, USER_ID);

    return httpHeaders;
  }

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgreDBContainer::getJdbcUrl);
    registry.add("spring.datasource.username", postgreDBContainer::getUsername);
    registry.add("spring.datasource.password", postgreDBContainer::getPassword);
  }

  @TestConfiguration
  static class TestConfig {

    @Bean
    public DatabaseHelper databaseHelper(JdbcTemplate jdbcTemplate, FolioModuleMetadata moduleMetadata) {
      return new DatabaseHelper(moduleMetadata, jdbcTemplate);
    }
  }
}
