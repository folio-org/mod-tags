package org.folio.tags.controller;

import static org.folio.tags.util.ErrorsHelper.ErrorCode.NOT_FOUND_ERROR;
import static org.folio.tags.util.ErrorsHelper.ErrorCode.VALIDATION_ERROR;
import static org.folio.tags.util.ErrorsHelper.createInternalError;
import static org.folio.tags.util.ErrorsHelper.createUnknownError;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.folio.spring.cql.CqlQueryValidationException;
import org.folio.tenant.domain.dto.Errors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Log4j2
@RestControllerAdvice
public class ErrorHandlingAdvice {

  @ResponseStatus(HttpStatus.NOT_FOUND)
  @ExceptionHandler(EntityNotFoundException.class)
  public Errors handleNotFoundException(EntityNotFoundException e) {
    return createInternalError(e.getMessage(), NOT_FOUND_ERROR);
  }

  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  @ExceptionHandler(ConstraintViolationException.class)
  public Errors handleConstraintViolationException(ConstraintViolationException e) {
    return createInternalError(e.getMessage(), VALIDATION_ERROR);
  }

  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  @ExceptionHandler(DataIntegrityViolationException.class)
  public Errors handleDataIntegrityException(DataIntegrityViolationException e) {
    var localizedMessage = e.getMostSpecificCause().getLocalizedMessage();
    var message = StringUtils.substringAfter(localizedMessage, "Detail:").trim();
    return createInternalError(message, VALIDATION_ERROR);
  }

  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  @ExceptionHandler({
    IllegalArgumentException.class,
    MissingServletRequestParameterException.class,
    CqlQueryValidationException.class,
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentNotValidException.class
  })
  public Errors handleMissingParameterException(Exception e) {
    return createInternalError(e.getLocalizedMessage(), VALIDATION_ERROR);
  }

  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  @ExceptionHandler(Exception.class)
  public Errors handleGlobalException(Exception e) {
    log.error("Unhandled exception occurred", e);
    return createUnknownError(e.getMessage());
  }
}
