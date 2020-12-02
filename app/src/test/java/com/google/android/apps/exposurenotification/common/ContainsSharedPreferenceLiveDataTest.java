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

package com.google.android.apps.exposurenotification.common;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
@Config(application = HiltTestApplication.class)
public class ContainsSharedPreferenceLiveDataTest {

  private static final String KEY = "testKey";

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();

  private Context context = ApplicationProvider.getApplicationContext();
  private SharedPreferences sharedPreferences;

  @Before
  public void setup() {
    sharedPreferences = context.getSharedPreferences("test", Context.MODE_PRIVATE);
  }

  @Test
  public void observeForever_defaultFalse() {
    ContainsSharedPreferenceLiveData containsSharedPreferenceLiveData =
        new ContainsSharedPreferenceLiveData(sharedPreferences, KEY);
    List<Boolean> values = new ArrayList<>();

    containsSharedPreferenceLiveData.observeForever(values::add);

    assertThat(values).containsExactly(false);
  }

  @Test
  public void observeForever_unsetThenSet_deliversInitalOnObserve() {
    ContainsSharedPreferenceLiveData containsSharedPreferenceLiveData =
        new ContainsSharedPreferenceLiveData(sharedPreferences, KEY);
    List<Boolean> values = new ArrayList<>();

    containsSharedPreferenceLiveData.observeForever(values::add);
    sharedPreferences.edit().putBoolean(KEY, true).commit();

    assertThat(values).containsExactly(false, true);
  }

  @Test
  public void observeForever_updatesThenRemoved_observesUpdatesThenRemoved() {
    ContainsSharedPreferenceLiveData containsSharedPreferenceLiveData =
        new ContainsSharedPreferenceLiveData(sharedPreferences, KEY);
    List<Boolean> values = new ArrayList<>();

    containsSharedPreferenceLiveData.observeForever(values::add);
    sharedPreferences.edit().putBoolean(KEY, true).commit();
    sharedPreferences.edit().putBoolean(KEY, false).commit();
    sharedPreferences.edit().remove(KEY).commit();

    assertThat(values).containsExactly(false, true, true, false);
  }
}