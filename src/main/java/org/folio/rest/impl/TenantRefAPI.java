package org.folio.rest.impl;

import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.tools.utils.TenantLoading;

import io.vertx.core.Context;
import io.vertx.core.Future;

public class TenantRefAPI extends TenantAPI {

  private static final Logger log = LogManager.getLogger(TenantRefAPI.class);

  private static final String SAMPLE_LEAD = "sample-data";
  private static final String SAMPLE_KEY = "loadSample";

  @Override
  Future<Integer> loadData(TenantAttributes attributes, String tenantId, Map<String, String> headers, Context vertxContext) {
    return super.loadData(attributes, tenantId, headers, vertxContext)
      .compose(recordsLoaded -> {
        log.info("Loading sample data from {}", SAMPLE_LEAD);

        return new TenantLoading()
          .withKey(SAMPLE_KEY).withLead(SAMPLE_LEAD)
          .withIdContent()
          .add("", "tags")
          .perform(attributes, headers, vertxContext, recordsLoaded);
      });
  }
}
