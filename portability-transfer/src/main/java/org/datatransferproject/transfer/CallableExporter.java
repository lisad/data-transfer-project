/*
 * Copyright 2018 The Data Transfer Project Authors.
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

package org.datatransferproject.transfer;

import com.google.common.base.Stopwatch;
import com.google.inject.Provider;
import org.datatransferproject.api.launcher.DtpInternalMetricRecorder;
import org.datatransferproject.api.launcher.ServiceResult;
import org.datatransferproject.spi.transfer.provider.ExportResult;
import org.datatransferproject.spi.transfer.provider.Exporter;
import org.datatransferproject.types.common.ExportInformation;
import org.datatransferproject.types.transfer.auth.AuthData;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Callable around an {@link Exporter}.
 */
public class CallableExporter implements Callable<ExportResult> {

    private Provider<Exporter> exporterProvider;
    private UUID jobId;
    private AuthData authData;
    private Optional<ExportInformation> exportInformation;
    private final DtpInternalMetricRecorder metricRecorder;

    public CallableExporter(
            Provider<Exporter> exporterProvider,
            UUID jobId,
            AuthData authData,
            Optional<ExportInformation> exportInformation,
            DtpInternalMetricRecorder metricRecorder) {
        this.exporterProvider = checkNotNull(exporterProvider, "exportProvider can't be null");
        this.jobId = checkNotNull(jobId, "jobId can't be null");
        this.authData = checkNotNull(authData, "authData can't be null");
        this.exportInformation = exportInformation;
        this.metricRecorder = checkNotNull(metricRecorder, "metric recorder can't be null");
    }

    @Override
    public ExportResult call() throws Exception {
        boolean success = false;
        Stopwatch stopwatch = Stopwatch.createStarted();
        try {
            ExportResult result = exporterProvider.get().export(jobId, authData, exportInformation);
            success = result.getType() != ExportResult.ResultType.ERROR;
            return result;
        } finally {
            metricRecorder.exportPageAttemptFinished(
                    JobMetadata.getDataType(), new ServiceResult(
                            JobMetadata.getExportService(),
                            success,
                            stopwatch.elapsed())
            );
        }
    }
}
