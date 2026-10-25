package com.recarry.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.SecretKey;

/** 서버 키(JWT 서명 키)로 만든 HMAC-SHA256 서명 — 인증 링크 · 가입 인증번호 · 카카오 가입 대기 쿠키의 위조를 막는다 */
final class Hmac {

	private Hmac() {}

	static String sign(SecretKey key, String data) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(key);
			return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	/** 시간 차로 서명이 드러나지 않게 비교한다 */
	static boolean matches(SecretKey key, String data, String signature) {
		return signature != null && MessageDigest.isEqual(sign(key, data).getBytes(StandardCharsets.US_ASCII),
			signature.getBytes(StandardCharsets.US_ASCII));
	}
}
