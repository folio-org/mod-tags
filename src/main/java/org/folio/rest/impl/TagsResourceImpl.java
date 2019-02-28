package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import static io.vertx.core.Future.succeededFuture;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.ws.rs.core.Response;
import org.apache.commons.io.IOUtils;
import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Tag;
import org.folio.rest.jaxrs.model.TagsCollection;
import org.folio.rest.jaxrs.resource.Tags;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;
import org.z3950.zing.cql.cql2pgjson.SchemaException;

public class TagsResourceImpl implements Tags {
  private final Logger logger = LoggerFactory.getLogger("mod-tags");
  private final Messages messages = Messages.getInstance();
  private static final String TAGS_TABLE = "tags";
  private static final String LOCATION_PREFIX = "/tags/";
  private static final String IDFIELDNAME = "_id";
  private String tagSchema = null;
  private static final String TAG_SCHEMA_NAME = "ramls/tag.json";

  private void initCQLValidation() {
    try {
      tagSchema = IOUtils.toString(getClass().getClassLoader()
        .getResourceAsStream(TAG_SCHEMA_NAME), "UTF-8");
    } catch (Exception e) {
      logger.error("unable to load schema - " + TAG_SCHEMA_NAME + ", validation of query fields will not be active",e);
    }
  }

  public TagsResourceImpl(Vertx vertx, String tenantId) {
    if (tagSchema == null) {
      initCQLValidation();
    }
    PostgresClient.getInstance(vertx, tenantId).setIdField(IDFIELDNAME);
  }

  private CQLWrapper getCQL(String query, int limit, int offset)
    throws FieldException, IOException, SchemaException {
    CQL2PgJSON cql2pgJson;
    if (tagSchema != null) {
      cql2pgJson = new CQL2PgJSON(TAGS_TABLE + ".jsonb", tagSchema);
    } else {
      cql2pgJson = new CQL2PgJSON(TAGS_TABLE + ".jsonb");
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
    Context vertxContext) {

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
      .get(TAGS_TABLE, Tag.class, new String[]{"*"}, cql,
        true /*get count too*/, false /* set id */,
        reply -> {
          if (reply.succeeded()) {
            TagsCollection notes = new TagsCollection();
            @SuppressWarnings("unchecked")
            List<Tag> taglist = reply.result().getResults();
            notes.setTags(taglist);
            Integer totalRecords = reply.result().getResultInfo().getTotalRecords();
            notes.setTotalRecords(totalRecords);
            asyncResultHandler.handle(succeededFuture(
              GetTagsResponse.respond200WithApplicationJson(notes)));
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
      Context context) {

      String tenantId = TenantTool.calculateTenantId(
        okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
    String id = entity.getId();
    if (id == null) {
      id = UUID.randomUUID().toString();
      entity.setId(id);
    }
      PostgresClient.getInstance(context.owner(), tenantId).save(TAGS_TABLE,
        id, entity,
        reply -> {
          if (reply.succeeded()) {
            String ret = reply.result();
            entity.setId(ret);
            asyncResultHandler.handle(succeededFuture(PostTagsResponse.
              respond201WithApplicationJson(entity, PostTagsResponse.headersFor201()
                .withLocation(LOCATION_PREFIX + ret))));
          } else {
            ValidationHelper.handleError(reply.cause(), asyncResultHandler);
          }
        });
    }

  @Override
  @Validate
  public void getTagsById(String id,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context context) {

    String tenantId = TenantTool.calculateTenantId(
      okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
    Criterion cql;
    try{
      cql = new Criterion(
        new Criteria().addField(IDFIELDNAME).setJSONB(false)
        .setOperation("=").setValue("'" + id + "'"));
    } catch (Exception e) {
      ValidationHelper.handleError(e, asyncResultHandler);
      return;
    }
      PostgresClient.getInstance(context.owner(), tenantId)
        .get(TAGS_TABLE, Tag.class, cql, true,
          reply -> {
            if (reply.succeeded()) {

              @SuppressWarnings("unchecked")
              List<Tag> tagslist
                = (List<Tag>) reply.result().getResults();
              if (tagslist.isEmpty()) {
                asyncResultHandler.handle(succeededFuture(GetTagsByIdResponse
                  .respond404WithTextPlain(id)));
              } else {
                asyncResultHandler.handle(succeededFuture(GetTagsByIdResponse
                  .respond200WithApplicationJson(tagslist.get(0))));
              }
            } else {
              ValidationHelper.handleError(reply.cause(), asyncResultHandler);
            }
          });

  }

  @Override
  @Validate
  public void deleteTagsById(String id,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = TenantTool.calculateTenantId(
      okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
      PostgresClient.getInstance(vertxContext.owner(), tenantId)
        .delete(TAGS_TABLE, id,
          reply -> {
            if (reply.succeeded()) {
              if (reply.result().getUpdated() == 1) {
                asyncResultHandler.handle(succeededFuture(
                    DeleteTagsByIdResponse.respond204()));
              } else {
                logger.error(messages.getMessage(lang,
                    MessageConsts.DeletedCountError, 1, reply.result().getUpdated()));
                asyncResultHandler.handle(succeededFuture(DeleteTagsByIdResponse
                  .respond404WithTextPlain(messages.getMessage(lang,
                        MessageConsts.DeletedCountError, 1, reply.result().getUpdated()))));
              }
            } else {
              ValidationHelper.handleError(reply.cause(), asyncResultHandler);
            }
          });

  }

  @Override
  @Validate
  public void putTagsById(String id,
          String lang, Tag entity, Map<String, String> okapiHeaders,
          Handler<AsyncResult<Response>> asyncResultHandler,
          Context vertxContext) {
    logger.info("PUT tag " + id + " " + Json.encode(entity));
    String noteId = entity.getId();
    if (noteId != null && !noteId.equals(id)) {
      logger.error("Trying to change tag Id from " + id + " to " + noteId);
      Errors valErr = ValidationHelper.createValidationErrorMessage("id", noteId,
        "Can not change the id");
      asyncResultHandler.handle(succeededFuture(PutTagsByIdResponse
        .respond422WithApplicationJson(valErr)));
      return;
    }
    String tenantId = TenantTool.calculateTenantId(
      okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
    
    // check id exists
    getTagsById(id, lang, okapiHeaders, new Handler<AsyncResult<Response>>() {
      @Override
      public void handle(AsyncResult<Response> ar) {
        if (ar.succeeded()) {
          int status = ar.result().getStatus();
          if (404 == status) {
            asyncResultHandler.handle(succeededFuture(PutTagsByIdResponse
              .respond404WithTextPlain(id)));
          } else if (200 == status) {
            PostgresClient.getInstance(vertxContext.owner(), tenantId)
            .update(TAGS_TABLE, entity, id, reply -> {
              if (reply.succeeded()) {
                if (reply.result().getUpdated() == 0) {
                  asyncResultHandler.handle(succeededFuture(PutTagsByIdResponse
                    .respond500WithTextPlain("internalErrorMsg(null, lang)")));
                } else { // all ok
                  asyncResultHandler.handle(succeededFuture(PutTagsByIdResponse
                    .respond204()));
                }
              } else {
                ValidationHelper.handleError(reply.cause(), asyncResultHandler);
              }
            });
          } else {
            asyncResultHandler.handle(succeededFuture(PutTagsByIdResponse
              .respond500WithTextPlain("Unexpected query by id response code: " + status)));
          }
        } else {
          ValidationHelper.handleError(ar.cause(), asyncResultHandler);
        }
      }
    }, vertxContext);
    
  }

}
