package com.recarry.common;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 연결 점검 모드 (dbcheck 프로필). migration 없이 읽기만 한다:
 * 접속 가능 여부 · 서버 버전 · SSL 사용 여부 · btree_gist 사용 가능 여부 · public 스키마의 테이블 · Flyway 이력.
 * 접속 정보(URL 의 호스트 외)나 비밀번호는 출력하지 않는다.
 */
@Component
@Profile("dbcheck")
public class DbCheckRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DbCheckRunner.class);

	private final DataSource dataSource;
	private final Environment env;

	public DbCheckRunner(DataSource dataSource, Environment env) {
		this.dataSource = dataSource;
		this.env = env;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		ProductionGuard.check(env, List.of(env.getProperty("spring.flyway.locations", "").split(",")));
		try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
			log.info("[dbcheck] connected: {} as {}", c.getMetaData().getDatabaseProductVersion(), one(st, "select current_user"));
			log.info("[dbcheck] database: {}", one(st, "select current_database()"));
			// 클라이언트→서버 TLS 는 DB_URL 의 sslmode=require 가 강제한다 (TLS 가 없으면 드라이버가 접속을 거부).
			// pg_stat_ssl 은 서버 쪽 마지막 구간만 보여준다 — Supabase pooler 경유면 pooler→DB 구간이라 false 일 수 있다
			log.info("[dbcheck] client sslmode: {}", env.getProperty("spring.datasource.url", "").replaceAll(".*[?&](sslmode=[a-z-]+).*", "$1"));
			log.info("[dbcheck] server-side ssl (last hop): {}", one(st, "select coalesce((select ssl::text from pg_stat_ssl where pid = pg_backend_pid()), 'unknown')"));
			log.info("[dbcheck] btree_gist available: {}", one(st, "select (count(*) > 0)::text from pg_available_extensions where name = 'btree_gist'"));
			log.info("[dbcheck] public tables: {}", list(st, "select table_name from information_schema.tables where table_schema = 'public' order by 1"));
			boolean history = one(st, "select to_regclass('public.flyway_schema_history')::text") != null;
			log.info("[dbcheck] flyway history: {}", history
				? list(st, "select coalesce(version, 'R') || ' ' || description || ' success=' || success from flyway_schema_history order by installed_rank")
				: "none (schema not migrated yet)");
		}
		log.info("[dbcheck] done - no changes were made");
	}

	private static String one(Statement st, String sql) throws Exception {
		try (ResultSet rs = st.executeQuery(sql)) {
			return rs.next() ? rs.getString(1) : null;
		}
	}

	private static List<String> list(Statement st, String sql) throws Exception {
		List<String> out = new ArrayList<>();
		try (ResultSet rs = st.executeQuery(sql)) {
			while (rs.next()) out.add(rs.getString(1));
		}
		return out;
	}
}
