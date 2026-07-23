package com.nspace.mediacenter.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.nspace.mediacenter.R;
import com.nspace.mediacenter.core.DownloadManager;
import com.nspace.mediacenter.model.DownloadItem;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows tracked downloads and exposes controls to start or clear them.
 */
public final class DownloadFragment extends Fragment {

  private ArrayAdapter<String> adapter;
  private final List<DownloadItem> items = new ArrayList<>();

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_download, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    TextView title = view.findViewById(R.id.list_title);
    title.setText(R.string.nav_downloads);

    final EditText urlBox = view.findViewById(R.id.download_url);
    Button startButton = view.findViewById(R.id.button_start_download);
    Button clearButton = view.findViewById(R.id.button_clear_downloads);
    ListView listView = view.findViewById(R.id.item_list);

    startButton.setOnClickListener(v -> {
      String url = urlBox.getText().toString().trim();
      if (url.isEmpty()) {
        return;
      }
      String fileName = url.substring(url.lastIndexOf('/') + 1);
      if (fileName.isEmpty() || fileName.contains("?")) {
        fileName = "download-" + System.currentTimeMillis();
      }
      DownloadManager.getInstance().enqueue(url, fileName);
      urlBox.setText("");
      refresh();
    });

    clearButton.setOnClickListener(v -> {
      DownloadManager.getInstance().clearFinished();
      refresh();
    });

    adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1,
        new ArrayList<String>());
    listView.setAdapter(adapter);
    refresh();
  }

  @Override
  public void onResume() {
    super.onResume();
    refresh();
  }

  private void refresh() {
    items.clear();
    items.addAll(DownloadManager.getInstance().getDownloads());
    List<String> labels = new ArrayList<>();
    for (DownloadItem item : items) {
      int percent = (int) (item.getProgress() * 100f);
      labels.add(item.getFileName() + "  [" + item.getStatus().name() + " " + percent + "%]");
    }
    if (adapter != null) {
      adapter.clear();
      adapter.addAll(labels);
      adapter.notifyDataSetChanged();
    }
  }
}
