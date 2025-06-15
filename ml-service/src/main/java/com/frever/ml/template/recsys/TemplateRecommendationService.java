package com.frever.ml.template.recsys;

import static com.frever.ml.utils.Constants.NUM_TEMPLATE_RECOMMENDATIONS;

import com.frever.ml.dao.SchoolTaskDao;
import com.frever.ml.dao.TemplateDao;
import com.frever.ml.dao.UserDao;
import com.frever.ml.dao.VideoDao;
import com.frever.ml.feed.recsys.FeedRecSysService;
import com.frever.ml.feed.recsys.GeoClusterService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class TemplateRecommendationService {
    @Inject
    UserDao userDao;
    @Inject
    VideoDao videoDao;
    @Inject
    SchoolTaskDao schoolTaskDao;
    @Inject
    TemplateDao templateDao;
    @Inject
    GeoClusterService geoClusterService;
    @Inject
    FeedRecSysService feedRecSysService;

    public List<Long> personalizedTemplateRecommendation(long groupId) {
        var userInfo = userDao.getUserInfo(groupId);
        if (userInfo == null) {
            Log.warn("User info not found for group: " + groupId);
            return Collections.emptyList();
        }
        List<Long> curatedTemplates = templateDao.getCuratedTemplates();
        var levelIds = schoolTaskDao.getSchoolTaskLevelIds();
        var assignedGeoClusters = geoClusterService.assignedGeoClusters(userInfo);
        var blockedUsers = userDao.getBlockedUsers(userInfo.groupId());
        var videoIds = feedRecSysService.getPersonalizedHeuristicsVideoRecommendation(
            userInfo,
            levelIds,
            assignedGeoClusters,
            blockedUsers
        );
        List<Long> personalizedTemplates = videoDao.getTemplateIdsFromVideoIds(videoIds);
        List<Long> result = new ArrayList<>(NUM_TEMPLATE_RECOMMENDATIONS);
        Set<Long> existingTemplateIds = new HashSet<>(curatedTemplates.size() + personalizedTemplates.size());
        for (var templateId : curatedTemplates) {
            if (result.size() >= NUM_TEMPLATE_RECOMMENDATIONS) {
                break;
            }
            if (existingTemplateIds.add(templateId)) {
                result.add(templateId);
            }
        }
        for (var templateId : personalizedTemplates) {
            if (result.size() >= NUM_TEMPLATE_RECOMMENDATIONS) {
                break;
            }
            if (existingTemplateIds.add(templateId)) {
                result.add(templateId);
            }
        }

        return result;
    }
}
