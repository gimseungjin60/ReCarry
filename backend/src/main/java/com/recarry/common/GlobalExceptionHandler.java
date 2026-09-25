package com.recarry.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 예외를 ErrorResponse 로 바꾼다. 내부 메시지(SQL 등)는 밖으로 내보내지 않는다. */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ErrorResponse> api(ApiException e) {
		return ResponseEntity.status(e.status()).body(ErrorResponse.of(e.code(), e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> invalid(MethodArgumentNotValidException e) {
		Map<String, String> fields = new LinkedHashMap<>();
		e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
		return ResponseEntity.badRequest()
			.body(new ErrorResponse("VALIDATION_FAILED", "입력값을 확인해주세요.", fields));
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
			MissingServletRequestParameterException.class })
	ResponseEntity<ErrorResponse> unreadable(Exception e) {
		return ResponseEntity.badRequest().body(ErrorResponse.of("BAD_REQUEST", "요청 형식이 올바르지 않습니다."));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ErrorResponse> noRoute(NoResourceFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of("NOT_FOUND", "찾을 수 없습니다."));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ErrorResponse> method(HttpRequestMethodNotSupportedException e) {
		return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
			.body(ErrorResponse.of("METHOD_NOT_ALLOWED", "지원하지 않는 요청입니다."));
	}

	/** 동시 예약이 배타 제약(bookings_no_overlap)에 걸리거나 행 잠금이 교착되면 여기로 온다. */
	@ExceptionHandler({ DataIntegrityViolationException.class, PessimisticLockingFailureException.class })
	ResponseEntity<ErrorResponse> conflict(DataAccessException e) {
		String msg = String.valueOf(e.getMostSpecificCause().getMessage());
		if (msg.contains("bookings_no_overlap") || e instanceof PessimisticLockingFailureException) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(ErrorResponse.of("CARRIER_UNAVAILABLE", "선택한 기간에 예약할 수 있는 캐리어가 없습니다."));
		}
		// SQL 상세(행 값·이메일 등)는 로그에도 남기지 않고 제약 이름만 남긴다
		log.warn("data integrity violation: constraint={}", constraintName(e));
		return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of("CONFLICT", "요청을 처리할 수 없습니다."));
	}

	private static String constraintName(Throwable e) {
		for (Throwable t = e; t != null; t = t.getCause()) {
			if (t instanceof org.hibernate.exception.ConstraintViolationException c) return c.getConstraintName();
		}
		return "unknown";
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> unexpected(Exception e) {
		log.error("unhandled", e);
		return ResponseEntity.internalServerError().body(ErrorResponse.of("INTERNAL", "일시적인 오류가 발생했습니다."));
	}
}
