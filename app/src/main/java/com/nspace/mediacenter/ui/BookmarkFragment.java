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
import com.nspace.mediacenter.core.BookmarkManager;
import com.nspace.mediacenter.model.Bookmark;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows the saved bookmarks and lets the user open or delete them.
 */
public final class BookmarkFragment extends Fragment {

  private ArrayAdapter<String> adapter;
  private final List<Bookmark> bookmarks = new ArrayList<>();

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
    title.setText(R.string.nav_bookmarks);
    ListView listView = view.findViewById(R.id.item_list);
    refresh();

    adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1,
        new ArrayList<String>());
    listView.setAdapter(adapter);
    listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        if (position < bookmarks.size()) {
          ((MainNavigator) requireActivity()).openUrl(bookmarks.get(position).getUrl());
        }
      }
    });
    listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
      @Override
      public boolean onItemLongClick(AdapterView<?> parent, View v, int position, long id) {
        if (position < bookmarks.size()) {
          BookmarkManager.getInstance().removeBookmark(bookmarks.get(position).getId());
          refresh();
          return true;
        }
        return false;
      }
    });
  }

  @Override
  public void onResume() {
    super.onResume();
    refresh();
  }

  private void refresh() {
    bookmarks.clear();
    bookmarks.addAll(BookmarkManager.getInstance().getBookmarks());
    List<String> labels = new ArrayList<>();
    for (Bookmark bookmark : bookmarks) {
      labels.add(bookmark.getTitle() + "\n" + bookmark.getUrl());
    }
    if (adapter != null) {
      adapter.clear();
      adapter.addAll(labels);
      adapter.notifyDataSetChanged();
    }
  }
}
