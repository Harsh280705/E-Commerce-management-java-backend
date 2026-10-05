package com.ecom.level3.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Maps to 422 to match Level 2 FastAPI validation behaviour. */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class UnprocessableException extends RuntimeException {
  public UnprocessableException(String message) { super(message); }
}
