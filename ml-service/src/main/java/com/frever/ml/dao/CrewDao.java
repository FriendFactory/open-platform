package com.frever.ml.dao;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;

@ApplicationScoped
public class CrewDao extends BaseDao {
    private static final String CREW_MEMBERS = """
        SELECT c1."GroupId"
            FROM "CrewMember" AS c1
            INNER JOIN "CrewMember" AS c2 ON c1."CrewId" = c2."CrewId"
        WHERE c1."GroupId" != :groupId
            AND c1."RemovedAt" IS NULL
            AND c2."GroupId" = :groupId
            AND c2."RemovedAt" IS NULL
        GROUP BY c1."GroupId"
        """;

    public Set<Long> getCrewMembers(long groupId) {
        return jdbi.withHandle(handle -> handle.createQuery(CREW_MEMBERS)
            .bind("groupId", groupId)
            .mapTo(Long.class)
            .set());
    }
}
