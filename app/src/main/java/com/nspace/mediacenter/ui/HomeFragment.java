package com.nspace.mediacenter.ui;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.nspace.mediacenter.R;
import com.nspace.mediacenter.config.RegionAppsConfig;
import com.nspace.mediacenter.core.RecentsManager;
import com.nspace.mediacenter.model.RecentItem;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
 * <p>Each app card is a rounded square tile with a centred brand-coloured logo glyph,
 * focusable with a green outline (DPAD / car-remote friendly). Tapping opens the URL in
 * the built-in browser.
 */
public final class HomeFragment extends Fragment {

  /** App-entry definitions: string-res label, web URL. Logo glyph is the first letter of the (translated) label. */
  // Cache reflection-based drawable lookups so rebuilding the app row (which
  // happens on every home-screen entry and on each region switch) doesn't
  // re-query getIdentifier() for every app on every pass.
  private static final Map<String, Integer> ICON_RES_CACHE = new ConcurrentHashMap<>();

  private static final Shortcut[] SHORTCUTS = {
      new Shortcut(R.string.shortcut_bilibili, "https://www.bilibili.com"),
      new Shortcut(R.string.shortcut_tencent, "https://v.qq.com"),
      new Shortcut(R.string.shortcut_douyin, "https://www.douyin.com"),
      new Shortcut(R.string.shortcut_xigua, "https://www.ixigua.com"),
      new Shortcut(R.string.shortcut_kuaishou, "https://www.kuaishou.com"),
      new Shortcut(R.string.shortcut_haokan, "https://haokan.baidu.com"),
      new Shortcut(R.string.shortcut_sohu, "https://tv.sohu.com"),
      new Shortcut(R.string.shortcut_xiaohongshu, "https://www.xiaohongshu.com"),
      new Shortcut(R.string.shortcut_dedao, "https://www.dedao.cn"),
      new Shortcut(R.string.shortcut_toutiao, "https://www.toutiao.com"),
      new Shortcut(R.string.shortcut_apple_music, "https://music.apple.com"),
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

    final float density = getResources().getDisplayMetrics().density;

    // ── Region badge (display-only, top-right) ──
    updateRegionBadge(view.findViewById(R.id.region_badge));

    // ── Settings gear (top-right, outside system hot zone) ──
    View settingsBtn = view.findViewById(R.id.settings_button);
    if (settingsBtn != null) {
      settingsBtn.setOnClickListener(v -> showRegionPicker());
    }

    // ── Favorite Apps row ────────────────────────────────
    populateApps(view, density);

    // ── Continue Playing row (mid screen) ──────────────────
    populateContinuePlaying(view, density);
  }

  /**
   * (Re)build the Favorite Apps row from the active shortcut set, clearing any
   * previous cards first. Safe to call again after a region switch.
   */
  private void populateApps(@NonNull View root, float density) {
    LinearLayout appsContainer = root.findViewById(R.id.apps_container);
    Shortcut[] activeShortcuts = resolveShortcuts();

    // Fixed large tiles like the reference launcher.  The row lives inside a
    // HorizontalScrollView, so when there are more apps than fit on screen the
    // user simply slides the panel left/right.
    int cardSize = (int) (200 * density);
    int gap = (int) (20 * density);
    int logoSize = (int) (cardSize * 0.58f);
    int count = activeShortcuts.length;

    appsContainer.removeAllViews();

    for (int i = 0; i < activeShortcuts.length; i++) {
      Shortcut sc = activeShortcuts[i];
      View card = createAppCard(sc, cardSize, logoSize, density);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
          cardSize, cardSize);
      // left margin on every card (including first) so the row is centred;
      // the trailing gap is handled by the symmetrical padding maths above.
      lp.setMargins(gap, 0, (i == count - 1) ? gap : 0, 0);
      card.setLayoutParams(lp);
      appsContainer.addView(card);
    }

