package com.recarry;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.recarry.common.ProductionGuard;

/** prod 프로필에서 위험한 설정으로는 뜨지 않는다 (migration 전에 멈춘다). */
class ProductionGuardTest {

	static final String SUPABASE = "jdbc:postgresql://aws-0-ap-northeast-2.pooler.supabase.com:5432/postgres?sslmode=require";
	static final List<String> SCHEMA_ONLY = List.of("classpath:db/migration");

	MockEnvironment env(String url, String... profiles) {
		MockEnvironment e = new MockEnvironment().withProperty("spring.datasource.url", url);
		e.setActiveProfiles(profiles);
		return e;
	}

	@Test
	void valid_production_config_passes() {
		assertThatCode(() -> ProductionGuard.check(env(SUPABASE, "prod"), SCHEMA_ONLY)).doesNotThrowAnyException();
	}

	@Test
	void local_profile_together_with_prod_is_refused() {
		assertThatThrownBy(() -> ProductionGuard.check(env(SUPABASE, "prod", "local"), SCHEMA_ONLY))
			.hasMessageContaining("'local' and 'prod'");
	}

	@Test
	void local_database_is_refused() {
		assertThatThrownBy(() -> ProductionGuard.check(env("jdbc:postgresql://localhost:5433/recarry?sslmode=require", "prod"), SCHEMA_ONLY))
			.hasMessageContaining("local database");
	}

	@Test
	void missing_ssl_is_refused() {
		assertThatThrownBy(() -> ProductionGuard.check(env(SUPABASE.replace("?sslmode=require", ""), "prod"), SCHEMA_ONLY))
			.hasMessageContaining("sslmode");
	}

	@Test
	void sample_catalog_is_refused() {
		assertThatThrownBy(() -> ProductionGuard.check(env(SUPABASE, "prod"), List.of("classpath:db/migration", "classpath:db/sample")))
			.hasMessageContaining("sample catalog");
	}

	@Test
	void insecure_cookie_is_refused() {
		MockEnvironment e = env(SUPABASE, "prod").withProperty("recarry.cookie.secure", "false");
		assertThatThrownBy(() -> ProductionGuard.check(e, SCHEMA_ONLY)).hasMessageContaining("Secure");
	}

	@Test
	void non_prod_profiles_are_not_checked() {
		assertThatCode(() -> ProductionGuard.check(env("jdbc:postgresql://localhost:5433/recarry", "local"),
			List.of("classpath:db/migration", "classpath:db/sample"))).doesNotThrowAnyException();
	}
}
