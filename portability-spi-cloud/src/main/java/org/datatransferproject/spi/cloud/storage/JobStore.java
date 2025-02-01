package org.datatransferproject.spi.cloud.storage;

import java.io.IOException;
import java.util.Collection;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.datatransferproject.spi.cloud.types.JobAuthorization;
import org.datatransferproject.spi.cloud.types.PortabilityJob;
import org.datatransferproject.spi.cloud.types.PortabilityJob.State;
import org.datatransferproject.types.common.ExportInformation;
import org.datatransferproject.types.transfer.errors.ErrorDetail;

/**
 * A store for {@link PortabilityJob}s.
 *
 * <p>This class is intended to be implemented by extensions that support storage in various
 * back-end services.
 */
public interface JobStore extends TemporaryPerJobDataStore {
  /**
   * Inserts a new {@link PortabilityJob} keyed by {@code jobId} in the store.
   *
   * <p>To update an existing {@link PortabilityJob} instead, use {@link #update}.
   *
   * @throws IOException if a job already exists for {@code job}'s ID, or if there was a different
   *     problem inserting the job.
   */
  void createJob(UUID jobId, PortabilityJob job) throws IOException;

  /**
   * Called by a transfer worker to claim the job matching {@code jobId}, and updates the entry to
   * {@code job} to set the new state and auth public key. This should be atomic and not allow
   * multiple workers to claim the same job.
   *
   * @throws IOException if a job didn't already exist for {@code jobId} or there was a problem
   *     updating it
   * @throws IllegalStateException if fails to successfully claim the job.
   */
  void claimJob(UUID jobId, PortabilityJob job) throws IOException;

  /**
   * Update the jobs auth state to {@code JobAuthorization.State.CREDS_AVAILABLE} in the store. This
   * indicates to the pool of workers that this job is available for processing.
   */
  void updateJobAuthStateToCredsAvailable(UUID jobId) throws IOException;

  /**
   * Updates the job to the new version with keys added and the auth state to {@link
   * JobAuthorization.State#CREDS_STORED} state.
   */
  void updateJobWithCredentials(UUID jobId, PortabilityJob job) throws IOException;

  /**
   * Stores errors related to a transfer job.
   *
   * @throws IOException if a job didn't already exist for {@code jobId} or there was a problem
   *     updating it
   */
  void addErrorsToJob(UUID jobId, Collection<ErrorDetail> errors) throws IOException;

  /**
   * Stores a FailureReason related to a transfer job.
   *
   * @throws IOException if a job didn't already exist for {@code jobId} or there was a problem
   *     updating it
   */
  void addFailureReasonToJob(UUID jobId, String failureReason) throws IOException;

  /**
   * Updates a job to mark as finished.
   *
   * @param state The new state of the job. Can be {@code State.ERROR} or {@code State.COMPLETE}.
   * @throws IOException if unable to update the job
   */
  void markJobAsFinished(UUID jobId, State state) throws IOException;

  /**
   * Updates a job to mark as in progress.
   *
   * @throws IOException if unable to update the job
   */
  void markJobAsStarted(UUID jobId) throws IOException;

  /**
   * Called when a worker has been waiting for credentials for a job but has not received them and
   * timed out. The job should be set to {@code State.Error} and {@code
   * JobAuthorization.State.TIMED_OUT}.
   *
   * @throws IOException if unable to update the job
   */
  void markJobAsTimedOut(UUID jobId) throws IOException;

  /**
   * Removes the {@link PortabilityJob} in the store keyed by {@code jobId}.
   *
   * @throws IOException if the job doesn't exist, or there was a different problem deleting it.
   */
  void remove(UUID jobId) throws IOException;

  /**
   * Returns the job for the id or null if not found.
   *
   * @param jobId the job id
   */
  PortabilityJob findJob(UUID jobId);

  /**
   * Gets the ID of the first {@link PortabilityJob} in state {@code jobState} in the store, or null
   * if none found.
   */
  UUID findFirst(JobAuthorization.State jobState);

  /**
   * Updates the counter data.
   *
   * @param newCounts the new items counted
   */
  default void addCounts(UUID jobId, Map<String, Integer> newCounts) throws IOException {}

  /**
   * Provides the total number of items recorded.
   *
   * @return mapping from items names to items counts or null if none exist
   */
  default Map<String, Integer> getCounts(UUID jobId) {
    return null;
  }

  /**
   * Increments the bytes count for the job.
   *
   * @param bytes the size in bytes of new items
   */
  default void addBytes(UUID jobId, Long bytes) throws IOException {}

  /**
   * Increments the bytes count for downloadable items of the given job.
   *
   * @param bytes key is idempotent id of a DownloadableItem
   */
  default void addBytes(UUID jobId, Map<String, Long> bytes) {}

  /** Provides the total number of bytes transferred. */
  default Long getBytes(UUID jobId) {
    return null;
  }

  /**
   * Returns a stack of export information associated with outstanding job iterations - used to
   * resume job transfer.
   */
  default Optional<Deque<ExportInformation>> loadJobStack(UUID jobId) {
    return Optional.empty();
  }

  /**
   * Stores a stack of export information associated with the remaining copy iterations left in a job
   * transfer.
   */
  default void storeJobStack(UUID jobId, Deque<ExportInformation> stack) {}

  /**
   * Called by a transfer worker when abandoning the job matching {@code jobId}, and do cleanup at their end.
   * Accepts the {@code reason} for abandoning the job (can be derived from but not limited to {@link State})
   *
   * @throws IllegalStateException if failed to successfully abandon the job.
   */
  default void abandonJob(UUID jobId, String reason) {}
}
