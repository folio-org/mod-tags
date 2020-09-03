package org.folio.rest.impl;

import static org.folio.rest.impl.TagsResourceImpl.extractFromSingleQuotes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import javax.ws.rs.core.Response;

import org.junit.Test;

import io.vertx.core.AsyncResult;

public class TagsResourceImplTest {
  @Test
  public void canExtractFromSingleQuotes() {
    assertThat(extractFromSingleQuotes(""), is(""));
    assertThat(extractFromSingleQuotes("'"), is("'"));
    assertThat(extractFromSingleQuotes("''"), is(""));
    assertThat(extractFromSingleQuotes("rock'n'roll"), is("n"));
    assertThat(extractFromSingleQuotes("The size is '2'5'"), is("2'5"));
  }

  @Test
  public void handleExceptionInHandleTagError() {
    Throwable throwable = new Throwable() {
      @Override
      public Throwable getCause() {
        throw new RuntimeException();
      }
    };
    AsyncResult<?> [] asyncResult = new AsyncResult[1];
    new TagsResourceImpl().handleTagError(throwable, result -> {
      asyncResult[0] = result;
      assertThat(result.result().getStatus(), is(500));
    });
    assertThat(asyncResult[0].succeeded(), is(true));
  }
}
