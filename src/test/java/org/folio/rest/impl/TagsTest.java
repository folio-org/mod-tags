package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.Header;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.folio.okapi.common.XOkapiHeaders;
import org.folio.postgres.testing.PostgresTesterContainer;
import org.folio.rest.RestVerticle;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.persist.PostgresClient;

/**
 * Unit tests for mod-tags.
 * Tags is a simple module, so the test are pretty simple too. We run against
 * the test containers postgres, so we always start from a clean slate,
 * and there is no need to clean up afterwards.
 */
@RunWith(VertxUnitRunner.class)
public class TagsTest {

  private final Logger logger = LogManager.getLogger("TagsTest");
  private final int port = Integer.parseInt(System.getProperty("port", "8081"));
  private final String TENANT = "testlib";
  private final String TOKEN = "token";
  private final int TENANT_OP_WAITINGTIME = 60000;
  private final Header TEN = new Header("X-Okapi-Tenant", TENANT);
  private final String USERID7 = "77777777-7777-7777-7777-777777777777";
  private final Header USER7 = new Header("X-Okapi-User-Id", USERID7);
  private final String OKAPI_URL = "http://localhost:" + port;
  private String moduleId;
  private Vertx vertx;
  private Async async;
  private RequestSpecification spec;

  // sample tags from sample-data directory
  private static final Map<String, String> TAGS = new HashMap<>();

  static {
    TAGS.put("c3799dc5-500b-44dd-8e17-2f2354cc43e3", "urgent");
    TAGS.put("d3c8b511-41e7-422e-a483-18778d0596e5", "important");
  }

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();
    moduleId = "mod-tags-0.1.0";

    try {
      PostgresClient.setPostgresTester(new PostgresTesterContainer());
      PostgresClient.getInstance(vertx).startPostgresTester();
    } catch (Exception e) {
      context.fail(e);
    }

    RestAssured.port = port;

    spec = new RequestSpecBuilder()
      .setBaseUri("http://localhost:" + port)
      .setPort(port)
      .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
      .addHeader(RestVerticle.OKAPI_HEADER_TENANT, TENANT)
      .addHeader(RestVerticle.OKAPI_HEADER_TOKEN, TOKEN)
      .addHeader(XOkapiHeaders.URL, OKAPI_URL)
      .log(LogDetail.ALL)
      .build();

