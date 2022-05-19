package org.folio.support.http;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;

import java.net.URI;
import java.util.Map;

import org.folio.support.Group;
import org.folio.support.Groups;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.LogConfig;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.mapper.ObjectMapperType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import lombok.NonNull;

public class GroupsClient {
  private final RequestSpecification requestSpecification;
  private final RestAssuredConfig config;

  public GroupsClient(URI baseUri, OkapiHeaders defaultHeaders) {
    requestSpecification = new RequestSpecBuilder()
      .setBaseUri(baseUri)
      .addHeader("X-Okapi-Tenant", defaultHeaders.getTenantId())
      .addHeader("X-Okapi-Token", defaultHeaders.getToken())
      .addHeader("X-Okapi-Url", defaultHeaders.getOkapiUrl())
      .setAccept("application/json, text/plain")
      .build();

    config = RestAssuredConfig.newConfig()
      .objectMapperConfig(new ObjectMapperConfig(ObjectMapperType.JACKSON_2))
      .logConfig(new LogConfig().enableLoggingOfRequestAndResponseIfValidationFails());
  }

  public Group createGroup(@NonNull Group group) {
    return attemptToCreateGroup(group)
      .statusCode(HTTP_CREATED)
      .extract().as(Group.class);
  }

  public ValidatableResponse attemptToCreateGroup(@NonNull Group group) {
    return given()
      .config(config)
      .spec(requestSpecification)
      .contentType(JSON)
      .when()
      .body(group)
      .post("/groups")
      .then();
  }

  public Groups getAllGroups() {
    return given()
      .config(config)
      .spec(requestSpecification)
      .when()
      .get("/groups")
      .then()
      .statusCode(HTTP_OK)
      .extract().as(Groups.class);
  }

  public void deleteGroup(String id) {
    attemptToDeleteGroup(id)
      .statusCode(HTTP_NO_CONTENT);
  }

  public ValidatableResponse attemptToDeleteGroup(String id) {
    return given()
      .config(config)
      .spec(requestSpecification)
      .when()
      .delete("/groups/{id}", Map.of("id", id))
      .then();
  }

  public void deleteAllGroups() {
    final var groups = getAllGroups();

    groups.getUsergroups().forEach(group -> deleteGroup(group.getId()));
  }

  public Group getGroup(String id) {
    return attemptToGetGroup(id)
      .statusCode(HTTP_OK)
      .extract().as(Group.class);
  }

  public ValidatableResponse attemptToGetGroup(String id) {
    return given()
      .config(config)
      .spec(requestSpecification)
      .when()
      .get("/groups/{id}", Map.of("id", id))
      .then();
  }

  public Groups findGroups(String cqlQuery) {
    return given()
      .config(config)
      .spec(requestSpecification)
      .when()
      .queryParam("query", cqlQuery)
      .get("/groups")
      .then()
      .statusCode(HTTP_OK)
      .extract().as(Groups.class);
  }

  public void updateGroup(@NonNull Group group) {
    given()
      .spec(requestSpecification)
      .contentType(JSON)
      .when()
      .body(group)
      .put("/groups/{id}", Map.of("id", group.getId()))
      .then()
      .statusCode(HTTP_NO_CONTENT);
  }
}