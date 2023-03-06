package io.dockstore.tooltester.runWorkflow;

import java.util.Date;

public class TimeStatisticForOneTask {
    private Date startTime;
    private Date endTime;
    private String taskName;

    TimeStatisticForOneTask(Date startTime, Date endTime, String taskName) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.taskName = taskName;
    }
    public Date getStartTime() {
        return startTime;
    }
    public Date getEndTime() {
        return endTime;
    }

    public String getTaskName() {
        return taskName;
    }

    public Long getTimeTakenInMilliseconds() {
        return endTime.getTime() - startTime.getTime();
    }

}
