package com.recarry.admin;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.recarry.user.User;
import com.recarry.user.UserRepository;

/** 관리자 변경 기록. 기록은 변경과 같은 트랜잭션에 들어간다 (변경이 롤백되면 기록도 남지 않는다). */
@Service
public class AuditService {

	public record AuditResponse(Instant at, String admin, String action, String target, String detail) {}

	private final AuditLogRepository logs;
	private final UserRepository users;

	AuditService(AuditLogRepository logs, UserRepository users) {
		this.logs = logs;
		this.users = users;
	}

	@Transactional
	public void record(Long adminId, String action, String target, String detail) {
		logs.save(new AuditLog(adminId, action, target, detail));
	}

	/** 최근 기록부터 limit 개 */
	@Transactional(readOnly = true)
	public List<AuditResponse> recent(int limit) {
		List<AuditLog> page = logs.findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt", "id")))
			.getContent();
		List<Long> adminIds = page.stream().map(AuditLog::getAdminId).filter(Objects::nonNull).distinct().toList();
		Map<Long, String> emails = users.findAllById(adminIds).stream()
			.collect(Collectors.toMap(User::getId, User::getEmail, (a, b) -> a, HashMap::new));
		return page.stream()
			.map(l -> new AuditResponse(l.getCreatedAt(), emails.getOrDefault(l.getAdminId(), "—"), l.getAction(),
				l.getTarget(), l.getDetail()))
			.toList();
	}
}
