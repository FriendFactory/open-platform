package com.frever.platform.timers.template.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "stats", name = "template_data")
public class TemplateData {
    @Id
    private long id;
    @Column(name = "level_id")
    private long levelId;
    @Column(name = "files")
    @JdbcTypeCode(SqlTypes.JSON)
    private String files;
    @Column(name = "template_category_id")
    private long templateCategoryId;
    @Column(name = "template_sub_category_id")
    private long templateSubCategoryId;
    @Column(name = "title")
    private String title;
    @Column(name = "top_list_position_in_discovery")
    private Long topListPositionInDiscovery;
    @Column(name = "trending_sorting_order")
    private Long trendingSortingOrder;
    @Column(name = "category_sorting_order")
    private Long categorySortingOrder;
    @Column(name = "sub_category_sorting_order")
    private Long subCategorySortingOrder;
    @Column(name = "onboarding_sorting_order")
    private Long onboardingSortingOrder;
    @Column(name = "description")
    private String description;
    @Column(name = "character_count")
    private int characterCount;
    @Column(name = "artist_name")
    private String artistName;
    @Column(name = "song_name")
    private String songName;
    @Column(name = "is_deleted")
    private boolean isDeleted;
    @Column(name = "reverse_thumbnail")
    private boolean reverseThumbnail;
    @Column(name = "readiness_id")
    private long readinessId;
    @Column(name = "tags")
    private long[] tags = new long[0];
    @Column(name = "usage_count")
    private long usageCount = 0;
    @Column(name = "created_time")
    private Instant createdTime;
    @Column(name = "original_video_id")
    private Long originalVideoId;
    @Column(name = "creator_id")
    private long creatorId;
    @Column(name = "creator_nickname")
    private String creatorNickname;
    @Column(name = "creator_main_character_id")
    private Long creatorMainCharacterId;
    @Column(name = "creator_main_character_files")
    private String creatorMainCharacterFiles;
    @Column(name = "country")
    private String country;
    @Column(name = "language")
    private String language;
    @Column(name = "external_song_ids")
    private long[] externalSongIds = new long[0];
    @Column(name = "event_id")
    private long eventId;
    @Column(name = "stats_updated_timestamp")
    private Instant statsUpdatedTimestamp;
    @Column(name = "song_ids")
    private long[] songIds = new long[0];

