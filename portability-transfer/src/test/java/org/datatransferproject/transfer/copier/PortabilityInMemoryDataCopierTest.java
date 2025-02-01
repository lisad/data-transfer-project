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

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.Optional;
import java.util.UUID;
import org.datatransferproject.api.launcher.Monitor;
import org.datatransferproject.copier.stack.PortabilityStackInMemoryDataCopier;
import org.datatransferproject.spi.cloud.storage.JobStore;
import org.datatransferproject.spi.transfer.provider.ExportResult;
import org.datatransferproject.spi.transfer.types.ContinuationData;
import org.datatransferproject.spi.transfer.types.CopyException;
import org.datatransferproject.test.types.FakeIdempotentImportExecutor;
import org.datatransferproject.types.common.ExportInformation;
import org.datatransferproject.types.common.PaginationData;
import org.datatransferproject.types.common.models.ContainerResource;
import org.datatransferproject.types.transfer.auth.AuthData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.mockito.Mockito;

public class PortabilityInMemoryDataCopierTest {

  private UUID jobId;
  private String jobIdPrefix;
  private ExportInformation exportInfo;
  private ExportResult<?> initialExportResult;
  private ContinuationData continuationData;
  private AuthData exportAuthData;
  private AuthData importAuthData;
  private PortabilityStackInMemoryDataCopier stackInMemoryDataCopier;

  private static class TestPortabilityInMemoryDataCopier extends PortabilityInMemoryDataCopier {

    public TestPortabilityInMemoryDataCopier() {
      super(
          null,
          null,
          null,
          Mockito.mock(Monitor.class),
          new FakeIdempotentImportExecutor(),
          new FakeIdempotentImportExecutor(),
          null,
          null);
    }

    protected ExportResult<?> copyIteration(
        UUID jobId,
        AuthData exportAuthData,
        AuthData importAuthData,
        Optional<ExportInformation> exportInformation,
        String jobIdPrefix,
        int copyIteration)
        throws CopyException {
      return Mockito.mock(ExportResult.class);
    }
  }

  private static class TestPortabilityStackInMemoryDataCopier
      extends PortabilityStackInMemoryDataCopier {

    public TestPortabilityStackInMemoryDataCopier() {
      super(
          null,
          null,
          null,
          Mockito.mock(Monitor.class),
          new FakeIdempotentImportExecutor(),
          new FakeIdempotentImportExecutor(),
          null,
          (Mockito.mock(JobStore.class)));
    }

    protected ExportResult<?> copyIteration(
        UUID jobId,
        AuthData exportAuthData,
        AuthData importAuthData,
        Optional<ExportInformation> exportInformation,
        String jobIdPrefix,
        int copyIteration)
        throws CopyException {
      return Mockito.mock(ExportResult.class);
    }
  }

  public static Iterable<PortabilityAbstractInMemoryDataCopier> data() {
    return Arrays.asList(
        (new PortabilityAbstractInMemoryDataCopier[]{
            Mockito.spy(new TestPortabilityInMemoryDataCopier()),
            Mockito.spy(new TestPortabilityStackInMemoryDataCopier())
        }));
  }

  @BeforeEach
  public void setUp() throws CopyException, IOException {
    importAuthData = exportAuthData = Mockito.mock(AuthData.class);
    jobId = UUID.randomUUID();
    jobIdPrefix = "Job " + jobId + ": ";
    exportInfo = Mockito.mock(ExportInformation.class);
    initialExportResult = Mockito.mock(ExportResult.class);
    continuationData = Mockito.mock(ContinuationData.class);
    stackInMemoryDataCopier = Mockito.spy(new TestPortabilityStackInMemoryDataCopier());
    stackInMemoryDataCopier.resetCopyIterationCounter();
  }

