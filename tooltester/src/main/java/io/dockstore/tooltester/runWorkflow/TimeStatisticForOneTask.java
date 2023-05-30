/*
 *    Copyright 2023
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.dockstore.tooltester.runWorkflow;

import java.util.Date;

/**
 * Used for holding and giving information about the time it took a single task in a workflow to run.
 */
public class TimeStatisticForOneTask {
    private final Date startTime;
    private final Date endTime;
    private final String taskName;

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
        if (startTime == null || endTime == null) {
            return 0L;
        }
        return endTime.getTime() - startTime.getTime();
    }

}
