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
import org.datatransferproject.spi.transfer.idempotentexecutor.IdempotentImportExecutor;
import org.datatransferproject.spi.transfer.provider.ImportResult;
import org.datatransferproject.spi.transfer.provider.Importer;
import org.datatransferproject.types.common.models.DataModel;
import org.datatransferproject.types.transfer.auth.AuthData;
import org.datatransferproject.types.transfer.errors.ErrorDetail;

import java.io.IOException;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Callable wrapper around an {@link Importer}.
 */
public class CallableImporter implements Callable<ImportResult> {

    private final Provider<Importer> importerProvider;
    private final UUID jobId;
    private final IdempotentImportExecutor idempotentImportExecutor;
    private final AuthData authData;
    private final DataModel data;
    private final DtpInternalMetricRecorder metricRecorder;

    public CallableImporter(
            Provider<Importer> importerProvider,
            UUID jobId,
            IdempotentImportExecutor idempotentImportExecutor,
            AuthData authData,
            DataModel data,
            DtpInternalMetricRecorder metricRecorder) {
        this.importerProvider = importerProvider;
        this.jobId = jobId;
        this.idempotentImportExecutor = idempotentImportExecutor;
        this.authData = authData;
        this.data = data;
        this.metricRecorder = metricRecorder;
    }

    @Override
    public ImportResult call() throws Exception {
        boolean success = false;
        Stopwatch stopwatch = Stopwatch.createStarted();
        try {
            idempotentImportExecutor.resetRecentErrors();
            ImportResult result = importerProvider.get()
                    .importItem(jobId, idempotentImportExecutor, authData, data);

            Collection<ErrorDetail> errors = idempotentImportExecutor.getRecentErrors();
            success = result.getType() == ImportResult.ResultType.OK && errors.isEmpty();

            if (!success && errors.iterator().hasNext() && !errors.iterator().next().canSkip()) {
                throw new IOException(
                        "Problem with importer, forcing a retry, "
                                + "first error: "
                                + (errors.iterator().hasNext() ? errors.iterator().next().exception() : "none"));
            }

            result = result.copyWithCounts(data.getCounts());
            return result;
        } finally {
            metricRecorder.importPageAttemptFinished(
                    JobMetadata.getDataType(), new ServiceResult(
                            JobMetadata.getImportService(),
                            success,
                            stopwatch.elapsed())
            );
        }
    }
}
