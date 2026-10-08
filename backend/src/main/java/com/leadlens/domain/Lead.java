package com.leadlens.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * One company after cleaning: duplicate rows are merged into a single Lead,
 * and enrichment only fills fields that are still blank (the imported value wins).
 *
 * signals, score_breakdown and ai_brief hold JSON written by the service layer.
 */
@Entity
@Table(name = "lead")
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long batchId;
    @Column(nullable = false)
    private String company;
    /** Company name with legal suffixes and punctuation stripped; the dedup key when there is no domain. */
    @Column(nullable = false)
    private String companyKey;
    private String domain;
    private String website;
    private String industry;
    private String city;
    private String state;
    private String country;
    private Integer employees;
    private Long revenueUsd;
    private Integer foundedYear;
    private String ownerName;
    private String ownerTitle;
    private String email;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailStatus emailStatus = EmailStatus.MISSING;
    private String phone;
    private boolean phoneValid;
    private String linkedinUrl;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WebsiteStatus websiteStatus = WebsiteStatus.UNCHECKED;
    private String websiteNote;
    private String description;
    private String signals;
    /** Comma-separated names of fields that came from the website rather than the import. */
    private String enrichedFields;
    private int sourceRows = 1;
    private int score;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tier tier = Tier.C;
    private String scoreBreakdown;
    private String excludedReason;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeadStatus status = LeadStatus.NEW;
    private String notes;
    private String aiBrief;
    private String aiProvider;
    private Instant contactedAt;
    /** When the next touch is due; set automatically when a lead is marked CONTACTED. */
    private Instant followUpAt;
    /** True while verification/enrichment is running for this lead. */
    private boolean processing;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getCompanyKey() { return companyKey; }
    public void setCompanyKey(String companyKey) { this.companyKey = companyKey; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public Integer getEmployees() { return employees; }
    public void setEmployees(Integer employees) { this.employees = employees; }
    public Long getRevenueUsd() { return revenueUsd; }
    public void setRevenueUsd(Long revenueUsd) { this.revenueUsd = revenueUsd; }
    public Integer getFoundedYear() { return foundedYear; }
    public void setFoundedYear(Integer foundedYear) { this.foundedYear = foundedYear; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getOwnerTitle() { return ownerTitle; }
    public void setOwnerTitle(String ownerTitle) { this.ownerTitle = ownerTitle; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public EmailStatus getEmailStatus() { return emailStatus; }
    public void setEmailStatus(EmailStatus emailStatus) { this.emailStatus = emailStatus; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public boolean isPhoneValid() { return phoneValid; }
    public void setPhoneValid(boolean phoneValid) { this.phoneValid = phoneValid; }
    public String getLinkedinUrl() { return linkedinUrl; }
    public void setLinkedinUrl(String linkedinUrl) { this.linkedinUrl = linkedinUrl; }
    public WebsiteStatus getWebsiteStatus() { return websiteStatus; }
    public void setWebsiteStatus(WebsiteStatus websiteStatus) { this.websiteStatus = websiteStatus; }
    public String getWebsiteNote() { return websiteNote; }
    public void setWebsiteNote(String websiteNote) { this.websiteNote = websiteNote; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSignals() { return signals; }
    public void setSignals(String signals) { this.signals = signals; }
    public String getEnrichedFields() { return enrichedFields; }
    public void setEnrichedFields(String enrichedFields) { this.enrichedFields = enrichedFields; }
    public int getSourceRows() { return sourceRows; }
    public void setSourceRows(int sourceRows) { this.sourceRows = sourceRows; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public Tier getTier() { return tier; }
    public void setTier(Tier tier) { this.tier = tier; }
    public String getScoreBreakdown() { return scoreBreakdown; }
    public void setScoreBreakdown(String scoreBreakdown) { this.scoreBreakdown = scoreBreakdown; }
    public String getExcludedReason() { return excludedReason; }
    public void setExcludedReason(String excludedReason) { this.excludedReason = excludedReason; }
    public LeadStatus getStatus() { return status; }
    public void setStatus(LeadStatus status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getAiBrief() { return aiBrief; }
    public void setAiBrief(String aiBrief) { this.aiBrief = aiBrief; }
    public String getAiProvider() { return aiProvider; }
    public void setAiProvider(String aiProvider) { this.aiProvider = aiProvider; }
    public Instant getContactedAt() { return contactedAt; }
    public void setContactedAt(Instant contactedAt) { this.contactedAt = contactedAt; }
    public Instant getFollowUpAt() { return followUpAt; }
    public void setFollowUpAt(Instant followUpAt) { this.followUpAt = followUpAt; }
    public boolean isProcessing() { return processing; }
    public void setProcessing(boolean processing) { this.processing = processing; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
