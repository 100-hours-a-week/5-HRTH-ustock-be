package com.hrth.ustock.exception.common;

import com.hrth.ustock.exception.domain.user.UserException;
import io.sentry.Sentry;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.springframework.http.HttpStatus.*;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Value("${spring.config.domain}")
    private String domain;

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ExceptionResponse> handleCustomException(CustomException ex) {
        Sentry.captureException(ex);

        log.info(ex.getMessage());
        for (StackTraceElement element : ex.getStackTrace()) {
            if (element.getClassName().startsWith("com.hrth.ustock"))
                log.info(element.toString());
        }

        CustomExceptionType exceptionType = ex.getExceptionType();
        return ResponseEntity
                .status(exceptionType.status())
                .body(new ExceptionResponse(exceptionType.status().value(), ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionResponse> handleException(Exception ex) {
        Sentry.captureException(ex);
        log.warn(ex.getMessage(), ex);

        return ResponseEntity
                .status(INTERNAL_SERVER_ERROR)
                .body(new ExceptionResponse(INTERNAL_SERVER_ERROR.value(), "서버에 오류가 발생하였습니다."));
    }

    @ExceptionHandler(ServletException.class)
    public ResponseEntity<ExceptionResponse> handleServletException(
            ServletException ex, HttpServletRequest request, HttpServletResponse response) {

        Throwable realException = ex.getRootCause();
        if (realException instanceof UserException) {
            Cookie[] cookies = request.getCookies();

            Cookie refreshCookie = null;
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals("refresh")) {
                    refreshCookie = cookie;
                    refreshCookie.setMaxAge(0);
                    refreshCookie.setPath("/");
                    refreshCookie.setHttpOnly(true);
                    refreshCookie.setSecure(true);
                    refreshCookie.setDomain(domain);
                }
            }

            response.addCookie(refreshCookie);

            return ResponseEntity
                    .status(UNAUTHORIZED)
                    .body(new ExceptionResponse(UNAUTHORIZED.value(), realException.getMessage()));
        }

        return ResponseEntity
                .status(INTERNAL_SERVER_ERROR)
                .body(new ExceptionResponse(INTERNAL_SERVER_ERROR.value(), ex.getMessage()));
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        Sentry.captureException(ex);
        log.warn(ex.getMessage(), ex);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ExceptionResponse(BAD_REQUEST.value(), "잘못된 요청 파라미터입니다."));
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ExceptionResponse(HttpStatus.METHOD_NOT_ALLOWED.value(), "접근할 수 없는 페이지입니다."));
    }
}
