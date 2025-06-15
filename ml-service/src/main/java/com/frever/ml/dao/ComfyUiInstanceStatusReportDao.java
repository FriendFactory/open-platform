package com.frever.ml.dao;

import static com.frever.ml.utils.Utils.normalizeServerIp;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ComfyUiInstanceStatusReportDao extends BaseDao {
    public boolean beginReport(String serverIp) {
        var realIp = normalizeServerIp(serverIp);
        return mlJdbi.withHandle(handle -> handle.createQuery("""
                SELECT pg_try_advisory_lock(id)
                FROM comfy_ui_instance_status_report
                WHERE server_ip = :serverIp::INET
                """)
            .bind("serverIp", realIp)
            .mapTo(boolean.class).one());
    }

    public boolean endReport(String serverIp) {
        var realIp = normalizeServerIp(serverIp);
        return mlJdbi.withHandle(handle -> handle.createQuery("""
                SELECT pg_advisory_unlock(id)
                FROM comfy_ui_instance_status_report
                WHERE server_ip = :serverIp::INET
                """)
            .bind("serverIp", realIp)
            .mapTo(boolean.class).one());
    }

    public void addComfyUiInstance(String serverIp) {
        var realIp = normalizeServerIp(serverIp);
        mlJdbi.useHandle(handle -> handle.createUpdate("""
                INSERT INTO comfy_ui_instance_status_report (server_ip)
                VALUES (:serverIp::INET)
                ON CONFLICT DO NOTHING
                """)
            .bind("serverIp", realIp)
            .execute());
    }

    public boolean comfyUiInstanceExists(String serverIp) {
        var realIp = normalizeServerIp(serverIp);
        return mlJdbi.withHandle(handle -> handle.createQuery("""
                SELECT EXISTS (
                    SELECT 1
                    FROM comfy_ui_instance_status_report
                    WHERE server_ip = :serverIp::INET
                )
                """)
            .bind("serverIp", realIp)
            .mapTo(boolean.class).one());
    }

    public boolean zeroReported(String serverIp) {
        var realIp = normalizeServerIp(serverIp);
        return mlJdbi.withHandle(handle -> handle.createQuery("""
                SELECT zero_tasks_reported
                FROM comfy_ui_instance_status_report
                WHERE server_ip = :serverIp::INET
                """)
            .bind("serverIp", realIp)
            .mapTo(boolean.class).one());
    }

    public void markReport(String serverIp) {
        var realIp = normalizeServerIp(serverIp);
        mlJdbi.useHandle(handle -> handle.createUpdate("""
                UPDATE comfy_ui_instance_status_report
                SET status_reported_at = now(), zero_tasks_reported = false
                WHERE server_ip = :serverIp::INET
                """)
            .bind("serverIp", realIp)
            .execute());
    }

    public void markZeroReported(String serverIp) {
        var realIp = normalizeServerIp(serverIp);
        mlJdbi.useHandle(handle -> handle.createUpdate("""
                UPDATE comfy_ui_instance_status_report
                SET zero_tasks_reported = true, status_reported_at = now()
                WHERE server_ip = :serverIp::INET
                """)
            .bind("serverIp", realIp)
            .execute());
    }

    public boolean reportedWithinMinutes(String serverIp, int minutes) {
        var realIp = normalizeServerIp(serverIp);
        return mlJdbi.withHandle(handle -> handle.createQuery("""
                SELECT status_reported_at >= now() - INTERVAL ':minutes minutes'
                FROM comfy_ui_instance_status_report
                WHERE server_ip = :serverIp::INET
                """.replace(":minutes", String.valueOf(minutes)))
            .bind("serverIp", realIp)
            .mapTo(boolean.class).one());
    }
}
