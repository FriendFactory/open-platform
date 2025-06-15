package com.frever.platform.timers.template.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "public", name = "\"Template\"")
public class Template {
    @Id
    @Column(name = "\"Id\"")
    private Long id;
    @Column(name = "\"FilesInfo\"")
    @JdbcTypeCode(SqlTypes.JSON)
    private String files;
    @Column(name = "\"TemplateSubCategoryId\"")
    private long templateSubCategoryId;
    @Column(name = "\"Title\"")
    private String title;
    @Column(name = "\"TopListPositionInDiscovery\"")
    private Long topListPositionInDiscovery;
    @Column(name = "\"TrendingSortingOrder\"")
    private Long trendingSortingOrder;
    @Column(name = "\"CategorySortingOrder\"")
    private Long categorySortingOrder;
    @Column(name = "\"SubCategorySortingOrder\"")
    private Long subCategorySortingOrder;
    @Column(name = "\"OnBoardingSortingOrder\"")
    private Long onboardingSortingOrder;
    @Column(name = "\"Description\"")
    private String description;
    @Column(name = "\"CharacterCount\"")
    private int characterCount;
    @Column(name = "\"ArtistName\"")
    private String artistName;
    @Column(name = "\"SongName\"")
    private String songName;
    @Column(name = "\"IsDeleted\"")
    private boolean isDeleted;
    @Column(name = "\"ReverseThumbnail\"")
    private boolean reverseThumbnail;
    @Column(name = "\"ReadinessId\"")
    private long readinessId;
    @Column(name = "\"Tags\"")
    private long[] tags = new long[0];
    @Column(name = "\"CreatedTime\"")
    private Instant createdTime;
    @Column(name = "\"CreatorId\"")
    private long creatorId;
    @Column(name = "\"EventId\"")
    private long eventId;
    @Column(name = "\"ModifiedTime\"")
    private Instant modifiedTime;

    // For JPA
    protected Template() {
    }

    public Long getId() {
        return id;
    }

    public String getFiles() {
        return files;
    }

    public void setFiles(String files) {
        this.files = files;
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

    public Instant getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(Instant createdTime) {
        this.createdTime = createdTime;
    }

    public long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(long creatorId) {
        this.creatorId = creatorId;
    }

    public long getEventId() {
        return eventId;
    }

    public void setEventId(long eventId) {
        this.eventId = eventId;
    }

    public Instant getModifiedTime() {
        return modifiedTime;
    }

    public void setModifiedTime(Instant modifiedTime) {
        this.modifiedTime = modifiedTime;
    }
}
