package com.nspace.mediacenter.ui;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.nspace.mediacenter.R;
import com.nspace.mediacenter.core.RecentsManager;
import com.nspace.mediacenter.model.RecentItem;
import java.io.File;

/**
 * Android TV Launcher-style home screen.
 *
 * <p>Layout (top → bottom):
 * <ol>
 *   <li>Hero banner: full-width background with dark gradient overlay + title/subtitle (top-left)</li>
 *   <li>Continue Playing: big snapshot cards of recently opened pages (hidden when empty)</li>
 *   <li>Favorite Apps: single long panel with brand-coloured app cards, always at bottom</li>
 * </ol>
 *
 * <p>Each app card is a rounded tile with a brand-coloured logo glyph and the app name,
 * focusable with a green outline (DPAD / car-remote friendly). Tapping opens the URL in
 * the built-in browser.
 */
public final class HomeFragment extends Fragment {

  /** App-entry definitions: display name, logo glyph, brand colour, web URL. */
  private static final Shortcut[] SHORTCUTS = {
      new Shortcut("Bilibili", "B", "#FB7299", "https://www.bilibili.com"),
      new Shortcut("腾讯视频", "腾", "#23ADE5", "https://v.qq.com"),
      new Shortcut("抖音", "抖", "#FE2C55", "https://www.douyin.com"),
      new Shortcut("西瓜", "西", "#FF7A00", "https://www.ixigua.com"),
      new Shortcut("快手", "快", "#FF6600", "https://www.kuaishou.com"),
      new Shortcut("好看视频", "好", "#2B5CFF", "https://haokan.baidu.com"),
      new Shortcut("搜狐视频", "搜", "#C80815", "https://tv.sohu.com"),
      new Shortcut("小红书", "小", "#FF2442", "https://www.xiaohongshu.com"),
      new Shortcut("得到", "得", "#FF6A00", "https://www.dedao.cn"),
      new Shortcut("今日头条", "今", "#FE0601", "https://www.toutiao.com"),
      new Shortcut("Apple Music", "\u266A", "#FA2D48", "https://music.apple.com"),
  };

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_home, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    // ── Favorite Apps row ────────────────────────────────
    LinearLayout appsContainer = view.findViewById(R.id.apps_container);
    final float density = getResources().getDisplayMetrics().density;
    final int cardW = (int) (150 * density);
    final int logoSize = (int) (100 * density);
    final int gap = (int) (12 * density);