  @ParameterizedTest
  @MethodSource("data")
  public void initialExportInfoIsNull(PortabilityAbstractInMemoryDataCopier inMemoryDataCopier)
      throws CopyException, IOException {
    inMemoryDataCopier.resetCopyIterationCounter();
    Optional<ExportInformation> initialExportInfo = Optional.empty();

    inMemoryDataCopier.copy(exportAuthData, importAuthData, jobId, initialExportInfo);

    Mockito.verify(inMemoryDataCopier)
        .copyIteration(jobId, exportAuthData, importAuthData, initialExportInfo, jobIdPrefix, 1);
  }

  @ParameterizedTest
  @MethodSource("data")
  public void continuationDataIsNull(PortabilityAbstractInMemoryDataCopier inMemoryDataCopier)
      throws CopyException, IOException {
    inMemoryDataCopier.resetCopyIterationCounter();
    Mockito.when(initialExportResult.getContinuationData()).thenReturn(null);
    Mockito.doReturn(initialExportResult)
        .when(inMemoryDataCopier)
        .copyIteration(
            jobId, exportAuthData, importAuthData, Optional.of(exportInfo), jobIdPrefix, 1);
    inMemoryDataCopier.copy(exportAuthData, importAuthData, jobId, Optional.of(exportInfo));
    Mockito.verify(inMemoryDataCopier)
        .copyIteration(
            jobId, exportAuthData, importAuthData, Optional.of(exportInfo), jobIdPrefix, 1);
  }

  @ParameterizedTest
  @MethodSource("data")
  public void continuationDataWithPaginationData(
      PortabilityAbstractInMemoryDataCopier inMemoryDataCopier) throws CopyException, IOException {
    inMemoryDataCopier.resetCopyIterationCounter();
    PaginationData paginationData = Mockito.mock(PaginationData.class);
    ExportInformation paginationExportInfo = new ExportInformation(paginationData, null);

    Mockito.when(continuationData.getPaginationData()).thenReturn(paginationData);
    Mockito.when(initialExportResult.getContinuationData()).thenReturn(continuationData);
    Mockito.doReturn(initialExportResult)
        .when(inMemoryDataCopier)
        .copyIteration(
            jobId, exportAuthData, importAuthData, Optional.of(exportInfo), jobIdPrefix, 1);

    inMemoryDataCopier.copy(exportAuthData, importAuthData, jobId, Optional.of(exportInfo));

    InOrder orderVerifier = Mockito.inOrder(inMemoryDataCopier);
    orderVerifier.verify(inMemoryDataCopier)
        .copyIteration(
            jobId, exportAuthData, importAuthData, Optional.of(exportInfo), jobIdPrefix, 1);
    orderVerifier.verify(inMemoryDataCopier)
        .copyIteration(
            jobId,
            exportAuthData,
            importAuthData,
            Optional.of(paginationExportInfo),
            jobIdPrefix,
            2);
  }

  @ParameterizedTest
  @MethodSource("data")
  public void continuationDataWithEmptySubResourceList(
      PortabilityAbstractInMemoryDataCopier inMemoryDataCopier) throws CopyException, IOException {
    inMemoryDataCopier.resetCopyIterationCounter();
    Mockito.when(continuationData.getContainerResources()).thenReturn(new ArrayList<>());
    Mockito.when(initialExportResult.getContinuationData()).thenReturn(continuationData);
    Mockito.doReturn(initialExportResult)
        .when(inMemoryDataCopier)
        .copyIteration(
            jobId, exportAuthData, importAuthData, Optional.of(exportInfo), jobIdPrefix, 1);

    inMemoryDataCopier.copy(exportAuthData, importAuthData, jobId, Optional.of(exportInfo));

    Mockito.verify(inMemoryDataCopier)
        .copyIteration(
            jobId, exportAuthData, importAuthData, Optional.of(exportInfo), jobIdPrefix, 1);
  }