    // For JPA
    protected TemplateData() {
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public long getLevelId() {
        return levelId;
    }

    public void setLevelId(long levelId) {
        this.levelId = levelId;
    }

    public String getFiles() {
        return files;
    }

    public void setFiles(String files) {
        this.files = files;
    }

    public long getTemplateCategoryId() {
        return templateCategoryId;
    }

    public void setTemplateCategoryId(long templateCategoryId) {
        this.templateCategoryId = templateCategoryId;
    }

    public long getTemplateSubCategoryId() {
        return templateSubCategoryId;
    }

    public void setTemplateSubCategoryId(long templateSubCategoryId) {
        this.templateSubCategoryId = templateSubCategoryId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getTopListPositionInDiscovery() {
        return topListPositionInDiscovery;
    }

    public void setTopListPositionInDiscovery(Long topListPositionInDiscovery) {
        this.topListPositionInDiscovery = topListPositionInDiscovery;
    }

    public Long getTrendingSortingOrder() {
        return trendingSortingOrder;
    }

    public void setTrendingSortingOrder(Long trendingSortingOrder) {
        this.trendingSortingOrder = trendingSortingOrder;
    }

    public Long getCategorySortingOrder() {
        return categorySortingOrder;
    }

    public void setCategorySortingOrder(Long categorySortingOrder) {
        this.categorySortingOrder = categorySortingOrder;
    }

    public Long getSubCategorySortingOrder() {
        return subCategorySortingOrder;
    }

    public void setSubCategorySortingOrder(Long subCategorySortingOrder) {
        this.subCategorySortingOrder = subCategorySortingOrder;
    }

    public Long getOnboardingSortingOrder() {
        return onboardingSortingOrder;
    }

    public void setOnboardingSortingOrder(Long onboardingSortingOrder) {
        this.onboardingSortingOrder = onboardingSortingOrder;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getCharacterCount() {
        return characterCount;
    }

    public void setCharacterCount(int characterCount) {
        this.characterCount = characterCount;
    }

    public String getArtistName() {
        return artistName;
    }

    public void setArtistName(String artistName) {
        this.artistName = artistName;
    }

    public String getSongName() {
        return songName;
    }

    public void setSongName(String songName) {
        this.songName = songName;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean deleted) {
        isDeleted = deleted;
    }

    public boolean isReverseThumbnail() {
        return reverseThumbnail;
    }

    public void setReverseThumbnail(boolean reverseThumbnail) {
        this.reverseThumbnail = reverseThumbnail;
    }

    public long getReadinessId() {
        return readinessId;
    }

    public void setReadinessId(long readinessId) {
        this.readinessId = readinessId;
    }

    public long[] getTags() {
        return tags;
    }

    public void setTags(long[] tags) {
        this.tags = tags;
    }

    public long getUsageCount() {
        return usageCount;
    }

    public void setUsageCount(long usageCount) {
        this.usageCount = usageCount;
    }

    public Instant getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(Instant createdTime) {
        this.createdTime = createdTime;
    }

    public Long getOriginalVideoId() {
        return originalVideoId;
    }

    public void setOriginalVideoId(Long originalVideoId) {
        this.originalVideoId = originalVideoId;
    }

    public long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(long creatorId) {
        this.creatorId = creatorId;
    }

    public String getCreatorNickname() {
        return creatorNickname;
    }

    public void setCreatorNickname(String creatorNickname) {
        this.creatorNickname = creatorNickname;
    }

    public Long getCreatorMainCharacterId() {
        return creatorMainCharacterId;
    }

    public void setCreatorMainCharacterId(Long creatorMainCharacterId) {
        this.creatorMainCharacterId = creatorMainCharacterId;
    }

    public String getCreatorMainCharacterFiles() {
        return creatorMainCharacterFiles;
    }

    public void setCreatorMainCharacterFiles(String creatorMainCharacterFiles) {
        this.creatorMainCharacterFiles = creatorMainCharacterFiles;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public long[] getExternalSongIds() {
        return externalSongIds;
    }

    public void setExternalSongIds(long[] externalSongIds) {
        this.externalSongIds = externalSongIds;
    }

    public long getEventId() {
        return eventId;
    }

    public void setEventId(long eventId) {
        this.eventId = eventId;
    }

    public Instant getStatsUpdatedTimestamp() {
        return statsUpdatedTimestamp;
    }

    public void setStatsUpdatedTimestamp(Instant statsUpdatedTimestamp) {
        this.statsUpdatedTimestamp = statsUpdatedTimestamp;
    }

    public long[] getSongIds() {
        return songIds;
    }

    public void setSongIds(long[] songIds) {
        this.songIds = songIds;
    }

    public static TemplateData newCopyFromTemplate(Template template) {
        TemplateData templateData = new TemplateData();
        templateData.setId(template.getId());
        templateData.setFiles(template.getFiles());
        templateData.setTemplateSubCategoryId(template.getTemplateSubCategoryId());
        templateData.setTitle(template.getTitle());
        templateData.setTopListPositionInDiscovery(template.getTopListPositionInDiscovery());
        templateData.setTrendingSortingOrder(template.getTrendingSortingOrder());
        templateData.setCategorySortingOrder(template.getCategorySortingOrder());
        templateData.setSubCategorySortingOrder(template.getSubCategorySortingOrder());
        templateData.setOnboardingSortingOrder(template.getOnboardingSortingOrder());
        templateData.setDescription(template.getDescription());
        templateData.setCharacterCount(template.getCharacterCount());
        templateData.setArtistName(template.getArtistName());
        templateData.setSongName(template.getSongName());
        templateData.setDeleted(template.isDeleted());
        templateData.setReverseThumbnail(template.isReverseThumbnail());
        templateData.setReadinessId(template.getReadinessId());
        templateData.setTags(template.getTags());
        templateData.setCreatedTime(template.getCreatedTime());
        templateData.setCreatorId(template.getCreatorId());
        templateData.setEventId(template.getEventId());
        return templateData;
    }

    public TemplateData copyFromTemplate(Template template) {
        this.setFiles(template.getFiles());
        this.setTemplateSubCategoryId(template.getTemplateSubCategoryId());
        this.setTitle(template.getTitle());
        this.setTopListPositionInDiscovery(template.getTopListPositionInDiscovery());
        this.setTrendingSortingOrder(template.getTrendingSortingOrder());
        this.setCategorySortingOrder(template.getCategorySortingOrder());
        this.setSubCategorySortingOrder(template.getSubCategorySortingOrder());
        this.setOnboardingSortingOrder(template.getOnboardingSortingOrder());
        this.setDescription(template.getDescription());
        this.setCharacterCount(template.getCharacterCount());
        this.setArtistName(template.getArtistName());
        this.setSongName(template.getSongName());
        this.setDeleted(template.isDeleted());
        this.setReverseThumbnail(template.isReverseThumbnail());
        this.setReadinessId(template.getReadinessId());
        this.setTags(template.getTags());
        this.setCreatedTime(template.getCreatedTime());
        this.setCreatorId(template.getCreatorId());
        this.setEventId(template.getEventId());
        return this;
    }
}
