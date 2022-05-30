package org.folio.rest.impl;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;

class ProxiesForAPITest {
  @Test
  void userAndProxyUserComboExistsCanHandlePostgresClientFailure() {
    var postgresClient = mock(PostgresClient.class);

    when(postgresClient.get(anyString(), any(), any(Criterion.class), anyBoolean()))
      .thenReturn(Future.failedFuture(new RuntimeException("my exception")));

    var future = new ProxiesForAPI()
      .userAndProxyUserComboExists("someUserId", "someProxyUserId",
        postgresClient);

    assertThat(future.cause().getMessage(), is("my exception"));
  }
}
