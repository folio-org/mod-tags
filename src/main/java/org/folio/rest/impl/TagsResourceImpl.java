package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import static io.vertx.core.Future.succeededFuture;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.commons.io.IOUtils;
import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Tag;
import org.folio.rest.jaxrs.model.TagsCollection;
import org.folio.rest.jaxrs.resource.TagsResource;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;
import org.z3950.zing.cql.cql2pgjson.SchemaException;

// TODO:
// - Find and rename all mentions of notify (once all code is copied in)
// - Enable CQL validation, latest RMB must support it



public class TagsResourceImpl implements TagsResource {
  private final Logger logger = LoggerFactory.getLogger("mod-tags");
  private final Messages messages = Messages.getInstance();
  private static final String NOTIFY_TABLE = "tags";
  private static final String LOCATION_PREFIX = "/tags/";
  private static final String IDFIELDNAME = "_id";
  private String notifySchema = null;
  private static final String NOTIFY_SCHEMA_NAME = "apidocs/raml/tags.json";

  private void initCQLValidation() {
    try {
      notifySchema = IOUtils.toString(getClass().getClassLoader().getResourceAsStream(NOTIFY_SCHEMA_NAME), "UTF-8");
    } catch (Exception e) {
      logger.error("unable to load schema - " + NOTIFY_SCHEMA_NAME + ", validation of query fields will not be active");
    }
  }

  public TagsResourceImpl(Vertx vertx, String tenantId) {
    if (notifySchema == null) { // Commented out, the validation fails a
      //initCQLValidation();   // prerfectly valid query=metaData.createdByUserId=e037b...
    }
    PostgresClient.getInstance(vertx, tenantId).setIdField(IDFIELDNAME);
  }

  private CQLWrapper getCQL(String query, int limit, int offset)
    throws FieldException, IOException, SchemaException {
    CQL2PgJSON cql2pgJson;
    if (notifySchema != null) {
      cql2pgJson = new CQL2PgJSON(NOTIFY_TABLE + ".jsonb", notifySchema);
    } else {
      cql2pgJson = new CQL2PgJSON(NOTIFY_TABLE + ".jsonb");
    }
    CQLWrapper wrap = new CQLWrapper(cql2pgJson, query);
    if (limit >= 0) {
      wrap.setLimit(new Limit(limit));
    }
    if (offset >= 0) {
      wrap.setOffset(new Offset(offset));
    }
    return wrap;
  }


  @Override
  @Validate
  public void getTags(String query,
    int offset, int limit, String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    CQLWrapper cql;
    String tenantId = TenantTool.calculateTenantId(
      okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
    try {
      logger.debug("Getting tags. "
        + offset + "+" + limit + " q=" + query);
      cql = getCQL(query, limit, offset);
    } catch (Exception e) {
      ValidationHelper.handleError(e, asyncResultHandler);
      return;
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(NOTIFY_TABLE, Tag.class, new String[]{"*"}, cql,
        true /*get count too*/, false /* set id */,
        reply -> {
          if (reply.succeeded()) {
            TagsCollection notes = new TagsCollection();
            @SuppressWarnings("unchecked")
            List<Tag> notifylist
              = (List<Tag>) reply.result().getResults();
            notes.setTags(notifylist);
            Integer totalRecords = reply.result().getResultInfo().getTotalRecords();
            notes.setTotalRecords(totalRecords);
            asyncResultHandler.handle(succeededFuture(
              GetTagsResponse.withJsonOK(notes)));
          } else {
            ValidationHelper.handleError(reply.cause(), asyncResultHandler);
          }
        });
  }

  @Override
  @Validate
  public void postTags(String lang,
      Tag entity,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context context) throws Exception {
    
      String tenantId = TenantTool.calculateTenantId(
        okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
      String id = entity.getId();
      PostgresClient.getInstance(context.owner(), tenantId).save(NOTIFY_TABLE,
        id, entity,
        reply -> {
          if (reply.succeeded()) {
            Object ret = reply.result();
            entity.setId((String) ret);
            OutStream stream = new OutStream();
            stream.setData(entity);
            asyncResultHandler.handle(succeededFuture(PostTagsResponse
                .withJsonCreated(LOCATION_PREFIX + ret, stream)));
          } else {
            ValidationHelper.handleError(reply.cause(), asyncResultHandler);
          }
        });
    }

  @Override
  public void getTagsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void deleteTagsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void putTagsById(String id, String lang, Tag entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

}
