package com.recarry.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	/** email 은 호출하는 쪽에서 소문자로 정규화해서 넘긴다. */
	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);
}
