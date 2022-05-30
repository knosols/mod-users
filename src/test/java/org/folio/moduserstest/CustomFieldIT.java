package org.folio.moduserstest;

import static io.vertx.core.json.Json.encode;
import static org.folio.moduserstest.RestITSupport.deleteWithNoContentStatus;
import static org.folio.moduserstest.RestITSupport.getJson;
import static org.folio.moduserstest.RestITSupport.postWithOkStatus;
import static org.folio.moduserstest.RestITSupport.putWithNoContentStatus;
import static org.folio.util.StringUtil.urlEncode;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.postgres.testing.PostgresTesterContainer;
import org.folio.rest.RestVerticle;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.rest.utils.TenantInit;
import org.folio.support.User;
import org.folio.support.ValidationErrors;
import org.folio.support.http.FakeTokenGenerator;
import org.folio.support.http.OkapiHeaders;
import org.folio.support.http.OkapiUrl;
import org.folio.support.http.UsersClient;
import org.folio.test.util.TokenTestUtil;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import io.restassured.http.Header;
import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import lombok.SneakyThrows;

@RunWith(VertxUnitRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CustomFieldIT {

  private static final Logger log = LogManager.getLogger(CustomFieldIT.class);
  private static final String joeBlockId = "ba6baf95-bf14-4020-b44c-0cad269fb5c9";
  private static final String johnRectangleId = "ae6d1c57-3041-4645-9215-3ca0094b77fc";
  private static final String notExistingCustomField = "notExistingCustomField";

  private static final Header FAKE_TOKEN = TokenTestUtil.createTokenHeader("joeBlock", joeBlockId);

  private static final String customFieldsPath = "/custom-fields";

  private static final String customFieldId = "524d3210-9ca2-4f91-87b4-d2227d595aaa";

  private static final String postCustomField = "{\"id\": \"524d3210-9ca2-4f91-87b4-d2227d595aaa\", " +
    "\"name\": \"Department\", " +
    "\"visible\": true, " +
    "\"required\": true, " +
    "\"helpText\": \"Provide a department\", " +
    "\"entityType\": \"user\", " +
    "\"type\": \"TEXTBOX_SHORT\", " +
    "\"order\": 1 }";
  private static final String putCustomField = "{\"id\": \"524d3210-9ca2-4f91-87b4-d2227d595aaa\", " +
    "\"name\": \"Department updated\", " +
    "\"visible\": false, " +
    "\"required\": true, " +
    "\"helpText\": \"Provide a department\", " +
    "\"entityType\": \"user\", " +
    "\"type\": \"TEXTBOX_SHORT\", " +
    "\"order\": 1 }";


  private static Vertx vertx;
  private static UsersClient usersClient;

  @Rule
  public Timeout rule = Timeout.seconds(20);

  @BeforeClass
  @SneakyThrows
  public static void setup(TestContext context) {
    final var tenant = "diku";
    final var token = new FakeTokenGenerator().generateToken();

    vertx = Vertx.vertx();

    PostgresClient.setPostgresTester(new PostgresTesterContainer());

    final var port = NetworkUtils.nextFreePort();

    final var okapiUrl = new OkapiUrl("http://localhost:" + port);
    final var headers = new OkapiHeaders(okapiUrl, tenant, token);

    usersClient = new UsersClient(okapiUrl, headers);

    RestITSupport.setUp(port);

    TenantClient tenantClient = new TenantClient("http://localhost:" + port, tenant, token);

    DeploymentOptions options = new DeploymentOptions()
        .setConfig(new JsonObject().put("http.port", port));

    vertx.deployVerticle(RestVerticle.class.getName(), options, context.asyncAssertSuccess(res -> {
      TenantAttributes ta = new TenantAttributes();
      ta.setModuleTo("mod-users-1.0.0");
      List<Parameter> parameters = new LinkedList<>();
      parameters.add(new Parameter().withKey("loadReference").withValue("true"));
      parameters.add(new Parameter().withKey("loadSample").withValue("false"));
      ta.setParameters(parameters);
      TenantInit.init(tenantClient, ta).onComplete(context.asyncAssertSuccess());
    }));
  }

  @Test
  public void test1Sequential(TestContext context) {
    Async async = context.async();

    postUser()
      .compose(v -> postCustomField())
      .compose(v -> postUserWithInvalidCustomFieldValueLength())
      .compose(v -> postUserWithCustomFields())
      .compose(v -> getUserWithCustomFields(context))
      .compose(v -> deleteUser(johnRectangleId))
      .compose(v -> putUserWithNotExistingCustomField())
      .compose(v -> postUserWithNotExistingCustomField())
      .compose(v -> deleteUser(joeBlockId))
      .compose(v -> deleteCustomField(context))
      .onComplete(testResultHandler(context, async));
  }

  @Test
  public void test4CustomFields(TestContext context) {
    Async async = context.async();
    postUser()
      .compose(v -> postCustomField())
      .compose(v -> putCustomField(context))
      .compose(v -> queryCustomField(context))
      .compose(this::assertCustomFieldValues)
      .compose(v -> deleteUser(joeBlockId))
      .compose(v -> deleteCustomField(context))
      .onComplete(testResultHandler(context, async));
  }

  private Handler<AsyncResult<Void>> testResultHandler(TestContext context, Async async) {
    return res -> {
      if (res.succeeded()) {
        async.complete();
      } else {
        res.cause().printStackTrace();
        context.fail(res.cause());
      }
    };
  }

  private Future<Void> postUser() {
    final var userToCreate = User.builder()
      .id(joeBlockId)
      .username("joeBlock")
      .active(true)
      .build();

    usersClient.createUser(userToCreate);

    return Future.succeededFuture();
  }

  private Future<Void> postUserWithCustomFields() {
    final var userToCreate = User.builder()
      .id(johnRectangleId)
      .username("johnRectangle")
      .active(true)
      .customFields(Map.of("department", "Math"))
      .build();

    usersClient.createUser(userToCreate);

    return Future.succeededFuture();
  }

  private Future<Void> postUserWithInvalidCustomFieldValueLength() {
    final var userToCreate = User.builder()
      .id(johnRectangleId)
      .username("johnRectangle")
      .active(true)
      .customFields(Map.of("department", RandomStringUtils.randomAlphanumeric(151)))
      .build();

    final var errors = usersClient.attemptToCreateUser(userToCreate)
      .statusCode(is(422))
      .extract().as(ValidationErrors.class);

    assertThat(errors.getErrors().get(0).getMessage(),
      is("Maximum length of the value is 150"));

    return Future.succeededFuture();
  }

  private Future<Void> getUserWithCustomFields(TestContext context) {
    log.info("Retrieving a user with custom fields\n");

    Future<JsonObject> future = getJson(context, "/users/" + johnRectangleId);

    return future.map(user -> {
      JsonObject customFields = user.getJsonObject("customFields");

      if (customFields == null || !customFields.encode().equals("{\"department\":\"Math\"}")) {
        fail("Bad value for customFields. " + encode(customFields));
      }

      return null;
    });
  }

  private Future<Void> putUserWithNotExistingCustomField() {
    final var userToCreate = User.builder()
      .id(johnRectangleId)
      .username("johnRectangle")
      .active(true)
      .customFields(Map.of(notExistingCustomField, "abc"))
      .build();

    final var errors = usersClient.attemptToUpdateUser(userToCreate)
      .statusCode(is(422))
      .extract().as(ValidationErrors.class);

    assertThat(errors.getErrors().size(), is(1));
    final var firstError = errors.getErrors().get(0);

    assertThat(firstError.getMessage(),
      is("Custom field with refId notExistingCustomField is not found"));
    assertThat(firstError.getParameters().get(0).getKey(), is("customFields"));
    assertThat(firstError.getParameters().get(0).getValue(), is(notExistingCustomField));

    return Future.succeededFuture();
  }

  private Future<Void> postUserWithNotExistingCustomField() {
    final var userToCreate = User.builder()
      .id(johnRectangleId)
      .username("johnRectangle")
      .active(true)
      .customFields(Map.of(notExistingCustomField, "abc"))
      .build();

    final var errors = usersClient.attemptToCreateUser(userToCreate)
      .statusCode(is(422))
      .extract().as(ValidationErrors.class);

    assertThat(errors.getErrors().size(), is(1));
    final var firstError = errors.getErrors().get(0);

    assertThat(firstError.getMessage(),
      is("Custom field with refId notExistingCustomField is not found"));

    assertThat(firstError.getParameters().get(0).getKey(), is("customFields"));
    assertThat(firstError.getParameters().get(0).getValue(), is(notExistingCustomField));

    return Future.succeededFuture();
  }

  private Future<Void> assertCustomFieldValues(JsonObject result) {
    Promise<Void> promise = Promise.promise();
    int totalRecords = result.getInteger("totalRecords");
    if (totalRecords != 1) {
      promise.fail("Expected 1 record, got " + totalRecords);
    }
    JsonArray customFields = result.getJsonArray("customFields");
    JsonObject customField = customFields.getJsonObject(0);
    assertThat(customField.getString("entityType"), is("user"));

    promise.complete();
    return promise.future();
  }

  private Future<Void> deleteCustomField(TestContext context) {
    log.info("Deleting existing custom field\n");
    return deleteWithNoContentStatus(context, customFieldsPath + "/" + customFieldId);
  }

  private Future<JsonObject> queryCustomField(TestContext context) {
    String requestUrl = customFieldsPath + "?query=" + urlEncode("entityType==user");
    log.info("Getting custom field via CQL, by entityType\n");

    return getJson(context, requestUrl);
  }

  private Future<Void> postCustomField() {
    log.info("Creating a new custom field definition\n");
    return postWithOkStatus(joeBlockId, customFieldsPath, postCustomField, FAKE_TOKEN);
  }

  private Future<Void> putCustomField(TestContext context) {
    log.info("Update custom field definition\n");
    return putWithNoContentStatus(context, joeBlockId, customFieldsPath + "/" + customFieldId, putCustomField, FAKE_TOKEN);
  }

  private Future<Void> deleteUser(String userId) {
    usersClient.deleteUser(userId);

    return Future.succeededFuture();
  }
}
