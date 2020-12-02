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

package com.google.android.apps.exposurenotification.debug;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.AbstractTextWatcher;
import com.google.android.apps.exposurenotification.common.KeyboardHelper;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.android.apps.exposurenotification.debug.TemporaryExposureKeyEncodingHelper.DecodeException;
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.common.io.BaseEncoding;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import dagger.hilt.android.AndroidEntryPoint;
import org.threeten.bp.Duration;

/**
 * Fragment for the provide tab in matching debug.
 */
@AndroidEntryPoint
public class ProvideMatchingFragment extends Fragment {

  private static final String TAG = "ProvideMatchingFragment";

  private static final BaseEncoding BASE16 = BaseEncoding.base16().lowerCase();

  private static final Duration INTERVAL_DURATION = Duration.ofMinutes(10);

  private ProvideMatchingViewModel provideMatchingViewModel;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_matching_provide, parent, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    provideMatchingViewModel =
        new ViewModelProvider(this, getDefaultViewModelProviderFactory())
            .get(ProvideMatchingViewModel.class);

    provideMatchingViewModel
        .getSnackbarLiveEvent()
        .observe(getViewLifecycleOwner(), this::maybeShowSnackbar);

    // Submit section
    MaterialButton provideButton = view.findViewById(R.id.provide_button);
    provideButton.setOnClickListener(
        (v) -> {
          KeyboardHelper.maybeHideKeyboard(getContext(), view);
          provideMatchingViewModel.provideSingleAction();
        });

    // Single
    EditText inputSingleKey = view.findViewById(R.id.input_single_key);
    inputSingleKey.addTextChangedListener(
        new AbstractTextWatcher() {
          @Override
          public void afterTextChanged(Editable s) {
            if (!TextUtils.isEmpty(s.toString())) {
              provideMatchingViewModel.setSingleInputKey(s.toString());
            }
          }
        });

    Button inputSingleScanButton = view.findViewById(R.id.scan_button);
    inputSingleScanButton.setOnClickListener(
        v -> IntentIntegrator.forSupportFragment(this)
            .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            .setOrientationLocked(false)
            .setBarcodeImageEnabled(false).initiateScan());

    TextInputLayout inputSingleIntervalNumberLayout = view
        .findViewById(R.id.input_single_interval_number_layout);
    TextInputEditText inputSingleIntervalNumber = view
        .findViewById(R.id.input_single_interval_number);
    inputSingleIntervalNumber.addTextChangedListener(
        new AbstractTextWatcher() {
          @Override
          public void afterTextChanged(Editable s) {
            if (!TextUtils.isEmpty(s.toString())) {
              provideMatchingViewModel.setSingleInputIntervalNumber(tryParseInteger(s.toString()));
            } else {
              provideMatchingViewModel.setSingleInputIntervalNumber(0);
            }
          }
        });

    provideMatchingViewModel.getSingleInputIntervalNumberLiveData().observe(getViewLifecycleOwner(),
        intervalNumber -> {
          if (intervalNumber == 0) {
            inputSingleIntervalNumberLayout.setSuffixText("");
          } else {
            inputSingleIntervalNumberLayout.setSuffixText(StringUtils
                .epochTimestampToLongUTCDateTimeString(
                    intervalNumber * INTERVAL_DURATION.toMillis(),
                    requireContext().getResources().getConfiguration().locale));
          }
        });

    EditText inputSingleRollingPeriod = view.findViewById(R.id.input_single_rolling_period);
    inputSingleRollingPeriod.addTextChangedListener(
        new AbstractTextWatcher() {
          @Override
          public void afterTextChanged(Editable s) {
            if (!TextUtils.isEmpty(s.toString())) {
              provideMatchingViewModel.setSingleInputRollingPeriod(tryParseInteger(s.toString()));
            }
          }
        });

    EditText inputSingleTransmissionRiskLevel =
        view.findViewById(R.id.input_single_transmission_risk_level);
    inputSingleTransmissionRiskLevel.addTextChangedListener(
        new AbstractTextWatcher() {
          @Override
          public void afterTextChanged(Editable s) {
            if (!TextUtils.isEmpty(s.toString())) {
              provideMatchingViewModel.setSingleInputTransmissionRiskLevel(
                  tryParseInteger(s.toString()));
            }
          }
        });

    provideMatchingViewModel
        .getSigningKeyInfoLiveData()
        .observe(
            getViewLifecycleOwner(),
            keyInfo -> {
              TextView signaturePublicKey = view.findViewById(R.id.keyfile_signature_public_key);
              TextView signaturePackage = view.findViewById(R.id.keyfile_signature_package_name);
              TextView signatureId = view.findViewById(R.id.keyfile_signature_id);
              TextView signatureVersion = view.findViewById(R.id.keyfile_signature_version);
              setTextAndCopyAction(signaturePublicKey, keyInfo.publicKeyBase64());
              setTextAndCopyAction(signaturePackage, keyInfo.packageName());
              setTextAndCopyAction(signatureId, keyInfo.keyId());
              setTextAndCopyAction(signatureVersion, keyInfo.keyVersion());
            });
  }

  private void setTextAndCopyAction(TextView view, String text) {
    view.setText(text);
    view.setOnClickListener(
        v -> {
          ClipboardManager clipboard =
              (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
          ClipData clip = ClipData.newPlainText(text, text);
          clipboard.setPrimaryClip(clip);
          Snackbar.make(
              v,
              getString(
                  R.string.debug_snackbar_copied_text,
                  StringUtils.truncateWithEllipsis(text, 35)),
              Snackbar.LENGTH_SHORT)
              .show();
        });
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == IntentIntegrator.REQUEST_CODE) {
      Log.d(TAG, "onActivityResult with requestCode=IntentIntegrator.REQUEST_CODE");
      IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
      if (result != null && result.getContents() != null) {
        try {
          TemporaryExposureKey temporaryExposureKey =
              TemporaryExposureKeyEncodingHelper.decodeSingle(result.getContents());

          EditText key = requireView().findViewById(R.id.input_single_key);
          EditText interValNumber = requireView().findViewById(R.id.input_single_interval_number);
          EditText rollingPeriod = requireView().findViewById(R.id.input_single_rolling_period);
          EditText transmissionRiskLevel =
              requireView().findViewById(R.id.input_single_transmission_risk_level);

          key.setText(BASE16.encode(temporaryExposureKey.getKeyData()));
          interValNumber.setText(
              Integer.toString(temporaryExposureKey.getRollingStartIntervalNumber()));
          rollingPeriod.setText(Integer.toString(temporaryExposureKey.getRollingPeriod()));
          transmissionRiskLevel.setText(
              Integer.toString(temporaryExposureKey.getTransmissionRiskLevel()));
        } catch (DecodeException e) {
          Log.e(TAG, "Decode error", e);
          maybeShowSnackbar(getString(R.string.debug_matching_provide_scan_error));
        }
      }
    } else {
      Log.d(TAG, String.format("onActivityResult unknown requestCode=%d", requestCode));
    }
    super.onActivityResult(requestCode, resultCode, data);
  }

  /**
   * Tries to parse an integer string, if not returns 0 and shows a snackbar.
   */
  private int tryParseInteger(String integer) {
    try {
      return Integer.parseInt(integer);
    } catch (NumberFormatException e) {
      Log.e(TAG, "Couldn't parse number", e);
      maybeShowSnackbar(getString(R.string.debug_matching_single_parse_error));
    }
    return 0;
  }

  private void maybeShowSnackbar(String message) {
    View view = getView();
    if (view != null) {
      Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show();
    }
  }
}
