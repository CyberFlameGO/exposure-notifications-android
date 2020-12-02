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

package com.google.android.apps.exposurenotification.settings;

import androidx.hilt.lifecycle.ViewModelInject;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;

/**
 * View model for {@link PrivateAnalyticsActivity}.
 */
public class PrivateAnalyticsViewModel extends ViewModel {

  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  @ViewModelInject
  public PrivateAnalyticsViewModel(
      ExposureNotificationSharedPreferences exposureNotificationSharedPreferences) {
    this.exposureNotificationSharedPreferences = exposureNotificationSharedPreferences;
  }

  public void setPrivateAnalyticsState(boolean isEnabled) {
    // Reset metrics on state changes
    exposureNotificationSharedPreferences.clearLastShownExposureNotification();
    exposureNotificationSharedPreferences.clearLastShownExposureNotificationInteraction();

    exposureNotificationSharedPreferences.setPrivateAnalyticsState(isEnabled);
  }

  public LiveData<Boolean> getPrivateAnalyticsLiveData() {
    return exposureNotificationSharedPreferences.getPrivateAnalyticsStateLiveData();
  }
}
