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

package com.google.android.apps.exposurenotification.keyupload;


import com.google.android.apps.exposurenotification.network.DiagnosisKey;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import androidx.annotation.Nullable;
import org.threeten.bp.LocalDate;

/**
 * A value class to carry data through the diagnosis verification and key upload flow, from start to
 * finish.
 *
 * <p>We use this class for both inputs and outputs of the {@link UploadController} because the
 * verification+upload flow and its parameters may change frequently, and this request/response
 * object makes it easy to alter/extend the parameters and return values without changing method
 * signatures.
 */
@AutoValue
public abstract class Upload {

  public abstract String verificationCode();

  @Nullable public abstract ImmutableList<DiagnosisKey> keys();

  @Nullable public abstract String homeRegion();

  @Nullable public abstract ImmutableList<String> regions();

  @Nullable public abstract String longTermToken();

  @Nullable public abstract String testType();

  @Nullable public abstract String hmacKeyBase64();

  @Nullable public abstract String certificate();

  @Nullable public abstract LocalDate symptomOnset();

  @Nullable public abstract LocalDate diagnosisDate();

  @Nullable public abstract String revisionToken();

  @Nullable public abstract String nonceBase64();

  public abstract boolean hasTraveled();

  /**
   * The keyserver reports back how many of the keys we uploaded result in a change, either adding
   * a new key or revising one it already had.
   */
  public abstract int numKeysAffected();

  public abstract boolean isCoverTraffic();

  /**
   * Every {@link Upload} starts with these three givens.
   */
  public static Upload.Builder newBuilder(
      List<DiagnosisKey> keys,
      String verificationCode,
      String hmacKeyBase64) {
    return new AutoValue_Upload.Builder()
        .setVerificationCode(verificationCode)
        .setKeys(ImmutableList.copyOf(keys))
        .setHmacKeyBase64(hmacKeyBase64)
        .setIsCoverTraffic(false)
        .setNumKeysAffected(0)
        .setRegions(ImmutableList.of())
        .setHasTraveled(false);
  }

  /**
   * Every {@link Upload} starts with these three givens.
   */
  public static Upload.Builder newBuilder(
      String verificationCode,
      String hmacKeyBase64) {
    return newBuilder(ImmutableList.of(), verificationCode, hmacKeyBase64);
  }

  public abstract Upload.Builder toBuilder();

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Upload.Builder setVerificationCode(String code);

    public abstract Upload.Builder setKeys(Collection<DiagnosisKey> keys);

    public abstract Upload.Builder setHomeRegion(String region);

    public abstract Upload.Builder setRegions(Collection<String> regions);

    public abstract Upload.Builder setTestType(String type);

    public abstract Upload.Builder setLongTermToken(String token);

    public abstract Upload.Builder setHmacKeyBase64(String key);

    public abstract Upload.Builder setCertificate(String cert);

    public abstract Upload.Builder setSymptomOnset(LocalDate date);

    public abstract Upload.Builder setDiagnosisDate(LocalDate date);

    public abstract Upload.Builder setIsCoverTraffic(boolean isFake);

    public abstract Upload.Builder setRevisionToken(String revisionToken);

    public abstract Upload.Builder setNumKeysAffected(int numKeysAffected);

    public abstract Upload.Builder setHasTraveled(boolean hasTraveled);

    public abstract Upload.Builder setNonceBase64(String nonce);

    public abstract Upload build();
  }

}
