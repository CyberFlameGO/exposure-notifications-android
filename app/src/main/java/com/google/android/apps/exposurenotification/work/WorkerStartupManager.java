/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.google.android.apps.exposurenotification.work;

import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.ScheduledExecutor;
import com.google.android.apps.exposurenotification.common.TaskToFutureAdapter;
import com.google.android.apps.exposurenotification.common.logging.Logger;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.nearby.PackageConfigurationHelper;
import com.google.android.apps.exposurenotification.storage.ExposureCheckRepository;
import com.google.android.apps.exposurenotification.storage.VerificationCodeRequestRepository;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import org.threeten.bp.Duration;

/**
 * Helper methods that should be used by all periodic workers to check the API is enabled and
 * perform some routine tasks.
 *
 * <pre>{@code
 *    public ListenableFuture<Result> startWork() {
 *       return FluentFuture.from(workerStartupManager.getIsEnabledWithStartupTasks())
 *           .transformAsync(
 *               (isEnabled) -> {
 *                 // Only continue if it is enabled.
 *                 if (isEnabled) {
 *                   return continueWorkFuture();
 *                 } else {
 *                   // Stop here because things aren't enabled. Will still return successful though.
 *                   return Futures.immediateFailedFuture(new NotEnabledException());
 *                 }
 *               }, backgroundExecutor)
 *           ... continue the work flow
 *           .catching(
 *                 NotEnabledException.class,
 *                 x -> {
 *                   // Not enabled. Return as success.
 *                   return Result.success();
 *                 },
 *                 backgroundExecutor);
 *     }
 * }</pre>
 */
public class WorkerStartupManager {

  private static final Logger logger = Logger.getLogger("WorkerStartupManager");

  private static final Duration IS_ENABLED_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration GET_PACKAGE_CONFIGURATION_TIMEOUT = Duration.ofSeconds(10);
  // Used to determine the threshold beyond which we mark the exposure checks as obsolete.
  // Currently, all exposure checks captured earlier than two weeks ago are deemed obsolete.
  @VisibleForTesting static final Duration EXPOSURE_CHECK_MAX_AGE = Duration.ofDays(14);
  // Used to determine the threshold beyond which we mark the requests for a verification code
  // as obsolete. Currently, all verification code requests made earlier than thirty days ago are
  // deemed obsolete.
  @VisibleForTesting static final Duration VERIFICATION_CODE_REQUEST_MAX_AGE = Duration.ofDays(30);

  private final ExposureNotificationClientWrapper exposureNotificationClientWrapper;
  private final ExecutorService backgroundExecutor;
  private final ScheduledExecutorService scheduledExecutor;
  private final PackageConfigurationHelper packageConfigurationHelper;
  private final ExposureCheckRepository exposureCheckRepo;
  private final VerificationCodeRequestRepository verificationCodeRequestRepo;
  private final Clock clock;

  @Inject
  public WorkerStartupManager(
      ExposureNotificationClientWrapper exposureNotificationClientWrapper,
      @BackgroundExecutor ExecutorService backgroundExecutor,
      @ScheduledExecutor ScheduledExecutorService scheduledExecutor,
      PackageConfigurationHelper packageConfigurationHelper,
      ExposureCheckRepository exposureCheckRepo,
      VerificationCodeRequestRepository verificationCodeRequestRepo,
      Clock clock) {
    this.exposureNotificationClientWrapper = exposureNotificationClientWrapper;
    this.backgroundExecutor = backgroundExecutor;
    this.scheduledExecutor = scheduledExecutor;
    this.packageConfigurationHelper = packageConfigurationHelper;
    this.exposureCheckRepo = exposureCheckRepo;
    this.verificationCodeRequestRepo = verificationCodeRequestRepo;
    this.clock = clock;
  }

  /**
   * Checks if the API isEnabled. If so, performs some startup tasks then returns true once done,
   * otherwise immediately returns false.
   *
   * <p> Also deletes obsolete exposure checks and verification code requests and resets nonces for
   * expired verification code requests, if any.
   */
  public ListenableFuture<Boolean> getIsEnabledWithStartupTasks() {
    return FluentFuture.from(TaskToFutureAdapter.getFutureWithTimeout(
        exposureNotificationClientWrapper.isEnabled(),
        IS_ENABLED_TIMEOUT,
        scheduledExecutor))
        .transformAsync(isEnabled -> {
          deleteObsoletesAndResetValues();
          if (isEnabled) {
            return maybeUpdatePackageConfigurationState().transform(v -> true, backgroundExecutor);
          } else {
            return Futures.immediateFuture(false);
          }
        }, backgroundExecutor)
        .catchingAsync(Exception.class, e -> {
          deleteObsoletesAndResetValues();
          return Futures.immediateFailedFuture(e);
        }, backgroundExecutor);
  }

  @WorkerThread
  private void deleteObsoletesAndResetValues() {
    // Delete obsolete exposure checks.
    exposureCheckRepo.deleteObsoleteChecksIfAny(clock.now().minus(EXPOSURE_CHECK_MAX_AGE));
    // Delete obsolete requests for a verification code.
    verificationCodeRequestRepo.deleteObsoleteRequestsIfAny(
        clock.now().minus(VERIFICATION_CODE_REQUEST_MAX_AGE));
    // Reset nonces for expired requests for a verification code.
    verificationCodeRequestRepo.resetNonceForExpiredRequestsIfAny(clock.now());
  }

  private FluentFuture<Void> maybeUpdatePackageConfigurationState() {
    return FluentFuture.from(TaskToFutureAdapter.getFutureWithTimeout(
        exposureNotificationClientWrapper.getPackageConfiguration(),
        GET_PACKAGE_CONFIGURATION_TIMEOUT,
        scheduledExecutor))
        .transformAsync(packageConfiguration -> {
          packageConfigurationHelper.maybeUpdateAnalyticsState(packageConfiguration);
          packageConfigurationHelper.maybeUpdateSmsNoticeState(packageConfiguration);
          return Futures.immediateVoidFuture();
        }, backgroundExecutor)
        .catchingAsync(Exception.class, t -> {
          logger.e("Unable to update app analytics state", t);
          return Futures.immediateVoidFuture();
        }, backgroundExecutor);
  }

}
