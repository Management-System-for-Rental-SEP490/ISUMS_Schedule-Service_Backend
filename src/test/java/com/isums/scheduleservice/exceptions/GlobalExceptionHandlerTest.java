package com.isums.scheduleservice.exceptions;

import com.isums.scheduleservice.domains.dtos.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalExceptionHandler (schedule-service)")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("handleDb returns 500 with DB_ERROR code")
    void db() {
        DataAccessException ex = new DataAccessException("outer", new RuntimeException("root")) {};
        ResponseEntity<ApiResponse<Void>> res = handler.handleDb(ex);
        assertThat(res.getStatusCode().value()).isEqualTo(500);
        assertThat(res.getBody().getErrors().get(0).getCode()).isEqualTo("DB_ERROR");
        assertThat(res.getBody().getErrors().get(0).getMessage()).isEqualTo("root");
    }

    @Test
    @DisplayName("handleBadRequest (IllegalArgument) returns 400")
    void illegalArg() {
        ResponseEntity<ApiResponse<Void>> res =
                handler.handleBadRequest(new IllegalArgumentException("bad"));
        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(res.getBody().getErrors().get(0).getCode()).isEqualTo("BAD_REQUEST");
    }

    @Test
    @DisplayName("handleBadRequestException returns 400 (fix: was falling through to 500)")
    void customBadRequest() {
        ResponseEntity<ApiResponse<Void>> res =
                handler.handleBadRequestException(new BadRequestException("slot confirmed"));
        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(res.getBody().getErrors().get(0).getCode()).isEqualTo("BAD_REQUEST");
        assertThat(res.getBody().getMessage()).isEqualTo("slot confirmed");
    }

    @Test
    @DisplayName("handleGeneric returns 500 with INTERNAL_ERROR code")
    void generic() {
        ResponseEntity<ApiResponse<Void>> res = handler.handleGeneric(new Exception("boom"));
        assertThat(res.getStatusCode().value()).isEqualTo(500);
        assertThat(res.getBody().getErrors().get(0).getCode()).isEqualTo("INTERNAL_ERROR");
    }
}
