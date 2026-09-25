package com.recarry.auth;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.recarry.common.AppProperties;
import com.recarry.common.ErrorResponse;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Stateless JWT (HS256). 토큰은 httpOnly 쿠키로 오간다 (SessionCookies).
 * CSRF: Spring 의 토큰 방식 대신 SameSite 쿠키 + 커스텀 헤더 검사(ClientHeaderFilter)로 막는다.
 * 토큰은 TokenService 가 발급하고, 검증은 Spring 의 resource server 가 한다.
 */
@Configuration
public class SecurityConfig {

	static final String ISSUER = "recarry";

	@Bean
	@ConditionalOnWebApplication   // 연결 점검 모드(웹 서버 없음)에서는 만들지 않는다
	SecurityFilterChain filterChain(HttpSecurity http, SessionCookies cookies) throws Exception {
		http
			.csrf(AbstractHttpConfigurer::disable)
			.cors(c -> {})
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(a -> a
				.requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login", "/api/auth/logout").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/auth/session", "/api/health").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/carriers", "/api/carriers/**").permitAll()
				.requestMatchers("/api/admin/**").hasRole("ADMIN")
				.requestMatchers("/api/**").authenticated()
				.requestMatchers("/error").permitAll()
				.anyRequest().denyAll())
			.addFilterBefore(new ClientHeaderFilter(), org.springframework.security.web.csrf.CsrfFilter.class)
			.oauth2ResourceServer(o -> o
				.bearerTokenResolver(new CookieTokenResolver(cookies))
				.jwt(j -> j.jwtAuthenticationConverter(authenticationConverter()))
				.authenticationEntryPoint((req, res, e) -> write(res, 401, "UNAUTHORIZED", "로그인이 필요합니다.")))
			.exceptionHandling(e -> e
				.authenticationEntryPoint((req, res, ex) -> write(res, 401, "UNAUTHORIZED", "로그인이 필요합니다."))
				.accessDeniedHandler((req, res, ex) -> write(res, 403, "FORBIDDEN", "접근 권한이 없습니다.")));
		return http.build();
	}

	private static void write(HttpServletResponse res, int status, String code, String message) throws java.io.IOException {
		res.setStatus(status);
		res.setContentType(MediaType.APPLICATION_JSON_VALUE);
		res.setCharacterEncoding(StandardCharsets.UTF_8.name());
		res.getWriter().write(ErrorResponse.of(code, message).toJson());
	}

	/** claim "roles": ["ADMIN"] → ROLE_ADMIN */
	private static JwtAuthenticationConverter authenticationConverter() {
		JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
		roles.setAuthoritiesClaimName("roles");
		roles.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(roles);
		return converter;
	}

	@Bean
	SecretKey jwtKey(AppProperties props) {
		String secret = props.jwt().secret();
		// HS256 은 32바이트 이상 키가 필요하다. 없거나 짧으면 기동하지 않는다
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalStateException("JWT_SECRET must be set and at least 32 bytes long");
		}
		return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtKey) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
		return decoder;
	}

	/** {bcrypt} 접두사가 붙는 위임형 인코더 — 나중에 알고리즘을 바꿔도 기존 해시가 동작한다. */
	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	/** 운영 프론트 origin 만 허용. 로컬은 Vite proxy 라 같은 origin 이다. */
	@Bean
	@ConditionalOnWebApplication
	CorsConfigurationSource corsConfigurationSource(AppProperties props) {
		CorsConfiguration cors = new CorsConfiguration();
		List<String> origins = props.cors().allowedOrigins() == null ? List.of()
			: props.cors().allowedOrigins().stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
		// 쿠키(credentials)를 쓰므로 와일드카드는 허용하지 않는다 — 정확한 origin 만
		if (origins.stream().anyMatch(o -> o.contains("*"))) {
			throw new IllegalStateException("CORS_ALLOWED_ORIGINS must list exact origins (no '*') because cookies are used");
		}
		cors.setAllowedOrigins(origins);
		cors.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
		cors.setAllowedHeaders(List.of("Content-Type", ClientHeaderFilter.HEADER));
		// 쿠키를 주고받으려면 credentials 가 필요하다 (이때 origin 은 * 가 될 수 없다 — 목록만 허용)
		cors.setAllowCredentials(true);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", cors);
		return source;
	}
}
