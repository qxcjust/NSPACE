package com.nspace.mediacenter.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.nspace.mediacenter.BuildConfig;
import com.nspace.mediacenter.R;
import com.nspace.mediacenter.core.SearchEngine;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings surface: chooses the default search engine and shows app info.
 */
public final class SettingsFragment extends Fragment {

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_settings, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    TextView title = view.findViewById(R.id.list_title);
    title.setText(R.string.nav_settings);

    TextView versionText = view.findViewById(R.id.version_text);
    versionText.setText(getString(R.string.settings_version, BuildConfig.VERSION_NAME));

    Spinner engineSpinner = view.findViewById(R.id.search_engine_spinner);
    final SearchEngine searchEngine = SearchEngine.getInstance();
    List<String> names = new ArrayList<>();
    for (SearchEngine.Engine engine : searchEngine.getEngines()) {
      names.add(engine.getName());
    }
    ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(requireContext(),
        android.R.layout.simple_spinner_item, names);
    spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    engineSpinner.setAdapter(spinnerAdapter);

    int selected = 0;
    List<SearchEngine.Engine> engines = searchEngine.getEngines();
    for (int i = 0; i < engines.size(); i++) {
      if (engines.get(i).getId().equals(searchEngine.getCurrent().getId())) {
        selected = i;
        break;
      }
    }
    engineSpinner.setSelection(selected);
    engineSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
        SearchEngine.Engine engine = searchEngine.getEngines().get(position);
        searchEngine.setCurrent(engine.getId());
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
        // Keep current selection.
      }
    });
  }
}
