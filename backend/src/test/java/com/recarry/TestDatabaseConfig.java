package com.recarry;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** 테스트마다 깨끗한 스키마에서 시작한다. 실수로 개발·운영 DB 를 비우지 않게 이름을 확인한다. */
@TestConfiguration
public class TestDatabaseConfig {

	@Bean
	FlywayMigrationStrategy cleanThenMigrate() {
		return flyway -> {
			// Spring 은 DataSource 로 Flyway 를 만들므로 URL 대신 실제 접속한 DB 이름을 본다
			String db;
			try (var conn = flyway.getConfiguration().getDataSource().getConnection()) {
				db = conn.getCatalog();
			} catch (java.sql.SQLException e) {
				throw new IllegalStateException(e);
			}
			if (db == null || !db.contains("test")) {
				throw new IllegalStateException("refusing to clean a non-test database: " + db);
			}
			flyway.clean();
			flyway.migrate();
		};
	}
}