    // Reset the scroller to the left edge so the large tiles always start from
    // the first app.  Disable saved state so the car launcher doesn't restore a
    // previous scroll position after reinstalling the app.
    HorizontalScrollView appsScroll = root.findViewById(R.id.apps_scroll);
    if (appsScroll != null) {
      appsScroll.setSaveEnabled(false);
      appsContainer.setSaveEnabled(false);
      appsScroll.post(() -> appsScroll.scrollTo(0, 0));
    }
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
    title.setTextSize(14);
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
   * Build a single app entry as a large rounded square tile (reference style):
   * brand-coloured background with a centred app icon or a white letter glyph.
   * Focus zooms the whole tile and adds a green outline for DPAD / car-remote
   * feedback. The tiles are large and sit inside a horizontal scroller, so
   * any overflow is handled by sliding left/right instead of shrinking the cards.
   */
  private View createAppCard(Shortcut sc, int cardSize, int logoSize, float density) {
    FrameLayout card = new FrameLayout(requireContext());
    card.setClickable(true);
    card.setFocusable(true);

    // Card background: icons already bring their own artwork, so keep the tile
    // transparent to avoid the ugly "double box" look. Only the letter-glyph
    // fallback keeps a neutral tile so the white text stays readable.
    final int strokeW = (int) (3 * density);
    final int green = getResources().getColor(R.color.nspace_primary, null);
    GradientDrawable cardBg = new GradientDrawable();
    cardBg.setShape(GradientDrawable.RECTANGLE);
    if (sc.iconResId != 0) {
      cardBg.setColor(Color.TRANSPARENT);
    } else {
      cardBg.setColor(Color.parseColor("#2C2C34"));
    }
    cardBg.setCornerRadius(28 * density);
    card.setBackground(cardBg);

    if (sc.iconResId != 0) {
      // Real app logo centred on the transparent tile.
      ImageView icon = new ImageView(requireContext());
      icon.setImageResource(sc.iconResId);
      icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
      FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(
          logoSize, logoSize, Gravity.CENTER);
      icon.setLayoutParams(iconLp);
      card.addView(icon);
    } else {
      // Fallback: white first-letter glyph on the neutral tile.
      TextView logo = new TextView(requireContext());
      String label = sc.getLabel(requireContext());
      logo.setText(label.substring(0, 1).toUpperCase(Locale.ROOT));
      logo.setTextSize(TypedValue.COMPLEX_UNIT_PX, logoSize * 0.5f);
      logo.setTextColor(Color.WHITE);
      logo.setGravity(Gravity.CENTER);
      logo.setTypeface(logo.getTypeface(), android.graphics.Typeface.BOLD);
      FrameLayout.LayoutParams logoLp = new FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
      logo.setLayoutParams(logoLp);
      card.addView(logo);
    }

    // Focus feedback: green ring on the tile + subtle zoom.
    card.setOnFocusChangeListener((v, hasFocus) -> {
      cardBg.setStroke(hasFocus ? strokeW : 0, green);
      card.setScaleX(hasFocus ? 1.08f : 1.0f);
      card.setScaleY(hasFocus ? 1.08f : 1.0f);
    });

    card.setOnClickListener(v -> onAppClicked(sc));
    return card;
  }

  private Shortcut[] resolveShortcuts() {
    String override = getOverrideRegion();
    try {
      RegionAppsConfig config = RegionAppsConfig.getInstance(requireContext());
      RegionAppsConfig.RegionInfo region = (override != null)
          ? config.getRegion(override)
          : config.getRegion("GLOBAL");
      if (region != null) {
        List<RegionAppsConfig.AppInfo> apps = region.getAllAppsDeduped();
        if (!apps.isEmpty()) {
          Shortcut[] regionShortcuts = new Shortcut[apps.size()];
          for (int i = 0; i < apps.size(); i++) {
            RegionAppsConfig.AppInfo app = apps.get(i);
            int iconResId = 0;
            if (!app.icon.isEmpty()) {
              Integer cached = ICON_RES_CACHE.get(app.icon);
              if (cached == null) {
                int id = requireContext().getResources().getIdentifier(
                    app.icon, "drawable", requireContext().getPackageName());
                cached = id;
                ICON_RES_CACHE.put(app.icon, id);
              }
              iconResId = cached;
            }
            regionShortcuts[i] = new Shortcut(app.name, app.url, iconResId);
          }
          return regionShortcuts;
        }
      }
    } catch (Exception ignored) {
      // Config missing or parse error → use defaults
    }
    return SHORTCUTS;
  }

  // ── Region switcher (settings gear entry) ──────────────

  private static final String PREFS_NAME = "nspace_prefs";
  private static final String KEY_OVERRIDE_REGION = "override_region";

