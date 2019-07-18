package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import static io.vertx.core.Future.succeededFuture;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.ws.rs.core.Response;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Tag;
import org.folio.rest.jaxrs.model.TagsCollection;
import org.folio.rest.jaxrs.resource.Tags;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;

public class TagsResourceImpl implements Tags {
  private final Logger logger = LoggerFactory.getLogger("mod-tags");
  private final Messages messages = Messages.getInstance();
  private static final String TAGS_TABLE = "tags";
  private static final String LOCATION_PREFIX = "/tags/";

  @Override
  @Validate
  public void getTags(String query,
    int offset, int limit, String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    try {
      logger.debug("Getting tags. "
        + offset + "+" + limit + " q=" + query);
      CQLWrapper cql = new CQLWrapper(new CQL2PgJSON(TAGS_TABLE + ".jsonb"), query, limit, offset);
      PgUtil.postgresClient(vertxContext, okapiHeaders)
      .get(TAGS_TABLE, Tag.class, new String[]{"*"}, cql,
          true /*get count too*/, false /* set id */,
          reply -> {
            if (reply.failed()) {
              ValidationHelper.handleError(reply.cause(), asyncResultHandler);
              return;
            }
            TagsCollection notes = new TagsCollection();
            List<Tag> taglist = reply.result().getResults();
            notes.setTags(taglist);
            Integer totalRecords = reply.result().getResultInfo().getTotalRecords();
            notes.setTotalRecords(totalRecords);
            asyncResultHandler.handle(succeededFuture(GetTagsResponse.respond200WithApplicationJson(notes)));
          });
    } catch (Exception e) {
      ValidationHelper.handleError(e, asyncResultHandler);
    }
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
            handleTagError(reply.cause(), asyncResultHandler);
          }
        });
    }

  @Override
  @Validate
  public void getTagsById(String id,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.postgresClient(vertxContext, okapiHeaders).getById(TAGS_TABLE, id, Tag.class, reply -> {
      if (reply.failed()) {
        ValidationHelper.handleError(reply.cause(), asyncResultHandler);
        return;
      }
      if (reply.result() == null) {
        asyncResultHandler.handle(succeededFuture(GetTagsByIdResponse.respond404WithTextPlain(id)));
        return;
      }
      asyncResultHandler.handle(succeededFuture(GetTagsByIdResponse.respond200WithApplicationJson(reply.result())));
    });
  }

  @Override
  @Validate
  public void deleteTagsById(String id,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.postgresClient(vertxContext, okapiHeaders).delete(TAGS_TABLE, id, reply -> {
      if (reply.failed()) {
        ValidationHelper.handleError(reply.cause(), asyncResultHandler);
        return;
      }
      if (reply.result().getUpdated() != 1) {
        String message = messages.getMessage(lang, MessageConsts.DeletedCountError, 1, reply.result().getUpdated());
        logger.error(message);
        asyncResultHandler.handle(succeededFuture(DeleteTagsByIdResponse
            .respond404WithTextPlain(message)));
        return;
      }
      asyncResultHandler.handle(succeededFuture(DeleteTagsByIdResponse.respond204()));
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
      // TODO: consider removing this check and instead use id to always overwrite jsonb->>'id', see
      // PgUtil.put for an example.
      logger.error("Trying to change tag Id from " + id + " to " + noteId);
      Errors valErr = ValidationHelper.createValidationErrorMessage("id", noteId,
        "Can not change the id");
      asyncResultHandler.handle(succeededFuture(PutTagsByIdResponse
        .respond422WithApplicationJson(valErr)));
      return;
    }

    PgUtil.postgresClient(vertxContext, okapiHeaders).update(TAGS_TABLE, entity, id, reply -> {
      if (reply.failed()) {
        handleTagError(reply.cause(), asyncResultHandler);
        return;
      }
      if (reply.result().getUpdated() == 0) {
        asyncResultHandler.handle(succeededFuture(PutTagsByIdResponse.respond404WithTextPlain(id)));
        return;
      }
      asyncResultHandler.handle(succeededFuture(PutTagsByIdResponse.respond204()));
    });
  }

  static String extractFromSingleQuotes(String s) {
    int start = s.indexOf('\'');
    int end = s.lastIndexOf('\'');
    if (start >= end) {
      return s;
    }
    return s.substring(start + 1, end);
  }

  void handleTagError(Throwable throwable, Handler<AsyncResult<Response>> asyncResultHandler) {
    try {
      ValidationHelper.handleError(throwable, reply -> {
        Response response = reply.result();
        if (response != null && response.getEntity() instanceof Errors) {
          Errors errors = (Errors) response.getEntity();
          Error error = errors.getErrors().get(0);
          if (error.getMessage().contains("duplicate key value violates unique constraint")) {
            String fieldname = error.getParameters().get(0).getKey();
            fieldname = extractFromSingleQuotes(fieldname);
            String value = error.getParameters().get(0).getValue();
            error.setMessage("Tag with " + fieldname + " '" + value + "' already exists");
          }
        }
        asyncResultHandler.handle(reply);
      });
    } catch (Exception e) {
      logger.error(throwable.getMessage(), throwable);
      logger.error(e.getMessage(), e);
      ValidationHelper.handleError(e, asyncResultHandler);
    }
  }
}
