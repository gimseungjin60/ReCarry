package com.recarry.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * prod 프로필로 뜰 때 위험한 설정이면 migration 전에 기동을 멈춘다.
 * Flyway 설정 단계에서 호출되므로 DB 에 아무것도 쓰기 전에 실패한다. (연결 점검 모드는 DbCheckRunner 가 직접 부른다)
 * 오류 메시지에 비밀 값은 넣지 않는다.
 */
@Component
public class ProductionGuard implements FlywayConfigurationCustomizer {

	private static final Logger log = LoggerFactory.getLogger(ProductionGuard.class);

	private final Environment env;

	public ProductionGuard(Environment env) {
		this.env = env;
	}

	@Override
	public void customize(FluentConfiguration configuration) {
		check(env, Arrays.stream(configuration.getLocations()).map(Object::toString).toList());
	}

	public static void check(Environment env, List<String> flywayLocations) {
		List<String> profiles = Arrays.asList(env.getActiveProfiles());
		if (!profiles.contains("prod")) return;

		List<String> problems = new ArrayList<>();
		if (profiles.contains("local")) {
			problems.add("'local' and 'prod' profiles are both active");
		}
		String url = env.getProperty("spring.datasource.url", "");
		if (url.contains("localhost") || url.contains("127.0.0.1") || url.contains("0.0.0.0")) {
			problems.add("DB_URL points to a local database");
		}
		if (!url.matches(".*[?&]sslmode=(require|verify-ca|verify-full)(&.*)?$")) {
			problems.add("DB_URL must use sslmode=require (or verify-ca / verify-full)");
		}
		if (flywayLocations.stream().anyMatch(l -> l.contains("db/sample"))) {
			problems.add("sample catalog location is enabled for Flyway");
		}
		if (!env.getProperty("recarry.cookie.secure", Boolean.class, true)) {
			problems.add("session cookie must be Secure in production");
		}
		if (!problems.isEmpty()) {
			throw new IllegalStateException("Refusing to start with profile 'prod': " + String.join("; ", problems));
		}
		// 막지는 않는다 (로컬에서 운영 DB 를 점검할 때는 비어 있어도 된다). 배포에서 비어 있으면 로그인·예약 POST 가 403 이 된다
		if (env.getProperty("recarry.cors.allowed-origins", "").isBlank()) {
			log.warn("CORS_ALLOWED_ORIGINS is empty: requests proxied from the frontend domain (e.g. Vercel rewrite) will be rejected. Set it to the frontend origin.");
		}
	}
}
