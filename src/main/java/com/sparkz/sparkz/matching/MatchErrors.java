package com.sparkz.sparkz.matching;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = MatchController.class)
public class MatchErrors {
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail conflict() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Request conflicts with existing data");
    }
}