  @ParameterizedTest
  @MethodSource("data")
  public void continuationDataWithSingleSubResource(
      PortabilityAbstractInMemoryDataCopier inMemoryDataCopier) throws CopyException, IOException {
    inMemoryDataCopier.resetCopyIterationCounter();
    ContainerResource subResource = Mockito.mock(ContainerResource.class);

    ExportInformation subResourceExportInfo = new ExportInformation(null, subResource);

    Mockito.when(continuationData.getContainerResources()).thenReturn(Arrays.asList(subResource));
    Mockito.when(initialExportResult.getContinuationData()).thenReturn(continuationData);
    Mockito.doReturn(initialExportResult)
        .when(inMemoryDataCopier)
        .copyIteration(
            jobId, exportAuthData, importAuthData, Optional.of(exportInfo), jobIdPrefix, 1);

    inMemoryDataCopier.copy(exportAuthData, importAuthData, jobId, Optional.of(exportInfo));

    InOrder orderVerifier = Mockito.inOrder(inMemoryDataCopier);
    orderVerifier.verify(inMemoryDataCopier)
        .copyIteration(
            jobId, exportAuthData, importAuthData, Optional.of(exportInfo), jobIdPrefix, 1);
    orderVerifier.verify(inMemoryDataCopier)
        .copyIteration(
            jobId,
            exportAuthData,
            importAuthData,
            Optional.of(subResourceExportInfo),
            jobIdPrefix,
            2);
  }

  @ParameterizedTest
  @MethodSource("data")
  public void continuationDataWithMultipleSubResources(
      PortabilityAbstractInMemoryDataCopier inMemoryDataCopier) throws CopyException, IOException {
    inMemoryDataCopier.resetCopyIterationCounter();
    ContainerResource subResource1 = Mockito.mock(ContainerResource.class);
    ContainerResource subResource2 = Mockito.mock(ContainerResource.class);

    ExportInformation subResource1ExportInfo = new ExportInformation(null, subResource1);
    ExportInformation subResource2ExportInfo = new ExportInformation(null, subResource2);

    Mockito.when(continuationData.getContainerResources())
        .thenReturn(Arrays.asList(subResource1, subResource2));
    Mockito.when(initialExportResult.getContinuationData()).thenReturn(continuationData);
    Mockito.doReturn(initialExportResult)
        .when(inMemoryDataCopier)
        .copyIteration(
            jobId, exportAuthData, importAuthData, Optional.of(exportInfo), jobIdPrefix, 1);

    inMemoryDataCopier.copy(exportAuthData, importAuthData, jobId, Optional.of(exportInfo));

    InOrder orderVerifier = Mockito.inOrder(inMemoryDataCopier);
    orderVerifier.verify(inMemoryDataCopier)
        .copyIteration(
            jobId, exportAuthData, importAuthData, Optional.of(exportInfo), jobIdPrefix, 1);
    orderVerifier.verify(inMemoryDataCopier)
        .copyIteration(
            jobId,
            exportAuthData,
            importAuthData,
            Optional.of(subResource1ExportInfo),
            jobIdPrefix,
            2);
    orderVerifier.verify(inMemoryDataCopier)
        .copyIteration(
            jobId,
            exportAuthData,
            importAuthData,
            Optional.of(subResource2ExportInfo),
            jobIdPrefix,
            3);
  }

