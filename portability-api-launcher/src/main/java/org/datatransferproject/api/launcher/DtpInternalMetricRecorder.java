/*
 * Copyright 2019 The Data Transfer Project Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.datatransferproject.api.launcher;

import org.datatransferproject.types.common.models.DataVertical;

import java.time.Duration;

/**
 * Interface to log metrics about a DTP job.
 *
 * <p>Note this class is to be used by DTP framework code only, transfer extensions should use:
 * {@link MetricRecorder} which pipes calls into the recordGeneric* methods.
 *
 * <p>Cloud extensions should implement this interface to records stats to their preferred stats
 * platform.
 */
public interface DtpInternalMetricRecorder {
    // Metrics related to DTP internals

    /**
     * A DTP job started.
     **/
    void startedJob(DataVertical dataType, String exportService, String importService);

    /**
     * A DTP job finished
     **/
    void finishedJob(
            DataVertical dataType,
            String exportService,
            String importService,
            boolean success,
            Duration duration);

    /**
     * A DTP job cancelled
     **/
    void cancelledJob(
            DataVertical dataType,
            String exportService,
            String importService,
            Duration duration);

    /**
     * An single attempt to export a page of data finished.
     *
     * @param serviceResult The ServiceResult object
     */
    void exportPageAttemptFinished(
            DataVertical dataType,
            ServiceResult serviceResult);

    /**
     * An attempt to export a page of data finished including all retires.
     *
     * @param serviceResult The ServiceResult object
     */
    void exportPageFinished(DataVertical dataType, ServiceResult serviceResult);

    /**
     * An single attempt to import a page of data finished.
     *
     * @param serviceResult The ServiceResult object
     */
    void importPageAttemptFinished(
            DataVertical dataType,
            ServiceResult serviceResult);

    /**
     * An attempt to import a page of data finished including all retires.
     *
     * @param serviceResult The ServiceResult object
     **/
    void importPageFinished(DataVertical dataType, ServiceResult serviceResult);

    // Metrics from {@link MetricRecorder}
    void recordGenericMetric(DataVertical dataType, String service, String tag);

    void recordGenericMetric(DataVertical dataType, String service, String tag, boolean bool);

    void recordGenericMetric(DataVertical dataType, String service, String tag, Duration duration);

    void recordGenericMetric(DataVertical dataType, String service, String tag, int value);
}
