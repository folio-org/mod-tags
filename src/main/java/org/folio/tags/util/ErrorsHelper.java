package org.folio.tags.util;

import java.util.List;

import lombok.experimental.UtilityClass;

import org.folio.tenant.domain.dto.Error;
import org.folio.tenant.domain.dto.Errors;
import org.folio.tenant.domain.dto.Parameter;

@UtilityClass
public class ErrorsHelper {

  public static Errors createValidationErrorMessage(String field, String message) {
    Parameter p = createParameter(field);
    Error error = createError(message, ErrorType.INTERNAL, ErrorCode.VALIDATION_ERROR);
    error.setParameters(List.of(p));

    return createErrors(error);
  }

  private static Parameter createParameter(String field) {
    Parameter p = new Parameter();
    p.setKey(field);
    return p;
  }

  public static Error createError(String message, ErrorType type, ErrorCode errorCode) {
    Error error = new Error();
    error.setMessage(message);
    error.setType(type.getTypeCode());
    error.setCode(errorCode == null ? null : errorCode.name());
    return error;
  }

  private static Errors createErrors(Error error) {
    Errors e = new Errors();
    e.setErrors(List.of(error));
    return e;
  }

  public static Errors createInternalError(String message) {
    return createInternalError(message, null);
  }

  public static Errors createUnknownError(String message) {
    return createErrors(createError(message, ErrorType.UNKNOWN, null));
  }
  public static Errors createInternalError(String message, ErrorCode errorCode) {
    return createErrors(createError(message, ErrorType.INTERNAL, errorCode));
  }

  public enum ErrorType {
    INTERNAL("-1"),
    FOLIO_EXTERNAL_OR_UNDEFINED("-2"),
    EXTERNAL_OR_UNDEFINED("-3"),
    UNKNOWN("-4");

    private final String typeCode;

    ErrorType(String typeCode) {
      this.typeCode = typeCode;
    }

    public String getTypeCode() {
      return typeCode;
    }
  }

  public enum ErrorCode {
    VALIDATION_ERROR,
    NOT_FOUND_ERROR
  }
}
