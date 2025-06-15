package com.frever.platform.timers.followerStats.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(schema = "stats", name = "follow_edge")
@IdClass(FollowEdge.FollowEdgeListId.class)
public class FollowEdge {
    @Id
    @Column(name = "source")
    private long source;
    @Id
    @Column(name = "destination")
    private long destination;
    @Column(name = "is_mutual")
    private boolean isMutual;
    @Column(name = "source_is_minor")
    private boolean sourceIsMinor;
    @Column(name = "source_strict_coppa_rules")
    private boolean sourceStrictCoppaRules;
    @Column(name = "destination_is_minor")
    private boolean destinationIsMinor;
    @Column(name = "destination_strict_coppa_rules")
    private boolean destinationStrictCoppaRules;

    public long getSource() {
        return source;
    }

    public void setSource(long source) {
        this.source = source;
    }

    public long getDestination() {
        return destination;
    }

    public void setDestination(long destination) {
        this.destination = destination;
    }

    public boolean isMutual() {
        return isMutual;
    }

    public void setMutual(boolean mutual) {
        isMutual = mutual;
    }

    public boolean isSourceIsMinor() {
        return sourceIsMinor;
    }

    public void setSourceIsMinor(boolean sourceIsMinor) {
        this.sourceIsMinor = sourceIsMinor;
    }

    public boolean isSourceStrictCoppaRules() {
        return sourceStrictCoppaRules;
    }

    public void setSourceStrictCoppaRules(boolean sourceStrictCoppaRules) {
        this.sourceStrictCoppaRules = sourceStrictCoppaRules;
    }

    public boolean isDestinationIsMinor() {
        return destinationIsMinor;
    }

    public void setDestinationIsMinor(boolean destinationIsMinor) {
        this.destinationIsMinor = destinationIsMinor;
    }

    public boolean isDestinationStrictCoppaRules() {
        return destinationStrictCoppaRules;
    }

    public void setDestinationStrictCoppaRules(boolean destinationStrictCoppaRules) {
        this.destinationStrictCoppaRules = destinationStrictCoppaRules;
    }

    public static class FollowEdgeListId implements Serializable {
        private long source;
        private long destination;

        protected FollowEdgeListId() {
        }

        public FollowEdgeListId(long source, long destination) {
            this.source = source;
            this.destination = destination;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FollowEdgeListId that = (FollowEdgeListId) o;
            return source == that.source && destination == that.destination;
        }

        @Override
        public int hashCode() {
            return Objects.hash(source, destination);
        }
    }
}
