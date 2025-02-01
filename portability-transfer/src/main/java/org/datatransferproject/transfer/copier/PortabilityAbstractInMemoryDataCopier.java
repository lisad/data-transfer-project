/*
 * Copyright 2020 The Data Transfer Project Authors.
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
package org.datatransferproject.transfer.copier;

import com.google.cloud.datastore.DatastoreException;
import com.google.common.base.Stopwatch;
import com.google.inject.Provider;
import org.datatransferproject.api.launcher.DtpInternalMetricRecorder;
import org.datatransferproject.api.launcher.Monitor;
import org.datatransferproject.api.launcher.ServiceResult;
import org.datatransferproject.launcher.monitor.events.EventCode;
import org.datatransferproject.spi.cloud.connection.ConnectionProvider;
import org.datatransferproject.spi.cloud.storage.JobStore;
import org.datatransferproject.spi.cloud.types.PortabilityJob;
import org.datatransferproject.spi.cloud.types.PortabilityJob.TransferMode;
import org.datatransferproject.spi.transfer.idempotentexecutor.IdempotentImportExecutor;
import org.datatransferproject.spi.transfer.provider.ExportResult;
import org.datatransferproject.spi.transfer.provider.Exporter;
import org.datatransferproject.spi.transfer.provider.ImportResult;
import org.datatransferproject.spi.transfer.provider.Importer;
import org.datatransferproject.spi.transfer.types.CopyException;
import org.datatransferproject.spi.transfer.types.CopyExceptionWithFailureReason;
import org.datatransferproject.transfer.*;
import org.datatransferproject.types.common.DownloadableItem;
import org.datatransferproject.types.common.ExportInformation;
import org.datatransferproject.types.common.models.DataModel;
import org.datatransferproject.types.common.models.media.MediaContainerResource;
import org.datatransferproject.types.common.models.photos.PhotosContainerResource;
import org.datatransferproject.types.common.models.videos.VideosContainerResource;
import org.datatransferproject.types.transfer.auth.AuthData;
import org.datatransferproject.types.transfer.errors.ErrorDetail;
import org.datatransferproject.types.transfer.retry.RetryException;
import org.datatransferproject.types.transfer.retry.RetryStrategyLibrary;
import org.datatransferproject.types.transfer.retry.RetryingCallable;

import java.io.IOException;
import java.time.Clock;
import java.util.*;

public abstract class PortabilityAbstractInMemoryDataCopier implements InMemoryDataCopier {
    /**
     * Lazy evaluate exporter and importer as their providers depend on the polled {@code
     * PortabilityJob} which is not available at startup.
     */
    protected final Provider<Exporter> exporterProvider;

    protected final Provider<Importer> importerProvider;
    protected final IdempotentImportExecutor idempotentImportExecutor;
    protected final IdempotentImportExecutor retryingIdempotentImportExecutor;
    protected final Provider<RetryStrategyLibrary> retryStrategyLibraryProvider;
    protected final Monitor monitor;
    protected final DtpInternalMetricRecorder metricRecorder;
    protected final JobStore jobStore;

    public PortabilityAbstractInMemoryDataCopier(
            Provider<Exporter> exporterProvider,
            Provider<Importer> importerProvider,
            Provider<RetryStrategyLibrary> retryStrategyLibraryProvider,
            Monitor monitor,
            IdempotentImportExecutor idempotentImportExecutor,
            @Annotations.RetryingExecutor IdempotentImportExecutor retryingIdempotentImportExecutor,
            DtpInternalMetricRecorder dtpInternalMetricRecorder,
            JobStore jobStore) {
        this.exporterProvider = exporterProvider;
        this.importerProvider = importerProvider;
        this.retryStrategyLibraryProvider = retryStrategyLibraryProvider;
        this.monitor = monitor;
        this.idempotentImportExecutor = idempotentImportExecutor;
        this.retryingIdempotentImportExecutor = retryingIdempotentImportExecutor;
        this.metricRecorder = dtpInternalMetricRecorder;
        this.jobStore = jobStore;
    }

    public abstract void resetCopyIterationCounter();

    /**
     * Kicks off transfer job {@code jobId} from {@code exporter} to {@code importer}.
     */
    @Override
    public abstract void copy(
            AuthData exportAuthData,
            AuthData importAuthData,
            UUID jobId,
            Optional<ExportInformation> exportInfo)
            throws IOException, CopyException;

    @Override
    public Collection<ErrorDetail> getErrors(UUID jobId) {
        idempotentImportExecutor.setJobId(jobId);
        retryingIdempotentImportExecutor.setJobId(jobId);
        return idempotentImportExecutor.getErrors();
    }

    protected ExportResult<?> copyIteration(
            UUID jobId,
            AuthData exportAuthData,
            AuthData importAuthData,
            Optional<ExportInformation> exportInformation,
            String jobIdPrefix,
            int copyIteration)
            throws CopyException {
        monitor.debug(() -> jobIdPrefix + "Copy iteration: " + copyIteration);

        ExportResult<?> exportResult =
                exportIteration(jobId, exportAuthData, exportInformation, jobIdPrefix, copyIteration);

        DataModel exportedData = exportResult.getExportedData();
        if (exportedData != null) {
            PortabilityJob job = jobStore.findJob(jobId);
            TransferMode transferMode =
                    job.transferMode() == null ? TransferMode.DATA_TRANSFER : job.transferMode();
            switch (transferMode) {
                case DATA_TRANSFER:
                    importIteration(jobId, importAuthData, jobIdPrefix, copyIteration, exportedData);
                    break;
                case SIZE_CALCULATION:
                    sizeCalculationIteration(jobId, jobIdPrefix, exportedData);
                    break;
                default:
                    throw new IllegalStateException(
                            "Job mode " + transferMode.name() + " is not supported by "
                                    + getClass().getSimpleName());
            }
        }

        return exportResult;
    }

    private ExportResult<?> exportIteration(
            UUID jobId,
            AuthData exportAuthData,
            Optional<ExportInformation> exportInformation,
            String jobIdPrefix,
            int copyIteration)
            throws CopyException {

        monitor.debug(
                () -> jobIdPrefix + "Starting export, copy iteration: " + copyIteration,
                EventCode.COPIER_STARTED_EXPORT);

        CallableExporter callableExporter =
                new CallableExporter(
                        exporterProvider, jobId, exportAuthData, exportInformation, metricRecorder);
        RetryingCallable<ExportResult> retryingExporter =
                new RetryingCallable<>(
                        callableExporter,
                        retryStrategyLibraryProvider.get(),
                        Clock.systemUTC(),
                        monitor,
                        JobMetadata.getDataType(),
                        JobMetadata.getExportService());
        boolean exportSuccess = false;
        Stopwatch exportStopwatch = Stopwatch.createStarted();
        try {
            ExportResult<?> exportResult = retryingExporter.call();
            exportSuccess = exportResult.getType() != ExportResult.ResultType.ERROR;
            monitor.debug(
                    () -> jobIdPrefix + "Finished export, copy iteration: " + copyIteration,
                    EventCode.COPIER_FINISHED_EXPORT);
            return exportResult;
        } catch (RetryException | RuntimeException e) {
            throw convertToCopyException(jobIdPrefix, "export", e);
        } finally {
            metricRecorder.exportPageFinished(
                    JobMetadata.getDataType(), new ServiceResult(
                            JobMetadata.getExportService(),
                            exportSuccess,
                            exportStopwatch.elapsed())
            );
        }
    }

    private void importIteration(
            UUID jobId,
            AuthData importAuthData,
            String jobIdPrefix,
            int copyIteration,
            DataModel exportedData)
            throws CopyException {

        monitor.debug(
                () -> jobIdPrefix + "Starting import, copy iteration: " + copyIteration,
                EventCode.COPIER_STARTED_IMPORT);

        CallableImporter callableImporter =
                new CallableImporter(
                        importerProvider,
                        jobId,
                        idempotentImportExecutor,
                        importAuthData,
                        exportedData,
                        metricRecorder);
        RetryingCallable<ImportResult> retryingImporter =
                new RetryingCallable<>(
                        callableImporter,
                        retryStrategyLibraryProvider.get(),
                        Clock.systemUTC(),
                        monitor,
                        JobMetadata.getDataType(),
                        JobMetadata.getImportService());
        boolean importSuccess = false;
        Stopwatch importStopwatch = Stopwatch.createStarted();
        try {
            ImportResult importResult = retryingImporter.call();
            importSuccess = importResult.getType() == ImportResult.ResultType.OK;
            if (importSuccess) {
                try {
                    jobStore.addCounts(jobId, importResult.getCounts().orElse(null));
                    jobStore.addBytes(jobId, importResult.getBytes().orElse(null));
                } catch (IOException | DatastoreException e) {
                    monitor.debug(() -> jobIdPrefix + "Unable to add counts to job: ", e);
                }
            }
            monitor.debug(
                    () -> jobIdPrefix + "Finished import, copy iteration: " + copyIteration,
                    EventCode.COPIER_FINISHED_IMPORT);
        } catch (RetryException e) {
            if (!e.canSkip()) {
                throw convertToCopyException(jobIdPrefix, "import", e);
            }
        } catch (RuntimeException e) {
            throw convertToCopyException(jobIdPrefix, "import", e);
        } finally {
            metricRecorder.importPageFinished(
                    JobMetadata.getDataType(), new ServiceResult(
                            JobMetadata.getImportService(),
                            importSuccess,
                            importStopwatch.elapsed())
            );
        }
    }

    private void sizeCalculationIteration(UUID jobId, String jobIdPrefix,
                                          DataModel exportedData) throws CopyException {
        Collection<? extends DownloadableItem> items;
        if (exportedData instanceof PhotosContainerResource) {
            items = ((PhotosContainerResource) exportedData).getPhotos();
        } else if (exportedData instanceof VideosContainerResource) {
            items = ((VideosContainerResource) exportedData).getVideos();
        } else if (exportedData instanceof MediaContainerResource) {
            MediaContainerResource mcr = (MediaContainerResource) exportedData;
            List<DownloadableItem> list = new ArrayList<>(mcr.getVideos());
            list.addAll(mcr.getPhotos());
            items = list;
        } else {
            return;
        }

        CallableSizeCalculator callableSizeCalculator =
                new CallableSizeCalculator(jobId, new ConnectionProvider(jobStore), items);
        try {
            RetryingCallable<Map<String, Long>> retryingImporter =
                    new RetryingCallable<>(
                            callableSizeCalculator,
                            retryStrategyLibraryProvider.get(),
                            Clock.systemUTC(),
                            monitor,
                            JobMetadata.getDataType(),
                            JobMetadata.getImportService());
            jobStore.addBytes(jobId, retryingImporter.call());
        } catch (RetryException | RuntimeException e) {
            throw convertToCopyException(jobIdPrefix, "size estimation", e);
        }
    }

    private CopyException convertToCopyException(String jobIdPrefix, String suffix, Exception e) {
        if (e.getClass() == RetryException.class
                && CopyExceptionWithFailureReason.class.isAssignableFrom(e.getCause().getClass())) {
            return (CopyExceptionWithFailureReason) e.getCause();
        }
        return new CopyException(jobIdPrefix + "Error happened during " + suffix, e);
    }
}
