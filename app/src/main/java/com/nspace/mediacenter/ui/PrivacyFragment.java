package com.nspace.mediacenter.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.nspace.mediacenter.R;
import com.nspace.mediacenter.core.PrivacyManager;

/**
 * Privacy surface: exposes the data-cleanup actions.
 */
public final class PrivacyFragment extends Fragment {

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_privacy, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    TextView title = view.findViewById(R.id.list_title);
    title.setText(R.string.nav_privacy);

    Button clearHistory = view.findViewById(R.id.button_clear_history);
    Button clearCookies = view.findViewById(R.id.button_clear_cookies);
    Button clearStorage = view.findViewById(R.id.button_clear_storage);
    Button clearAll = view.findViewById(R.id.button_clear_all);

    final PrivacyManager privacy = PrivacyManager.getInstance();

    clearHistory.setOnClickListener(v -> {
      privacy.clearHistory();
      Toast.makeText(requireContext(), R.string.privacy_cleared_history, Toast.LENGTH_SHORT).show();
    });
    clearCookies.setOnClickListener(v -> {
      privacy.clearCookies();
      Toast.makeText(requireContext(), R.string.privacy_cleared_cookies, Toast.LENGTH_SHORT).show();
    });
    clearStorage.setOnClickListener(v -> {
      privacy.clearWebStorage();
      Toast.makeText(requireContext(), R.string.privacy_cleared_storage, Toast.LENGTH_SHORT).show();
    });
    clearAll.setOnClickListener(v -> {
      privacy.clearAll();
      Toast.makeText(requireContext(), R.string.privacy_cleared_all, Toast.LENGTH_SHORT).show();
    });
  }
}