  @ParameterizedTest
  @MethodSource("data")
  public void continuationDataWithPaginationDataAndMultipleSubResources(
      PortabilityAbstractInMemoryDataCopier inMemoryDataCopier)
      throws CopyException, IOException {
    inMemoryDataCopier.resetCopyIterationCounter();
    PaginationData paginationData = Mockito.mock(PaginationData.class);
    ContainerResource subResource1 = Mockito.mock(ContainerResource.class);
    ContainerResource subResource2 = Mockito.mock(ContainerResource.class);

    ExportInformation paginationExportInfo = new ExportInformation(paginationData, null);
    ExportInformation subResource1ExportInfo = new ExportInformation(null, subResource1);
    ExportInformation subResource2ExportInfo = new ExportInformation(null, subResource2);

    Mockito.when(continuationData.getPaginationData()).thenReturn(paginationData);
    Mockito.when(continuationData.getContainerResources())
        .thenReturn(Arrays.asList(subResource1, subResource2));
    Mockito.when(initialExportResult.getContinuationData()).thenReturn(continuationData);
    Mockito.doReturn(initialExportResult)
        .when(inMemoryDataCopier)
        .copyIteration(
            jobId, exportAuthData, importAuthData, Optional.of(exportInfo), jobIdPrefix, 1);

    inMemoryDataCopier.copy(exportAuthData, importAuthData, jobId, Optional.of(exportInfo));

    InOrder orderVerifier = Mockito.inOrder(inMemoryDataCopier);
    orderVerifier.verify(inMemoryDataCopier)
        .copyIteration(
            jobId, exportAuthData, importAuthData, Optional.of(exportInfo), jobIdPrefix, 1);
    orderVerifier.verify(inMemoryDataCopier)
        .copyIteration(
            jobId,
            exportAuthData,
            importAuthData,
            Optional.of(paginationExportInfo),
            jobIdPrefix,
            2);
    orderVerifier.verify(inMemoryDataCopier)
        .copyIteration(
            jobId,
            exportAuthData,
            importAuthData,
            Optional.of(subResource1ExportInfo),
            jobIdPrefix,
            3);
    orderVerifier.verify(inMemoryDataCopier)
        .copyIteration(
            jobId,
            exportAuthData,
            importAuthData,
            Optional.of(subResource2ExportInfo),
            jobIdPrefix,
            4);
  }

  @ParameterizedTest
  @MethodSource("data")
  public void continuationDataWithPaginationDataAndNestedSubResource(
      PortabilityAbstractInMemoryDataCopier inMemoryDataCopier)
      throws CopyException, IOException {
    inMemoryDataCopier.resetCopyIterationCounter();
    PaginationData paginationData = Mockito.mock(PaginationData.class);
    ContinuationData paginationContinuationData = Mockito.mock(ContinuationData.class);
    ExportResult<?> paginationExportResult = Mockito.mock(ExportResult.class);

    ContainerResource subResource = Mockito.mock(ContainerResource.class);
    ContainerResource paginationSubResource = Mockito.mock(ContainerResource.class);

    ExportInformation paginationExportInfo = new ExportInformation(paginationData, null);
    ExportInformation subResourceExportInfo = new ExportInformation(null, subResource);
    ExportInformation paginationSubResourceExportInfo =
        new ExportInformation(null, paginationSubResource);

    Mockito.when(continuationData.getPaginationData()).thenReturn(paginationData);
    Mockito.when(continuationData.getContainerResources()).thenReturn(Arrays.asList(subResource));
    Mockito.when(paginationContinuationData.getContainerResources())
        .thenReturn(Arrays.asList(paginationSubResource));

    Mockito.when(initialExportResult.getContinuationData()).thenReturn(continuationData);
    Mockito.when(paginationExportResult.getContinuationData())
        .thenReturn(paginationContinuationData);

    Mockito.doReturn(initialExportResult)
        .when(inMemoryDataCopier)
        .copyIteration(
            jobId, exportAuthData, importAuthData, Optional.of(exportInfo), jobIdPrefix, 1);
    Mockito.doReturn(paginationExportResult)
        .when(inMemoryDataCopier)
        .copyIteration(
            jobId,
            exportAuthData,
            importAuthData,
            Optional.of(paginationExportInfo),
            jobIdPrefix,
            2);

    inMemoryDataCopier.copy(exportAuthData, importAuthData, jobId, Optional.of(exportInfo));

    InOrder orderVerifier = Mockito.inOrder(inMemoryDataCopier);
    orderVerifier.verify(inMemoryDataCopier)
        .copyIteration(
            jobId, exportAuthData, importAuthData, Optional.of(exportInfo), jobIdPrefix, 1);
    orderVerifier.verify(inMemoryDataCopier)
        .copyIteration(
            jobId,
            exportAuthData,
            importAuthData,
            Optional.of(paginationExportInfo),
            jobIdPrefix,
            2);
    orderVerifier.verify(inMemoryDataCopier)
        .copyIteration(
            jobId,
            exportAuthData,
            importAuthData,
            Optional.of(paginationSubResourceExportInfo),
            jobIdPrefix,
            3);
    orderVerifier.verify(inMemoryDataCopier)
        .copyIteration(
            jobId,
            exportAuthData,
            importAuthData,
            Optional.of(subResourceExportInfo),
            jobIdPrefix,
            4);
  }