  /** Show a single-choice dialog listing all target regions + "follow system". */
  private void showRegionPicker() {
    RegionAppsConfig config = RegionAppsConfig.getInstance(requireContext());
    if (config == null) {
      Toast.makeText(requireContext(), R.string.region_config_error, Toast.LENGTH_SHORT).show();
      return;
    }
    List<String> codes = config.getRegionCodes(); // sorted ISO codes
    String current = getOverrideRegion();

    final CharSequence[] items = new CharSequence[codes.size() + 1];
    int checked = 0;
    items[0] = getString(R.string.region_follow_system);
    for (int i = 0; i < codes.size(); i++) {
      RegionAppsConfig.RegionInfo r = config.getRegion(codes.get(i));
      items[i + 1] = r.name + "  (" + r.nameEn + ")";
      if (codes.get(i).equalsIgnoreCase(current)) {
        checked = i + 1;
      }
    }

    new AlertDialog.Builder(requireContext())
        .setTitle(R.string.region_picker_title)
        .setSingleChoiceItems(items, checked, (dialog, which) -> {
          String sel = (which == 0) ? null : codes.get(which - 1);
          saveOverrideRegion(sel);
          dialog.dismiss();
          updateRegionBadge(requireView().findViewById(R.id.region_badge));
          final float density = getResources().getDisplayMetrics().density;
          populateApps(requireView(), density);
          String label = (sel == null)
              ? getString(R.string.region_follow_system)
              : config.getRegion(sel).name;
          Toast.makeText(requireContext(),
              getString(R.string.region_toast_applied, label), Toast.LENGTH_SHORT).show();
        })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  /** Refresh the top-right badge: visible only when a region override is set. */
  private void updateRegionBadge(TextView badge) {
    if (badge == null) return;
    String code = getOverrideRegion();
    if (code == null) {
      badge.setVisibility(View.GONE);
      return;
    }
    RegionAppsConfig config = RegionAppsConfig.getInstance(requireContext());
    if (config == null) {
      badge.setVisibility(View.GONE);
      return;
    }
    RegionAppsConfig.RegionInfo r = config.getRegion(code);
    String name = (r != null) ? r.name : code;
    badge.setText(getString(R.string.region_badge_format, name));
    badge.setVisibility(View.VISIBLE);
  }

  private SharedPreferences prefs() {
    return requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);
  }

  /** Returns the manual override region code, or {@code null} to follow system. */
  private String getOverrideRegion() {
    String v = prefs().getString(KEY_OVERRIDE_REGION, null);
    return (v == null || v.isEmpty()) ? null : v.toUpperCase(Locale.US);
  }

  private void saveOverrideRegion(String code) {
    prefs().edit()
        .putString(KEY_OVERRIDE_REGION, code == null ? "" : code.toUpperCase(Locale.US))
        .apply();
  }

  private void onAppClicked(Shortcut sc) {
    if (getActivity() instanceof MainNavigator && sc.url != null) {
      ((MainNavigator) getActivity()).openUrl(sc.url);
    }
  }

  // ── Data class ─────────────────────────────────────────────

  private static final class Shortcut {
    /** String resource ID for label (used by built-in defaults). {@code 0} = use {@link #name} instead. */
    final int labelRes;
    /** Raw app name string (used by region-based config when labelRes == 0). */
    final String name;
    final String url;
    /** Drawable resource ID for the app logo, or {@code 0} when unavailable (letter glyph fallback). */
    final int iconResId;

    /** Primary constructor. */
    Shortcut(int labelRes, String name, String url, int iconResId) {
      this.labelRes = labelRes;
      this.name = name;
      this.url = url;
      this.iconResId = iconResId;
    }

    /** Constructor for built-in defaults (string-res based, supports i18n). */
    Shortcut(int labelRes, String url) {
      this(labelRes, null, url, 0);
    }

    /** Constructor for region-based config without a bundled icon. */
    Shortcut(String name, String url) {
      this(0, name, url, 0);
    }

    /** Constructor for region-based config with a bundled app logo. */
    Shortcut(String name, String url, int iconResId) {
      this(0, name, url, iconResId);
    }

    /** Resolve display label: translated string-res or raw name. */
    String getLabel(android.content.Context ctx) {
      if (labelRes != 0) return ctx.getString(labelRes);
      return (name != null) ? name : "?";
    }
  }
}
