package com.linkedin.thirdeye.anomaly.monitor;

import java.util.Objects;

import com.google.common.base.MoreObjects;
import com.linkedin.thirdeye.anomaly.monitor.MonitorConstants.MonitorType;
import com.linkedin.thirdeye.anomaly.task.TaskInfo;

public class MonitorTaskInfo implements TaskInfo {

  private Long jobExecutionId;
  private MonitorType monitorType;
  private int expireDaysAgo;

  public MonitorTaskInfo() {

  }

  public Long getJobExecutionId() {
    return jobExecutionId;
  }

  public void setJobExecutionId(Long jobExecutionId) {
    this.jobExecutionId = jobExecutionId;
  }

  public MonitorType getMonitorType() {
    return monitorType;
  }

  public void setMonitorType(MonitorType monitorType) {
    this.monitorType = monitorType;
  }

  public int getExpireDaysAgo() {
    return expireDaysAgo;
  }

  public void setExpireDaysAgo(int expireDaysAgo) {
    this.expireDaysAgo = expireDaysAgo;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof MonitorTaskInfo)) {
      return false;
    }
    MonitorTaskInfo mt = (MonitorTaskInfo) o;
    return Objects.equals(jobExecutionId, mt.getJobExecutionId()) && Objects.equals(monitorType, mt.getMonitorType())
        && Objects.equals(expireDaysAgo, mt.getExpireDaysAgo());
  }

  @Override
  public int hashCode() {
    return Objects.hash(jobExecutionId, monitorType, expireDaysAgo);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("jobExecutionId", jobExecutionId).add("monitorType", monitorType)
        .add("expireDaysAgo", expireDaysAgo).toString();
  }
}
