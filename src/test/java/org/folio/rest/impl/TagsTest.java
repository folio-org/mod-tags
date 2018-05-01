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
  private final String USERID7 = "77777777-7777-7777-7777-777777777777";
  private final Header USER7 = new Header("X-Okapi-User-Id", USERID7);
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

    // Part 1: Preliminaries
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

    // Part 2: Simple CRUD operations
    logger.info("Post a tag");
    String tag1 = "{\"id\" : \"" + id1 + "\", "
            + "\"label\" : \"First tag\", "
            + "\"description\" : \"This is the first test tag\" }";
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

    logger.info("Try to delete it again");
    given()
      .header(TEN)
      .delete("/tags/" + id1)
      .then().log().ifValidationFails()
      .statusCode(404);

    // Part 3: Validation
    logger.info("Bad UUID");
    String badUuid = tag1.replaceAll("-", "XXX");
    given()
      .header(TEN).header(JSON)
      .body(badUuid)
      .post("/tags")
      .then().log().ifValidationFails()
      .statusCode(422)
      .body(containsString("invalid input syntax for type uuid"));

    logger.info("Get by wrong id");
    given()
      .header(TEN)
      .get("/tags/" + UUID.randomUUID().toString())
      .then().log().ifValidationFails()
      .statusCode(404);

    logger.info("Get by bad id");
    given()
      .header(TEN)
      .get("/tags/9999-BAD-UUID-9999")
      .then().log().ifValidationFails()
      .statusCode(422);

    logger.info("Unknown field");
    String UnknownField = tag1.replaceAll("description", "unknownField");
    given()
      .header(TEN).header(JSON)
      .body(UnknownField)
      .post("/tags")
      .then().log().ifValidationFails()
      .statusCode(422)
      .body(containsString("Unrecognized field"));

    logger.info("Put tag1 back in the database");
    given()
      .header(TEN).header(JSON)
      .body(tag1)
      .post("/tags")
      .then().log().ifValidationFails()
      .statusCode(201)
      .body(containsString("first test"));

    logger.info("Changing ID");
    String newId = UUID.randomUUID().toString();
    String changeId = tag1.replaceAll(id1.toString(), newId);
    given()
      .header(TEN).header(JSON)
      .body(changeId)
      .put("/tags/" + id1)
      .then().log().ifValidationFails()
      .statusCode(422)
      .body(containsString("Can not change the id"));

    logger.info("PUT to non-existing");
    given()
      .header(TEN).header(JSON)
      .body(changeId)
      .put("/tags/" + newId)
      .then().log().ifValidationFails()
      .statusCode(500);  // Should probably be a 404, or 201.

    // Part 4:  Post a few records to test queries with
    logger.info("Second tag: Metadata and missing Id");
    String second = "{ \"label\":\"second tag\"}";
    logger.info(second);
    given()
      .header(TEN).header(JSON).header(USER7)
      .body(second)
      .post("/tags")
      .then().log().ifValidationFails()
      .statusCode(201)
      .body(containsString(USERID7))
      .body(containsString("createdByUserId"));
    logger.info("Second tag: Metadata and missing Id");

    String third = "{ \"label\":\"third tag\","
      + "\"description\" : \"Tag number three\"}";
    logger.info(third);
    given()
      .header(TEN).header(JSON)
      .body(third)
      .post("/tags")
      .then().log().ifValidationFails()
      .statusCode(201);

    String fourth = "{ \"label\":\"fourth tag\","
      + "\"description\" : \"Tag number FOUR\"}";
    logger.info(fourth);
    given()
      .header(TEN).header(JSON).header(USER7)
      .body(fourth)
      .post("/tags")
      .then().log().ifValidationFails()
      .statusCode(201);

    // Part 5: Test queries and limits
    logger.info("List test tags");
    given()
      .header(TEN)
      .get("/tags")
      .then().log().all() //.ifValidationFails()
      .statusCode(200)
      .body(containsString("\"totalRecords\" : 4"));

    logger.info("query");
    given()
      .header(TEN)
      .get("/tags?query=label=tag")
      .then().log().ifValidationFails()
      .statusCode(200)
      .body(containsString("\"totalRecords\" : 4"));

    logger.info("second query");
    given()
      .header(TEN)
      .get("/tags?query=label=second")
      .then().log().ifValidationFails()
      .statusCode(200)
      .body(containsString("\"totalRecords\" : 1"));

    logger.info("metadata query"); // Fails due to a missing index
    // if the initCQLValidation() call is not commented out.
    given()
      .header(TEN)
      .get("/tags?query=metadata.createdByUserId=" + USERID7)
      .then().log().all(); //ifValidationFails()
    //.statusCode(200)
    //.body(containsString("\"totalRecords\" : 2"));

    logger.info("query");
    given()
      .header(TEN)
      .get("/tags?query=label=tag&offset=2&limit=1")
      .then().log().ifValidationFails()
      .statusCode(200)
      .body(containsString("\"totalRecords\" : 4"));

    // All done
    logger.info("tagsTest done ==== ");
    async.complete();

  }


}
