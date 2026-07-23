package com.nspace.mediacenter.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.nspace.mediacenter.R;
import com.nspace.mediacenter.core.HistoryManager;
import com.nspace.mediacenter.model.HistoryItem;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows browsing history and lets the user reopen or clear entries.
 */
public final class HistoryFragment extends Fragment {

  private ArrayAdapter<String> adapter;
  private final List<HistoryItem> items = new ArrayList<>();

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_list, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    TextView title = view.findViewById(R.id.list_title);
    title.setText(R.string.nav_history);
    ListView listView = view.findViewById(R.id.item_list);
    refresh();

    adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1,
        new ArrayList<String>());
    listView.setAdapter(adapter);
    listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        if (position < items.size()) {
          ((MainNavigator) requireActivity()).openUrl(items.get(position).getUrl());
        }
      }
    });
  }

  @Override
  public void onResume() {
    super.onResume();
    refresh();
  }

  private void refresh() {
    items.clear();
    items.addAll(HistoryManager.getInstance().getHistory());
    List<String> labels = new ArrayList<>();
    for (HistoryItem item : items) {
      labels.add(item.getTitle() + "\n" + item.getUrl());
    }
    if (adapter != null) {
      adapter.clear();
      adapter.addAll(labels);
      adapter.notifyDataSetChanged();
    }
  }
}
