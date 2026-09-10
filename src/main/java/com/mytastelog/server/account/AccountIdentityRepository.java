package com.mytastelog.server.account;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountIdentityRepository extends JpaRepository<AccountIdentityEntity, Long> {
	Optional<AccountIdentityEntity> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject);
}
