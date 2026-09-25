package com.recarry.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Flyway 이력 테이블(flyway_schema_history)을 Supabase Data API 에서 막는다.
 * V2 migration 이 우리 테이블을 막지만, 이력 테이블은 migration 도중 Flyway 가 잠그고 있어
 * migration 안에서는 바꿀 수 없다. 그래서 기동이 끝난 뒤(= migration 뒤) 여기서 처리한다. 여러 번 실행해도 같다.
 * anon 역할이 없는 DB(로컬)에서는 아무것도 하지 않는다.
 */
@Component
@Profile("prod & !dbcheck")   // 연결 점검 모드는 읽기만 한다
public class SupabaseLockdown implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(SupabaseLockdown.class);

	private final JdbcTemplate jdbc;

	public SupabaseLockdown(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void run(ApplicationArguments args) {
		jdbc.execute("""
			DO $$
			DECLARE r text;
			BEGIN
			  IF to_regclass('public.flyway_schema_history') IS NULL THEN RETURN; END IF;
			  EXECUTE 'ALTER TABLE public.flyway_schema_history ENABLE ROW LEVEL SECURITY';
			  FOREACH r IN ARRAY ARRAY['anon', 'authenticated'] LOOP
			    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) THEN
			      EXECUTE format('REVOKE ALL ON public.flyway_schema_history FROM %I', r);
			    END IF;
			  END LOOP;
			END $$""");
		log.info("flyway_schema_history locked down for Supabase Data API roles");
	}
}
