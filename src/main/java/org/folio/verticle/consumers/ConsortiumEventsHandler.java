package org.folio.verticle.consumers;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.producer.KafkaHeader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.event.service.UserTenantService;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.kafka.KafkaHeaderUtils;
import org.folio.kafka.exception.DuplicateEventException;
import org.folio.rest.jaxrs.model.UserTenant;
import org.folio.rest.utils.OkapiConnectionParams;

import java.util.List;

public class ConsortiumEventsHandler implements AsyncRecordHandler<String, String> {

  private static final Logger logger = LogManager.getLogger(ConsortiumEventsHandler.class);

  private final Vertx vertx;
  private final UserTenantService userTenantService;

  public ConsortiumEventsHandler(Vertx vertx) {
    this.vertx = vertx;
    this.userTenantService = new UserTenantService();
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> record) {
    Promise<String> result = Promise.promise();
    List<KafkaHeader> kafkaHeaders = record.headers();
    OkapiConnectionParams okapiConnectionParams = new OkapiConnectionParams(KafkaHeaderUtils.kafkaHeadersToMap(kafkaHeaders), vertx);
    UserTenant event = new JsonObject(record.value()).mapTo(UserTenant.class);
    logger.info("Trying to save of user primary affiliation event with consortium id: {} for user id: {} with tenant id: {}",
      event.getId(), event.getUserId(), event.getTenantId());

    userTenantService.saveUserTenant(event, okapiConnectionParams.getTenantId(), okapiConnectionParams.getVertx())
      .onSuccess(ar -> {
        logger.info("User primary affiliation event with consortium id: {} has been saved for user id: {} with tenant id: {}",
          event.getId(), event.getUserId(), event.getTenantId());
        result.complete(event.getId());
      })
      .onFailure(e -> {
        if (e instanceof DuplicateEventException) {
          logger.info("Duplicate user primary affiliation event with consortium id: {} for user id: {} with tenant id: {} received, skipped processing",
            event.getId(), event.getUserId(), event.getTenantId());
          result.complete(event.getId());
        } else {
          logger.error("Trying to save of user primary affiliation event with consortium id: {} for user id: {} with tenant id: {} has been failed",
            event.getId(), event.getUserId(), event.getTenantId(), e);
          result.fail(e);
        }
      });

    return result.future();
  }
}