    for (int i = 0; i < SHORTCUTS.length; i++) {
      Shortcut sc = SHORTCUTS[i];
      View card = createAppCard(sc, logoSize, density);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
          cardW, ViewGroup.LayoutParams.WRAP_CONTENT);
      if (i > 0) {
        lp.setMargins(gap, 0, 0, 0);
      }
      card.setLayoutParams(lp);
      appsContainer.addView(card);
    }

    // ── Continue Playing row (mid screen) ──────────────────
    populateContinuePlaying(view, density);
  }

  /**
   * Shows the "Continue Playing" band when there are captured recents, hiding it
   * entirely otherwise. Cards are sized after layout so they fill the band's
   * height with a 16:9 width, making the row the dominant focal element.
   */
  private void populateContinuePlaying(@NonNull View root, float density) {
    View section = root.findViewById(R.id.continue_section);
    LinearLayout container = root.findViewById(R.id.continue_container);
    if (section == null || container == null) {
      return;
    }

    java.util.List<RecentItem> recents = RecentsManager.getInstance().getRecents();
    if (recents.isEmpty()) {
      section.setVisibility(View.GONE);
      return;
    }

    section.setVisibility(View.VISIBLE);
    final int gap = (int) (20 * density);

    container.removeAllViews();
    for (int i = 0; i < recents.size(); i++) {
      container.addView(createContinueCard(recents.get(i), density));
    }

    // Size each card to fill the band: height = band height, width = 16:9.
    container.post(() -> {
      int bandH = container.getHeight();
      if (bandH <= 0) {
        return;
      }
      int cardW = (int) (bandH * 16f / 9f);
      int gradH = (int) (bandH * 0.34f);
      int titleBottom = (int) (bandH * 0.05f);
      for (int i = 0; i < container.getChildCount(); i++) {
        View card = container.getChildAt(i);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) card.getLayoutParams();
        lp.width = cardW;
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.setMargins(i > 0 ? gap : 0, 0, 0, 0);
        card.setLayoutParams(lp);

        View grad = card.findViewWithTag("grad");
        if (grad != null) {
          FrameLayout.LayoutParams glp = (FrameLayout.LayoutParams) grad.getLayoutParams();
          glp.height = gradH;
          grad.setLayoutParams(glp);
        }
        View title = card.findViewWithTag("title");
        if (title != null) {
          FrameLayout.LayoutParams tlp = (FrameLayout.LayoutParams) title.getLayoutParams();
          tlp.setMargins(0, 0, 0, titleBottom);
          title.setLayoutParams(tlp);
        }
      }
    });
  }

  /**
   * Builds one "Continue Playing" card: a snapshot thumbnail with a bottom
   * gradient and the page title overlaid, opening the URL on tap. Card width and
   * the gradient/title proportions are finalized in {@link #populateContinuePlaying}
   * once the band's height is known.
   */
  private View createContinueCard(RecentItem item, float density) {
    final int pad = (int) (8 * density);

    FrameLayout card = new FrameLayout(requireContext());
    card.setClickable(true);
    card.setFocusable(true);
    card.setBackgroundResource(R.drawable.continue_card_bg);

    // Snapshot thumbnail (clipped to rounded corners)
    ImageView thumb = new ImageView(requireContext());
    thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
    thumb.setBackgroundResource(R.drawable.continue_card_bg);
    thumb.setClipToOutline(true);
    FrameLayout.LayoutParams thumbLp = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    thumbLp.setMargins(pad, pad, pad, pad);
    thumb.setLayoutParams(thumbLp);
    String thumbPath = item.getThumbnailPath();
    if (thumbPath != null && !thumbPath.isEmpty()) {
      thumb.setImageURI(Uri.fromFile(new File(thumbPath)));
    }
    card.addView(thumb);

    // Bottom-up dark gradient for title legibility
    View gradient = new View(requireContext());
    gradient.setTag("grad");
    gradient.setBackgroundResource(R.drawable.continue_card_gradient);
    FrameLayout.LayoutParams gradLp = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, (int) (88 * density));
    gradLp.gravity = Gravity.BOTTOM;
    gradient.setLayoutParams(gradLp);
    card.addView(gradient);

    // Title overlay
    TextView title = new TextView(requireContext());
    title.setTag("title");
    title.setText(item.getTitle());
    title.setTextSize(18);
    title.setTextColor(Color.WHITE);
    title.setSingleLine(true);
    title.setEllipsize(TextUtils.TruncateAt.END);
    title.setPadding((int) (16 * density), 0, (int) (16 * density), 0);
    FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    titleLp.gravity = Gravity.BOTTOM;
    titleLp.setMargins(0, 0, 0, (int) (16 * density));
    title.setLayoutParams(titleLp);
    card.addView(title);

    // Focus feedback: subtle zoom (DPAD / car-remote friendly)
    card.setOnFocusChangeListener((v, hasFocus) -> {
      card.setScaleX(hasFocus ? 1.06f : 1.0f);
      card.setScaleY(hasFocus ? 1.06f : 1.0f);
    });

    card.setOnClickListener(v -> {
      if (getActivity() instanceof MainNavigator && item.getUrl() != null) {
        ((MainNavigator) getActivity()).openUrl(item.getUrl());
      }
    });
    return card;
  }

  /**
   * Build a single app entry (no own frame): a brand-coloured rounded logo
   * tile with the app glyph and the app name underneath. The whole row sits
   * inside one long panel (see {@code apps_panel_bg}); on DPAD focus the logo
   * gains a green ring and the entry zooms slightly for TV feedback.
   */
  private View createAppCard(Shortcut sc, int logoSize, float density) {
    LinearLayout card = new LinearLayout(requireContext());
    card.setOrientation(LinearLayout.VERTICAL);
    card.setGravity(Gravity.CENTER);
    card.setClickable(true);
    card.setFocusable(true);

    // Brand-coloured rounded logo tile
    TextView logo = new TextView(requireContext());
    logo.setText(sc.glyph);
    logo.setTextSize(34);
    logo.setTextColor(Color.WHITE);
    logo.setGravity(Gravity.CENTER);
    logo.setTypeface(logo.getTypeface(), android.graphics.Typeface.BOLD);

    GradientDrawable logoBg = new GradientDrawable();
    logoBg.setShape(GradientDrawable.RECTANGLE);
    logoBg.setColor(Color.parseColor(sc.brandColor));
    logoBg.setCornerRadius(20 * density);
    logo.setBackground(logoBg);

    LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(logoSize, logoSize);
    logo.setLayoutParams(logoLp);
    card.addView(logo);

    // App name underneath
    TextView name = new TextView(requireContext());
    name.setText(sc.label);
    name.setTextSize(16);
    name.setTextColor(getResources().getColor(R.color.nspace_on_light, null));
    name.setGravity(Gravity.CENTER);
    name.setSingleLine(true);
    name.setEllipsize(android.text.TextUtils.TruncateAt.END);
    LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    nameLp.setMargins(0, (int) (10 * density), 0, 0);
    name.setLayoutParams(nameLp);
    card.addView(name);

    // Focus feedback: green ring on the logo + subtle zoom. No per-card frame,
    // so the entire row stays inside the single long panel.
    final int strokeW = (int) (3 * density);
    final int green = getResources().getColor(R.color.nspace_primary, null);
    card.setOnFocusChangeListener((v, hasFocus) -> {
      logoBg.setStroke(hasFocus ? strokeW : 0, green);
      card.setScaleX(hasFocus ? 1.06f : 1.0f);
      card.setScaleY(hasFocus ? 1.06f : 1.0f);
    });

    card.setOnClickListener(v -> onAppClicked(sc));
    return card;
  }

  private void onAppClicked(Shortcut sc) {
    if (getActivity() instanceof MainNavigator && sc.url != null) {
      ((MainNavigator) getActivity()).openUrl(sc.url);
    }
  }

  // ── Data class ─────────────────────────────────────────────

  private static final class Shortcut {
    final String label;
    final String glyph;
    final String brandColor;
    final String url;

    Shortcut(String label, String glyph, String brandColor, String url) {
      this.label = label;
      this.glyph = glyph;
      this.brandColor = brandColor;
      this.url = url;
    }
  }
}
