package com.recarry.common;

import org.springframework.http.HttpStatus;

/** 사용자에게 그대로 보여줄 수 있는 업무 오류. code 는 프론트가 분기에 쓴다. */
public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String code;
	/** 입력 칸 하나에 대한 오류면 그 칸 이름 — 응답 fields 에 메시지를 함께 넣는다 */
	private String field;

	public ApiException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public HttpStatus status() {
		return status;
	}

	public String code() {
		return code;
	}

	public String field() {
		return field;
	}

	public ApiException field(String name) {
		this.field = name;
		return this;
	}

	public static ApiException notFound(String code, String message) {
		return new ApiException(HttpStatus.NOT_FOUND, code, message);
	}

	public static ApiException badRequest(String code, String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, code, message);
	}

	public static ApiException conflict(String code, String message) {
		return new ApiException(HttpStatus.CONFLICT, code, message);
	}
}
