package org.folio.rest.impl;

import com.jayway.restassured.RestAssured;
import static com.jayway.restassured.RestAssured.given;
import com.jayway.restassured.response.Header;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.UUID;
import org.folio.rest.RestVerticle;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.PomReader;
import org.folio.rest.tools.client.test.HttpClientMock2;
import static org.hamcrest.Matchers.containsString;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for mod-tags.
 * Tags is a simple module, so the test are pretty simple too. We run against
 * the embedded postgres (oby default on port 9248, unlikely to collide with
 * a real postgress, or anything else), so we always start from a clean slate,
 * and there is no need to clean up afterwards.
 */
@RunWith(VertxUnitRunner.class)
public class TagsTest {

  private final Logger logger = LoggerFactory.getLogger("TagsTest");
  private final int port = Integer.parseInt(System.getProperty("port", "8081"));
  private final int pgPort = Integer.parseInt(System.getProperty("pgPort","9248"));
  private static final String LS = System.lineSeparator();
  private final Header TEN = new Header("X-Okapi-Tenant", "testlib");

  private final Header JSON = new Header("Content-Type", "application/json");
  private String moduleName; // "mod-tags";
  private String moduleVersion; // "0.2.0-SNAPSHOT";
  private String moduleId; // "mod-tags-0.2.0-SNAPSHOT"
  Vertx vertx;
  Async async;

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();
    moduleName = PomReader.INSTANCE.getModuleName()
      .replaceAll("_", "-");  // Rmb returns a 'normalized' name, with underscores
    moduleVersion = PomReader.INSTANCE.getVersion();
    moduleId = moduleName + "-" + moduleVersion;

    logger.info("Test setup starting for " + moduleId);

    try {
      PostgresClient.setIsEmbedded(true);
      PostgresClient.setEmbeddedPort(pgPort);
      PostgresClient.getInstance(vertx).startEmbeddedPostgres();
    } catch (Exception e) {
      context.fail(e);
      return;
    }

    JsonObject conf = new JsonObject()
      .put(HttpClientMock2.MOCK_MODE, "true")
      .put("http.port", port);

    logger.info("tagsTest: Deploying "
      + RestVerticle.class.getName() + " "
      + Json.encode(conf));
    DeploymentOptions opt = new DeploymentOptions()
      .setConfig(conf);
    vertx.deployVerticle(RestVerticle.class.getName(),
      opt, context.asyncAssertSuccess());
    RestAssured.port = port;
    logger.info("tagsTest: setup done. Using port " + port);
  }

  @After
  public void tearDown(TestContext context) {
    logger.info("Cleaning up after tagsTest");
    async = context.async();
    PostgresClient.stopEmbeddedPostgres();
    vertx.close(res -> {   // This logs a stack trace, ignore it.
      logger.info("tagsTest cleanup done");
      async.complete();
    });
  }



  @Test
  public void tests(TestContext context) throws  Exception {
    async = context.async();
    logger.info("tagsTest starting ==== ");  // search for ==== in mvn output

    UUID id1 = UUID.randomUUID();
    
    // Simple GET request to see the module is running and we can talk to it.
    given()
      .get("/admin/health")
      .then().log().ifValidationFails()
      .statusCode(200);

    // Call the tenant interface to initialize the database
    String tenants = "{\"module_to\":\"" + moduleId + "\"}";
    logger.info("About to call the tenant interface " + tenants);
    given()
      .header(TEN).header(JSON)
      .body(tenants)
      .post("/_/tenant")
      .then().log().ifValidationFails()
      .statusCode(201);
    logger.info("Tenant interface ok ====");

    logger.info("Empty list of tags");
    given()
      .header(TEN)
      .get("/tags")
      .then().log().ifValidationFails()
      .statusCode(200)
      .body(containsString("\"tags\" : [ ]"));

    logger.info("Post a tag");
    String tag1 = "{\"id\" : \"" + id1 + "\", "
            + "\"label\" : \"First tag\", "
            + "\"description\" : \"This is the first test tag\" }";
    logger.info("Post a tag");
    given()
      .header(TEN).header(JSON)
      .body(tag1)
      .post("/tags")
      .then().log().ifValidationFails()
      .statusCode(201)
      .body(containsString("first test"));

    logger.info("List of that one tag");
    given()
      .header(TEN)
      .get("/tags")
      .then().log().ifValidationFails()
      .statusCode(200)
      .body(containsString("the first test"))
      .body(containsString("\"totalRecords\" : 1"));

    logger.info("Get that one tag by id");
    given()
      .header(TEN)
      .get("/tags/" + id1)
      .then().log().ifValidationFails()
      .statusCode(200)
      .body(containsString("first test"));

    logger.info("Update the tag");
    String tag2 = "{\"id\" : \"" + id1 + "\", "
            + "\"label\" : \"First tag\", "
            + "\"description\" : \"This is the UPDATED test tag\" }";
    given()
      .header(TEN).header(JSON)
      .body(tag1)
      .put("/tags/" + id1)
      .then().log().ifValidationFails()
      .statusCode(204);

    logger.info("Delete that one tag by id");
    given()
      .header(TEN)
      .delete("/tags/" + id1)
      .then().log().ifValidationFails()
      .statusCode(204);

    logger.info("See that it is gone");
    given()
      .header(TEN)
      .get("/tags/" + id1)
      .then().log().ifValidationFails()
      .statusCode(404);

    // All done
    logger.info("tagsTest done ==== ");
    async.complete();

  }


}
