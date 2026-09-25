package com.recarry.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.recarry.common.AppProperties;
import com.recarry.user.User;

/** access token 발급. 서버에 저장하지 않으므로 로그아웃은 클라이언트가 토큰을 버리는 것이다. */
@Service
public class TokenService {

	private final JwtEncoder encoder;
	private final Clock clock;
	private final long ttlMinutes;

	public TokenService(JwtEncoder encoder, Clock clock, AppProperties props) {
		this.encoder = encoder;
		this.clock = clock;
		this.ttlMinutes = props.jwt().ttlMinutes();
	}

	public Issued issue(User user) {
		Instant now = clock.instant();
		Instant expiresAt = now.plus(ttlMinutes, ChronoUnit.MINUTES);
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(SecurityConfig.ISSUER)
			.subject(user.getId().toString())
			.issuedAt(now)
			.expiresAt(expiresAt)
			.claim("roles", List.of(user.getRole().name()))
			.build();
		String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
			.getTokenValue();
		return new Issued(token, expiresAt);
	}

	public record Issued(String token, Instant expiresAt) {}
}