  @Test
  public void loadStackFromJobStoreAtStartOfTransfer() throws CopyException, IOException {

    stackInMemoryDataCopier.copy(exportAuthData, importAuthData, jobId, Optional.of(exportInfo));

    Mockito.verify(stackInMemoryDataCopier.jobStore).loadJobStack(jobId);
  }

  @Test
  public void storeStackContainingPaginationDataAndMultipleSubResourcesInJobStore()
      throws CopyException, IOException {

    PaginationData paginationData = Mockito.mock(PaginationData.class);
    ContainerResource subResource1 = Mockito.mock(ContainerResource.class);
    ContainerResource subResource2 = Mockito.mock(ContainerResource.class);

    ExportInformation paginationExportInfo = new ExportInformation(paginationData, null);
    ExportInformation subResource1ExportInfo = new ExportInformation(null, subResource1);
    ExportInformation subResource2ExportInfo = new ExportInformation(null, subResource2);

    Deque<ExportInformation> jobStack = new ArrayDeque<>();
    jobStack.addLast(subResource2ExportInfo);
    jobStack.addLast(subResource1ExportInfo);
    jobStack.addLast(paginationExportInfo);

    Mockito.when(continuationData.getPaginationData()).thenReturn(paginationData);
    Mockito.when(continuationData.getContainerResources())
        .thenReturn(Arrays.asList(subResource1, subResource2));
    Mockito.when(initialExportResult.getContinuationData()).thenReturn(continuationData);
    Mockito.doReturn(initialExportResult)
        .when(stackInMemoryDataCopier)
        .copyIteration(
            jobId, exportAuthData, importAuthData, Optional.of(exportInfo), jobIdPrefix, 1);

    stackInMemoryDataCopier.copy(exportAuthData, importAuthData, jobId, Optional.of(exportInfo));

    InOrder orderVerifier = Mockito.inOrder(stackInMemoryDataCopier.jobStore);
    while (!jobStack.isEmpty()) {
      orderVerifier.verify(stackInMemoryDataCopier.jobStore)
          .storeJobStack(eq(jobId), argThat(s -> dequeEquals(s, jobStack)));
      jobStack.removeLast();
    }
    orderVerifier.verify(stackInMemoryDataCopier.jobStore)
        .storeJobStack(eq(jobId), argThat(Collection::isEmpty));
  }

  private boolean dequeEquals(Deque<ExportInformation> d1, Deque<ExportInformation> d2) {
    return Arrays.equals(d1.toArray(), d2.toArray());
  }

  @Test
  public void doNotPerformAdditionalCopyingIfLoadingEmptyStackFromJobStore()
      throws CopyException, IOException {

    Mockito.when(stackInMemoryDataCopier.jobStore.loadJobStack(jobId))
        .thenReturn(Optional.of(new ArrayDeque<>()));

    stackInMemoryDataCopier.copy(exportAuthData, importAuthData, jobId, Optional.of(exportInfo));

    Mockito.verify(stackInMemoryDataCopier, Mockito.never())
        .copyIteration(
            Mockito.any(UUID.class),
            Mockito.any(AuthData.class),
            Mockito.any(AuthData.class),
            Mockito.any(Optional.class),
            Mockito.anyString(),
            Mockito.anyInt());
  }
}
