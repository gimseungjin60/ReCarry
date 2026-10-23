package com.recarry.auth;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * TOTP (RFC 6238) — 30초 단계, 6자리, HMAC-SHA1. Google Authenticator 등 OTP 앱과 호환.
 * 라이브러리 없이 JDK 만 쓴다.
 */
public final class Totp {

	public static final long STEP_SECONDS = 30;
	private static final String B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
	private static final SecureRandom RANDOM = new SecureRandom();

	private Totp() {}

	/** 160비트 무작위 secret (Base32 32자) */
	public static String newSecret() {
		byte[] key = new byte[20];
		RANDOM.nextBytes(key);
		return base32(key);
	}

	public static long step(long epochSecond) {
		return epochSecond / STEP_SECONDS;
	}

	/** 그 시간 단계의 6자리 코드 */
	public static String code(String secret, long step) {
		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(unbase32(secret), "HmacSHA1"));
			byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
			int o = h[h.length - 1] & 0x0f;
			int bin = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
			return String.format("%06d", bin % 1_000_000);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * 앞뒤 한 단계(±30초) 시계 오차를 허용한다. 이미 쓴 단계(lastStep 이하)는 거절 — 같은 코드 재사용 방지.
	 * @return 맞은 단계, 틀리면 -1
	 */
	public static long verify(String secret, String code, long nowStep, Long lastStep) {
		if (code == null || !code.matches("[0-9]{6}")) return -1;
		for (long s = nowStep - 1; s <= nowStep + 1; s++) {
			if (lastStep != null && s <= lastStep) continue;
			if (MessageDigest.isEqual(code(secret, s).getBytes(), code.getBytes())) return s;
		}
		return -1;
	}

	/** OTP 앱에 등록하는 주소 (앱에서 "설정 키 입력"으로 secret 을 직접 넣어도 된다) */
	public static String uri(String account, String secret) {
		String label = java.net.URLEncoder.encode("RECARRY:" + account, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
		return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=RECARRY&digits=6&period=30";
	}

	private static String base32(byte[] data) {
		StringBuilder sb = new StringBuilder();
		int buffer = 0, bits = 0;
		for (byte b : data) {
			buffer = (buffer << 8) | (b & 0xff);
			bits += 8;
			while (bits >= 5) {
				sb.append(B32.charAt((buffer >> (bits - 5)) & 31));
				bits -= 5;
			}
		}
		if (bits > 0) sb.append(B32.charAt((buffer << (5 - bits)) & 31));
		return sb.toString();
	}

	private static byte[] unbase32(String s) {
		ByteBuffer out = ByteBuffer.allocate(s.length() * 5 / 8);
		int buffer = 0, bits = 0;
		for (char c : s.toUpperCase().toCharArray()) {
			int v = B32.indexOf(c);
			if (v < 0) continue;
			buffer = (buffer << 5) | v;
			bits += 5;
			if (bits >= 8) {
				out.put((byte) (buffer >> (bits - 8)));
				bits -= 8;
			}
		}
		return java.util.Arrays.copyOf(out.array(), out.position());
	}
}
