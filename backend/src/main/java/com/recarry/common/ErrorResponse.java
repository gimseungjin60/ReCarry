package com.recarry.common;

import java.util.Map;

/** 모든 API 오류의 공통 형식. fields 는 입력 검증 오류일 때만 채운다. */
public record ErrorResponse(String code, String message, Map<String, String> fields) {

	public static ErrorResponse of(String code, String message) {
		return new ErrorResponse(code, message, null);
	}

	/** Security 필터처럼 MVC 밖에서 응답을 쓸 때 쓴다 (메시지는 고정 문구만 넣는다). */
	public String toJson() {
		return "{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}";
	}
}
