package com.nspace.mediacenter.ui;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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

  /** App-entry definitions: string-res label, brand colour, web URL. Logo glyph is the first letter of the (translated) label. */
  private static final Shortcut[] SHORTCUTS = {
      new Shortcut(R.string.shortcut_bilibili, "#FB7299", "https://www.bilibili.com"),
      new Shortcut(R.string.shortcut_tencent, "#23ADE5", "https://v.qq.com"),
      new Shortcut(R.string.shortcut_douyin, "#FE2C55", "https://www.douyin.com"),
      new Shortcut(R.string.shortcut_xigua, "#FF7A00", "https://www.ixigua.com"),
      new Shortcut(R.string.shortcut_kuaishou, "#FF6600", "https://www.kuaishou.com"),
      new Shortcut(R.string.shortcut_haokan, "#2B5CFF", "https://haokan.baidu.com"),
      new Shortcut(R.string.shortcut_sohu, "#C80815", "https://tv.sohu.com"),
      new Shortcut(R.string.shortcut_xiaohongshu, "#FF2442", "https://www.xiaohongshu.com"),
      new Shortcut(R.string.shortcut_dedao, "#FF6A00", "https://www.dedao.cn"),
      new Shortcut(R.string.shortcut_toutiao, "#FE0601", "https://www.toutiao.com"),
      new Shortcut(R.string.shortcut_apple_music, "#FA2D48", "https://music.apple.com"),
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

    final int cardW = (int) (120 * density);
    final int logoSize = (int) (80 * density);
    final int gap = (int) (10 * density);

    appsContainer.removeAllViews();

    // Leading "Settings" entry (always visible, reliable tap zone in the
    // apps panel). Opens the region switcher.
    View settingsCard = createSettingsCard(density);
    LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
        cardW, ViewGroup.LayoutParams.WRAP_CONTENT);
    settingsCard.setLayoutParams(slp);
    appsContainer.addView(settingsCard);

    for (int i = 0; i < activeShortcuts.length; i++) {
      Shortcut sc = activeShortcuts[i];
      View card = createAppCard(sc, logoSize, density);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
          cardW, ViewGroup.LayoutParams.WRAP_CONTENT);
      lp.setMargins(gap, 0, 0, 0);
      card.setLayoutParams(lp);
      appsContainer.addView(card);
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
    String label = sc.getLabel(requireContext());
    logo.setText(label.substring(0, 1).toUpperCase(Locale.ROOT));
    logo.setTextSize(28);
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
    name.setText(label);
    name.setTextSize(13);
    name.setTextColor(getResources().getColor(R.color.nspace_on_light, null));
    name.setGravity(Gravity.CENTER);
    name.setSingleLine(true);
    name.setEllipsize(android.text.TextUtils.TruncateAt.END);
    LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    nameLp.setMargins(0, (int) (8 * density), 0, 0);
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

  /**
   * Resolve the active shortcut list: a manual region override (chosen via the
   * settings gear) takes priority, then a region match on the system locale,
   * finally the built-in {@link #SHORTCUTS} array.
   */
  private Shortcut[] resolveShortcuts() {
    String override = getOverrideRegion();
    try {
      RegionAppsConfig config = RegionAppsConfig.getInstance(requireContext());
      RegionAppsConfig.RegionInfo region = (override != null)
          ? config.getRegion(override)
          : config.getRegionForCurrentLocale(requireContext(), null);
      if (region != null) {
        List<RegionAppsConfig.AppInfo> apps = region.getAllAppsDeduped();
        if (!apps.isEmpty()) {
          Shortcut[] regionShortcuts = new Shortcut[apps.size()];
          for (int i = 0; i < apps.size(); i++) {
            RegionAppsConfig.AppInfo app = apps.get(i);
            regionShortcuts[i] = new Shortcut(app.name, app.brandColor, app.url);
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

  /**
   * Build the leading "Settings" entry card: a neutral rounded tile with a gear
   * glyph and the "Settings" label. Tapping opens the region switcher. Placed in
   * the apps panel (a reliably tappable zone) because the screen corners are
   * reserved by the car launcher.
   */
  private View createSettingsCard(float density) {
    LinearLayout card = new LinearLayout(requireContext());
    card.setOrientation(LinearLayout.VERTICAL);
    card.setGravity(Gravity.CENTER);
    card.setClickable(true);
    card.setFocusable(true);

    ImageView logo = new ImageView(requireContext());
    logo.setImageResource(R.drawable.ic_settings);
    logo.setColorFilter(Color.WHITE);
    logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

    GradientDrawable logoBg = new GradientDrawable();
    logoBg.setShape(GradientDrawable.RECTANGLE);
    logoBg.setColor(Color.parseColor("#2E4038"));
    logoBg.setCornerRadius(20 * density);
    logo.setBackground(logoBg);

    final int logoSize = (int) (80 * density);
    LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(logoSize, logoSize);
    logo.setLayoutParams(logoLp);
    card.addView(logo);

    TextView name = new TextView(requireContext());
    name.setText(R.string.action_settings);
    name.setTextSize(14);
    name.setTextColor(getResources().getColor(R.color.nspace_on_light, null));
    name.setGravity(Gravity.CENTER);
    name.setSingleLine(true);
    name.setEllipsize(android.text.TextUtils.TruncateAt.END);
    LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    nameLp.setMargins(0, (int) (8 * density), 0, 0);
    name.setLayoutParams(nameLp);
    card.addView(name);

    final int strokeW = (int) (3 * density);
    final int green = getResources().getColor(R.color.nspace_primary, null);
    card.setOnFocusChangeListener((v, hasFocus) -> {
      logoBg.setStroke(hasFocus ? strokeW : 0, green);
      card.setScaleX(hasFocus ? 1.06f : 1.0f);
      card.setScaleY(hasFocus ? 1.06f : 1.0f);
    });

    card.setOnClickListener(v -> showRegionPicker());
    return card;
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
    final String brandColor;
    final String url;

    /** Constructor for built-in defaults (string-res based, supports i18n). */
    Shortcut(int labelRes, String brandColor, String url) {
      this.labelRes = labelRes;
      this.name = null;
      this.brandColor = brandColor;
      this.url = url;
    }

    /** Constructor for region-based config (raw name from JSON). */
    Shortcut(String name, String brandColor, String url) {
      this.labelRes = 0;
      this.name = name;
      this.brandColor = brandColor;
      this.url = url;
    }

    /** Resolve display label: translated string-res or raw name. */
    String getLabel(android.content.Context ctx) {
      if (labelRes != 0) return ctx.getString(labelRes);
      return (name != null) ? name : "?";
    }
  }
}
