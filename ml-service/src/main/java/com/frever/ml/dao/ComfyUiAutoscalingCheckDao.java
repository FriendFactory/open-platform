package com.frever.ml.dao;

import static org.jdbi.v3.core.transaction.TransactionIsolationLevel.READ_COMMITTED;

import com.frever.ml.comfy.ComfyUiAutoscalingCheck;
import com.frever.ml.comfy.setup.ComfyUiAutoscalingCheckCallback;
import com.frever.ml.comfy.setup.ComfyUiTaskInstanceSetup;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ComfyUiAutoscalingCheckDao extends BaseDao {
    public void comfyUiAutoscalingCheck(ComfyUiTaskInstanceSetup setup, ComfyUiAutoscalingCheckCallback callback) {
        mlJdbi.inTransaction(
            READ_COMMITTED, transactionHandle -> {
                var existingCheck = transactionHandle.createQuery(
                        "SELECT * FROM public.comfyui_autoscaling_check WHERE setup_type = :setupType FOR UPDATE")
                    .bind("setupType", setup.name())
                    .mapToBean(ComfyUiAutoscalingCheck.class)
                    .findOne()
                    .orElseThrow();
                var updatedCheck = callback.comfyUiAutoscalingCheck(existingCheck);
                var update = """
                    UPDATE comfyui_autoscaling_check SET checked_at = :checkedAt, above_threshold_since = :aboveThresholdSince, below_threshold_since = :belowThresholdSince WHERE setup_type = :setupType""";
                transactionHandle.createUpdate(update)
                    .bind("checkedAt", updatedCheck.getCheckedAt())
                    .bind("aboveThresholdSince", updatedCheck.getAboveThresholdSince())
                    .bind("belowThresholdSince", updatedCheck.getBelowThresholdSince())
                    .bind("setupType", setup.name())
                    .execute();
                return null;
            }
        );
    }
}
