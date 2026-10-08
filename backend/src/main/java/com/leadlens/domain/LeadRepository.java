package com.leadlens.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface LeadRepository extends JpaRepository<Lead, Long>, JpaSpecificationExecutor<Lead> {

    Optional<Lead> findFirstByDomain(String domain);

    List<Lead> findByDomainIn(java.util.Collection<String> domains);

    List<Lead> findByCompanyKeyIn(java.util.Collection<String> companyKeys);

    Optional<Lead> findFirstByCompanyKeyAndStateIgnoreCase(String companyKey, String state);

    Optional<Lead> findFirstByCompanyKey(String companyKey);

    @Query("select l.tier, count(l) from Lead l group by l.tier")
    List<Object[]> countByTier();

    @Query("select l.status, count(l) from Lead l group by l.status")
    List<Object[]> countByStatus();

    @Query("select l.emailStatus, count(l) from Lead l group by l.emailStatus")
    List<Object[]> countByEmailStatus();

    @Query("select l.websiteStatus, count(l) from Lead l group by l.websiteStatus")
    List<Object[]> countByWebsiteStatus();

    @Query("select coalesce(avg(l.score), 0) from Lead l where l.tier <> com.leadlens.domain.Tier.X")
    double averageScore();

    @Query("select count(l) from Lead l where l.tier in (com.leadlens.domain.Tier.A, com.leadlens.domain.Tier.B)"
        + " and (l.emailStatus in (com.leadlens.domain.EmailStatus.VALID, com.leadlens.domain.EmailStatus.ROLE,"
        + " com.leadlens.domain.EmailStatus.UNVERIFIED) or l.phoneValid = true)"
        + " and l.status in (com.leadlens.domain.LeadStatus.NEW, com.leadlens.domain.LeadStatus.QUALIFIED)")
    long countReadyToContact();

    @Query("select distinct l.industry from Lead l where l.industry is not null")
    List<String> distinctIndustries();

    @Query("select count(l) from Lead l where l.enrichedFields is not null")
    long countEnriched();

    @Modifying
    @Transactional
    @Query("update Lead l set l.processing = false where l.processing = true")
    int clearProcessingFlags();
}
