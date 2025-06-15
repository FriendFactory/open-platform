package com.frever.ml.dao;

import static com.frever.ml.utils.Utils.normalizeServerIp;

import com.frever.ml.comfy.ComfyUiTask;
import com.frever.ml.comfy.dto.ComfyUiTaskDurationItem;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ComfyUiTaskDao extends BaseDao {
    public long createTask(ComfyUiTask comfyUiTask) {
        var serverIp = comfyUiTask.getServerIp();
        var realIp = normalizeServerIp(serverIp);
        comfyUiTask.setServerIp(realIp);
        return mlJdbi.withHandle(handle -> handle.createQuery("""
                INSERT INTO comfy_ui_task (prompt_id, server_ip, group_id, video_id, level_id, version, workflow, file_name, duration, partial_name)
                VALUES (:promptId, :serverIp::INET, :groupId, :videoId, :levelId, :version, :workflow, :fileName, :duration, :partialName)
                returning id
                """)
            .bindBean(comfyUiTask).mapTo(Long.class).one());
    }

    public void markTaskFinished(long id) {
        mlJdbi.useHandle(handle -> handle.createUpdate("""
                UPDATE comfy_ui_task
                SET finished_at = now()
                WHERE id = :id
                """)
            .bind("id", id)
            .execute());
    }

    public void markTaskFinished(UUID promptId) {
        mlJdbi.useHandle(handle -> handle.createUpdate("""
                UPDATE comfy_ui_task
                SET finished_at = now()
                WHERE prompt_id = :promptId
                """)
            .bind("promptId", promptId)
            .execute());
    }

    public void markUnfinishedTasksFinished() {
        mlJdbi.useHandle(handle -> handle.createUpdate("""
                UPDATE comfy_ui_task
                SET finished_at = now()
                WHERE finished_at IS NULL AND created_at < now() - INTERVAL '1 day'
                """)
            .execute());
    }

    public boolean taskExists(UUID promptId) {
        return mlJdbi.withHandle(handle -> handle.createQuery("""
                SELECT 1
                FROM comfy_ui_task
                WHERE prompt_id = :promptId
                """)
            .bind("promptId", promptId)
            .mapTo(Boolean.class)
            .findFirst()
            .orElse(false));
    }

    public ComfyUiTask getComfyUiTask(long id) {
        return mlJdbi.withHandle(handle -> handle.createQuery("""
                SELECT *
                FROM comfy_ui_task
                WHERE id = :id
                """)
            .bind("id", id)
            .mapToBean(ComfyUiTask.class)
            .findFirst()
            .orElse(null));
    }

    public void clearFinishedTasksOlderThanOneDay() {
        mlJdbi.useHandle(handle -> handle.createUpdate("""
                DELETE FROM comfy_ui_task
                WHERE finished_at IS NOT NULL and finished_at <= now() - INTERVAL '1 day'
                """)
            .execute());
    }

    public List<ComfyUiTaskDurationItem> getComfyUiUnfinishedTaskDurationItems(String serverIp) {
        return mlJdbi.withHandle(handle -> handle.createQuery("""
                SELECT id, prompt_id, server_ip, duration, workflow, started_at
                FROM comfy_ui_task
                WHERE finished_at IS NULL AND server_ip = :serverIp::INET
                """)
            .bind("serverIp", serverIp)
            .mapTo(ComfyUiTaskDurationItem.class)
            .list());
    }

    public void markTaskStarted(UUID promptId, Instant timestamp) {
        mlJdbi.useHandle(handle -> handle.createUpdate("""
                UPDATE comfy_ui_task
                SET started_at = :timestamp
                WHERE prompt_id = :promptId
                """)
            .bind("promptId", promptId)
            .bind("timestamp", timestamp)
            .execute());
    }

    public boolean isCacheComponentsWorkflowRunning(String serverIp) {
        return mlJdbi.withHandle(handle -> handle.createQuery("""
                SELECT 1
                FROM comfy_ui_task
                WHERE workflow = 'cache-components' AND server_ip = :serverIp::INET AND finished_at IS NULL AND created_at >= now() - INTERVAL '1 hour'
                """)
            .bind("serverIp", serverIp)
            .mapTo(Boolean.class)
            .findFirst()
            .orElse(false));
    }
}
