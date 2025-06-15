package com.frever.platform.timers.videoKpi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(schema = "stats", name = "video_kpi")
public class VideoKpi {
    @Id
    @Column(name = "video_id")
    private long videoId;
    @Column(name = "likes")
    private long likes;
    @Column(name = "views")
    private long views;
    @Column(name = "comments")
    private long comments;
    @Column(name = "shares")
    private long shares;
    @Column(name = "remixes")
    private long remixes;
    @Column(name = "battles_won")
    private long battlesWon;
    @Column(name = "battles_lost")
    private long battlesLost;
    @Column(name = "deleted")
    private boolean deleted;

    public long getVideoId() {
        return videoId;
    }

    public void setVideoId(long videoId) {
        this.videoId = videoId;
    }

    public long getLikes() {
        return likes;
    }

    public void setLikes(long likes) {
        this.likes = likes;
    }

    public long getViews() {
        return views;
    }

    public void setViews(long views) {
        this.views = views;
    }

    public long getComments() {
        return comments;
    }

    public void setComments(long comments) {
        this.comments = comments;
    }

    public long getShares() {
        return shares;
    }

    public void setShares(long shares) {
        this.shares = shares;
    }

    public long getRemixes() {
        return remixes;
    }

    public void setRemixes(long remixes) {
        this.remixes = remixes;
    }

    public long getBattlesWon() {
        return battlesWon;
    }

    public void setBattlesWon(long battlesWon) {
        this.battlesWon = battlesWon;
    }

    public long getBattlesLost() {
        return battlesLost;
    }

    public void setBattlesLost(long battlesLost) {
        this.battlesLost = battlesLost;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}