    deployVerticleWithTenant();
  }

  public void deployVerticleWithTenant() {
    JsonObject conf = new JsonObject()
      .put("http.port", port);

    logger.info("tagsTest: Deploying "
      + RestVerticle.class.getName() + " "
      + Json.encode(conf));

    DeploymentOptions options = new DeploymentOptions()
      .setConfig(conf);

    CompletableFuture<Void> future = new CompletableFuture<>();
    vertx.deployVerticle(RestVerticle.class.getName(), options, event -> {
      TenantClient tenantClient = new TenantClient(OKAPI_URL, TENANT, TOKEN, vertx.createHttpClient());
      try {
        List<Parameter> params = List.of(
          new Parameter().withKey("loadReference").withValue("true"),
          new Parameter().withKey("loadSample").withValue("true")
        );
        TenantAttributes ta = new TenantAttributes()
          .withModuleTo(moduleId)
          .withParameters(params);

        tenantClient.postTenant(ta, res1 -> {
          if (res1.succeeded()) {
            String jobId = res1.result().bodyAsJson(TenantJob.class).getId();
            tenantClient.getTenantByOperationId(jobId, TENANT_OP_WAITINGTIME, res2 -> {
              if (res2.succeeded()) {
                future.complete(null);
              } else {
                future.completeExceptionally(new IllegalStateException("Failed to get tenant"));
              }
            });
          } else {
            future.completeExceptionally(new IllegalStateException("Failed to create tenant job"));
          }
        });
      } catch (Exception e) {
        future.completeExceptionally(e);
      }
    });
    future.join();
  }

  @After
  public void tearDown(TestContext context) {
    logger.info("Cleaning up after tagsTest");
    async = context.async();
    PostgresClient.stopPostgresTester();
    vertx.close(res -> {   // This logs a stack trace, ignore it.
      logger.info("tagsTest cleanup done");
      async.complete();
    });
  }

  @Test
  public void tests(TestContext context) {
    async = context.async();
    logger.info("tagsTest starting ==== ");  // search for ==== in mvn output

    // Part 1: Preliminaries
    UUID id1 = UUID.randomUUID();

    // Simple GET request to see the module is running and we can talk to it.
    given()
      .spec(spec)
      .get("/admin/health")
      .then().log().ifValidationFails()
      .statusCode(200);

    // Check sample data size
    logger.info("List of sample tags");
    given()
      .spec(spec)
      .get("/tags")
      .then().log().ifValidationFails()
      .statusCode(200)
      .body(containsString("\"totalRecords\" : " + TAGS.size()));

    // Check sample data
    TAGS.forEach((key, value) ->
      given()
        .spec(spec)
        .get("/tags/" + key)
        .then().log().ifValidationFails()
        .statusCode(200)
        .body(containsString(value))
    );

    // Remove sample data
    TAGS.keySet().forEach(key ->
      given().spec(spec).delete("/tags/" + key)
        .then().log().ifValidationFails()
        .statusCode(204)
    );

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
      .spec(spec)
      .body(tag1)
      .post("/tags")
      .then().log().ifValidationFails()
      .statusCode(201)
      .body(containsString("first test"));

    logger.info("List of that one tag");
    given()
      .spec(spec)
      .get("/tags")
      .then().log().ifValidationFails()
      .statusCode(200)
      .body(containsString("the first test"))
      .body(containsString("\"totalRecords\" : 1"));

    logger.info("Get that one tag by id");
    given()
      .spec(spec)
      .get("/tags/" + id1)
      .then().log().ifValidationFails()
      .statusCode(200)
      .body(containsString("first test"));

    logger.info("Update the tag");
    String tag2 = "{\"id\" : \"" + id1 + "\", "
      + "\"label\" : \"First tag\", "
      + "\"description\" : \"This is the UPDATED test tag\" }";
    given()
      .spec(spec)
      .body(tag2)
      .put("/tags/" + id1)
      .then().log().ifValidationFails()
      .statusCode(204);

    logger.info("Delete that one tag by id");
    given()
      .spec(spec)
      .delete("/tags/" + id1)
      .then().log().ifValidationFails()
      .statusCode(204);

    logger.info("See that it is gone");
    given()
      .spec(spec)
      .get("/tags/" + id1)
      .then().log().ifValidationFails()
      .statusCode(404);

    logger.info("Try to delete it again");
    given()
      .spec(spec)
      .delete("/tags/" + id1)
      .then().log().ifValidationFails()
      .statusCode(404);

    // Part 3: Validation
    logger.info("Bad UUID");
    String badUuid = tag1.replaceAll("-", "XXX");
    given()
      .spec(spec)
      .body(badUuid)
      .post("/tags")
      .then().log().ifValidationFails()
      .statusCode(422)
      .body(containsString("must match"));

    logger.info("Get by wrong id");
    given()
      .spec(spec)
      .get("/tags/" + UUID.randomUUID())
      .then().log().ifValidationFails()
      .statusCode(404);

    logger.info("Get by bad id");
    given()
      .spec(spec)
      .get("/tags/9999-BAD-UUID-9999")
      .then().log().ifValidationFails()
      .statusCode(400);

    logger.info("Unknown field");
    String UnknownField = tag1.replaceAll("description", "unknownField");
    given()
      .spec(spec)
      .body(UnknownField)
      .post("/tags")
      .then().log().ifValidationFails()
      .statusCode(422)
      .body(containsString("Unrecognized field"));

    logger.info("Put tag1 back in the database");
    given()
      .spec(spec)
      .body(tag1)
      .post("/tags")
      .then().log().ifValidationFails()
      .statusCode(201)
      .body(containsString("first test"));

    logger.info("Changing ID");
    String newId = UUID.randomUUID().toString();
    String changeId = tag1.replaceAll(id1.toString(), newId);
    given()
      .spec(spec)
      .body(changeId)
      .put("/tags/" + id1)
      .then().log().ifValidationFails()
      .statusCode(422)
      .body(containsString("Can not change the id"));

    logger.info("PUT to non-existing");
    given()
      .spec(spec)
      .body(changeId)
      .put("/tags/" + newId)
      .then().log().ifValidationFails()
      .statusCode(404);  // Should probably be a 404, or 201.

    logger.info("Post tag with duplicated label");
    given()
      .spec(spec)
      .body("{\"label\" : \"first tag\", \"description\" : \"I'm the duplicate!\" }")
      .post("/tags")
      .then().log().ifValidationFails()
      .statusCode(422)
      .body(containsString("Tag with label 'first tag' already exists"));

    // Part 4:  Post a few records to test queries with
    logger.info("Second tag: Metadata and missing Id");
    String second = "{ \"label\":\"second tag\"}";
    logger.info(second);
    given()
      .spec(spec).header(USER7)
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
      .spec(spec)
      .body(third)
      .post("/tags")
      .then().log().ifValidationFails()
      .statusCode(201);

    String fourth = "{ \"label\":\"fourth tag\","
      + "\"description\" : \"Tag number FOUR\"}";
    logger.info(fourth);
    given()
      .spec(spec).header(USER7)
      .body(fourth)
      .post("/tags")
      .then().log().ifValidationFails()
      .statusCode(201);

    // Part 5: Test queries and limits
    logger.info("List test tags");
    given()
      .spec(spec)
      .get("/tags")
      .then().log().ifValidationFails()
      .statusCode(200)
      .body(containsString("\"totalRecords\" : 4"));

    logger.info("query");
    given()
      .spec(spec)
      .get("/tags?query=label=tag")
      .then().log().ifValidationFails()
      .statusCode(200)
      .body(containsString("\"totalRecords\" : 4"));

    logger.info("second query");
    given()
      .spec(spec)
      .get("/tags?query=label=second")
      .then().log().ifValidationFails()
      .statusCode(200)
      .body(containsString("\"totalRecords\" : 1"));

    logger.info("metadata query");
    given()
      .spec(spec)
      .get("/tags?query=metadata.createdByUserId=" + USERID7)
      .then().log().ifValidationFails()
      .statusCode(200)
      .body(containsString("\"totalRecords\" : 2"));

    logger.info("query");
    given()
      .spec(spec)
      .get("/tags?query=label=tag&offset=2&limit=1")
      .then().log().ifValidationFails()
      .statusCode(200)
      .body(containsString("\"totalRecords\" : 4"));

    given()
      .spec(spec)
      .get("/tags?query=label=tag&offset=2&limit=101")
      .then().log().ifValidationFails()
      .statusCode(200)
      .body(containsString("\"totalRecords\" : 4"));

    given()
      .spec(spec)
      .get("/tags?query=label=tag&offset=2&limit=-1")
      .then().log().ifValidationFails()
      .statusCode(400);

    logger.info("Substring search, empty string, ==");
    // Matches all that have a description not defined
    given()
      .spec(spec)
      .get("/tags?query=cql.allRecords=1 NOT description=\"\"")
      .then().log().ifValidationFails()
      .statusCode(200)
      .body(containsString("\"totalRecords\" : 1"))
      .body(containsString("second tag"));

    // All done
    logger.info("tagsTest done ==== ");
    async.complete();

  }

}
