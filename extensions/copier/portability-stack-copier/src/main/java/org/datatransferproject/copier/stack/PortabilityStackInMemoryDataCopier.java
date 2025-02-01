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

package org.datatransferproject.copier.stack;

import com.google.inject.Provider;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import org.datatransferproject.api.launcher.DtpInternalMetricRecorder;
import org.datatransferproject.api.launcher.Monitor;
import org.datatransferproject.spi.cloud.storage.JobStore;
import org.datatransferproject.spi.transfer.idempotentexecutor.IdempotentImportExecutor;
import org.datatransferproject.spi.transfer.provider.ExportResult;
import org.datatransferproject.spi.transfer.provider.Exporter;
import org.datatransferproject.spi.transfer.provider.Importer;
import org.datatransferproject.spi.transfer.types.ContinuationData;
import org.datatransferproject.spi.transfer.types.CopyException;
import org.datatransferproject.transfer.Annotations;
import org.datatransferproject.transfer.copier.InMemoryDataCopier;
import org.datatransferproject.transfer.copier.PortabilityAbstractInMemoryDataCopier;
import org.datatransferproject.types.common.ExportInformation;
import org.datatransferproject.types.common.models.ContainerResource;
import org.datatransferproject.types.transfer.auth.AuthData;
import org.datatransferproject.types.transfer.retry.RetryStrategyLibrary;

/**
 * Implementation of {@link InMemoryDataCopier}.
 */
public class PortabilityStackInMemoryDataCopier extends PortabilityAbstractInMemoryDataCopier {

  private static final AtomicInteger COPY_ITERATION_COUNTER = new AtomicInteger();

  private Deque<ExportInformation> exportInfoStack = new ArrayDeque<>();

  @Inject
  public PortabilityStackInMemoryDataCopier(
      Provider<Exporter> exporterProvider,
      Provider<Importer> importerProvider,
      Provider<RetryStrategyLibrary> retryStrategyLibraryProvider,
      Monitor monitor,
      IdempotentImportExecutor idempotentImportExecutor,
      @Annotations.RetryingExecutor IdempotentImportExecutor retryingIdempotentImportExecutor,
      DtpInternalMetricRecorder dtpInternalMetricRecorder,
      JobStore jobStore) {
    super(
        exporterProvider,
        importerProvider,
        retryStrategyLibraryProvider,
        monitor,
        idempotentImportExecutor,
        retryingIdempotentImportExecutor,
        dtpInternalMetricRecorder,
        jobStore);
  }

  @Override
  public void resetCopyIterationCounter() {
    COPY_ITERATION_COUNTER.set(0);
  }

  /**
   * Transfers data from the given {@code exporter} optionally starting at the point specified in
   * the provided {@code exportInformation}. Imports the data using the provided {@code importer}.
   * If there is more data to required to be exported, recursively copies using the specific {@link
   * ExportInformation} to continue the process.
   *
   * @param exportAuthData The auth data for the export
   * @param importAuthData The auth data for the import
   * @param exportInfo     Any pagination or resource information to use for subsequent calls.
   */
  @Override
  public void copy(
      AuthData exportAuthData,
      AuthData importAuthData,
      UUID jobId,
      Optional<ExportInformation> exportInfo)
      throws CopyException {
    idempotentImportExecutor.setJobId(jobId);
    String jobIdPrefix = "Job " + jobId + ": ";

    Optional<Deque<ExportInformation>> maybeLoadedStack = jobStore.loadJobStack(jobId);

    if (maybeLoadedStack.isPresent()) {
      // load stack from partially completed transfer
      exportInfoStack = maybeLoadedStack.get();
    } else {
      copyAndUpdateStack(exportAuthData, importAuthData, jobId, jobIdPrefix, exportInfo);
    }

    while (!exportInfoStack.isEmpty()) {
      Optional<ExportInformation> nextInfo = Optional.of(exportInfoStack.removeLast());
      copyAndUpdateStack(exportAuthData, importAuthData, jobId, jobIdPrefix, nextInfo);
    }
  }

  private void copyAndUpdateStack(AuthData exportAuthData, AuthData importAuthData, UUID jobId,
      String jobIdPrefix, Optional<ExportInformation> exportInfo) throws CopyException {
    int copyIteration = COPY_ITERATION_COUNTER.incrementAndGet();
    ExportResult<?> exportResult =
        copyIteration(
            jobId,
            exportAuthData,
            importAuthData,
            exportInfo,
            jobIdPrefix,
            copyIteration);
    // Import and Export were successful, determine what to do next
    updateStackAfterCopyIteration(
        jobId,
        jobIdPrefix,
        exportInfo.map(ExportInformation::getContainerResource).orElse(null),
        copyIteration,
        exportResult.getContinuationData());
  }

  private void updateStackAfterCopyIteration(
      UUID jobId,
      String jobIdPrefix,
      ContainerResource exportContainerResource,
      int copyIteration,
      ContinuationData continuationData) {

    // NOTE: order is important below: we process next page before sub-resources, so we push them
    // on the stack in reverse order.

    if (null != continuationData) {
      // Start processing sub-resources
      if (continuationData.getContainerResources() != null) {
        List<ContainerResource> subResources = continuationData.getContainerResources();
        for (int i = subResources.size() - 1; i >= 0; i--) {
          monitor.debug(
              () ->
                  jobIdPrefix
                      + "Pushing to the stack a new copy iteration with a new container resource, copy iteration: "
                      + copyIteration);
          exportInfoStack.addLast((new ExportInformation(null, subResources.get(i))));
        }
      }

      // Push the next page of items onto the stack
      if (null != continuationData.getPaginationData()) {
        monitor.debug(
            () ->
                jobIdPrefix
                    + "Pushing to the stack a new copy iteration with pagination info, copy iteration: "
                    + copyIteration);
        exportInfoStack.addLast(
            new ExportInformation(continuationData.getPaginationData(), exportContainerResource));
      }
    }
    jobStore.storeJobStack(jobId, new ArrayDeque<>(exportInfoStack));
  }
}
