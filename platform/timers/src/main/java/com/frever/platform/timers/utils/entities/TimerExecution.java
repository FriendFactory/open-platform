package com.frever.platform.timers.utils.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(schema = "stats", name = "timer_execution")
public class TimerExecution {
    @Id
    @Column(name = "timer_name")
    private String timerName;
    @Column(name = "last_execution_time")
    private Instant lastExecutionTime;

    // For JPA
    protected TimerExecution() {
    }

    public TimerExecution(String timerName, Instant lastExecutionTime) {
        this.timerName = timerName;
        this.lastExecutionTime = lastExecutionTime;
    }

    public String getTimerName() {
        return timerName;
    }

    public Instant getLastExecutionTime() {
        return lastExecutionTime;
    }

    public void setLastExecutionTime(Instant lastExecutionTime) {
        this.lastExecutionTime = lastExecutionTime;
    }
}
