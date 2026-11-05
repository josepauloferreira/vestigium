package io.github.josepauloferreira.vestigium.infrastructure.web.shared;

import io.github.josepauloferreira.vestigium.application.initialstock.DuplicateInitialStockException;
import io.github.josepauloferreira.vestigium.application.initialstock.InitialStockNotFoundException;
import io.github.josepauloferreira.vestigium.application.initialstock.InvalidInitialStockDataException;
import io.github.josepauloferreira.vestigium.application.item.DuplicateItemIdException;
import io.github.josepauloferreira.vestigium.application.item.ItemNotFoundException;
import io.github.josepauloferreira.vestigium.application.stock.StockChronologyConflictException;
import io.github.josepauloferreira.vestigium.application.stockentry.DuplicateStockEntryIdException;
import io.github.josepauloferreira.vestigium.application.stockentry.InvalidStockEntryDataException;
import java.util.Comparator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(InitialStockNotFoundException.class)
  ProblemDetail missingInitialStock(InitialStockNotFoundException exception) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
  }

  @ExceptionHandler({DuplicateInitialStockException.class, StockChronologyConflictException.class})
  ProblemDetail initialStockConflict(RuntimeException exception) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
  }

  @ExceptionHandler(InvalidInitialStockDataException.class)
  ProblemDetail invalidInitialStock(InvalidInitialStockDataException exception) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
  }

  @ExceptionHandler(DuplicateItemIdException.class)
  ProblemDetail duplicateItem(DuplicateItemIdException exception) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT, "An item with this ID already exists");
  }

  @ExceptionHandler(ItemNotFoundException.class)
  ProblemDetail missingItem(ItemNotFoundException exception) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Item was not found");
  }

  @ExceptionHandler(DataAccessException.class)
  ProblemDetail persistenceFailure(DataAccessException exception) {
    log.error("Request failed while accessing persistence", exception);
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.INTERNAL_SERVER_ERROR, "Unable to complete the request");
  }

  @ExceptionHandler(DuplicateStockEntryIdException.class)
  ProblemDetail duplicateStockEntry(DuplicateStockEntryIdException exception) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT, "A stock entry with this ID already exists");
  }

  @ExceptionHandler(InvalidStockEntryDataException.class)
  ProblemDetail invalidStockEntryData(InvalidStockEntryDataException exception) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Request fields are invalid");
    problem.setProperty(
        "errors",
        exception.getBindingResult().getFieldErrors().stream()
            .map(error -> new InvalidField(error.getField(), error.getDefaultMessage()))
            .sorted(Comparator.comparing(InvalidField::field).thenComparing(InvalidField::message))
            .toList());
    return handleExceptionInternal(exception, problem, headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            status, "Request body must contain valid JSON with the expected field types");
    return handleExceptionInternal(exception, problem, headers, status, request);
  }

  private record InvalidField(String field, String message) {}
}
