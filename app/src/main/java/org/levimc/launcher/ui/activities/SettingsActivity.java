package org.levimc.launcher.ui.activities;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.switchmaterial.SwitchMaterial;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import org.levimc.launcher.R;
import org.levimc.launcher.core.crash.CrashReporter;
import org.levimc.launcher.preloader.PreloaderSignatureRulesManager;
import org.levimc.launcher.settings.FeatureSettings;
import org.levimc.launcher.ui.animation.DynamicAnim;
import org.levimc.launcher.util.GithubReleaseUpdater;
import org.levimc.launcher.util.LanguageManager;
import org.levimc.launcher.util.PermissionsHandler;
import org.levimc.launcher.util.PersonalizationManager;
import org.levimc.launcher.util.ThemeManager;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

public class SettingsActivity extends BaseActivity {

    private PermissionsHandler permissionsHandler;
    private ActivityResultLauncher<Intent> permissionResultLauncher;
    private int updateButtonTapCount = 0;
    private long lastUpdateButtonTapTime = 0;
    private static final int EASTER_EGG_TAP_COUNT = 3;
    private static final long TAP_TIMEOUT_MS = 2000;

    private TextView tabBasic;
    private TextView tabPersonalize;
    private TextView tabUpdates;

    private View sectionBasic;
    private View sectionPersonalize;
    private View sectionUpdates;

    private static final String KEY_SELECTED_TAB = "selected_tab_index";
    private int selectedTabIndex = 0;

    private PersonalizationManager personalizationManager;
    private LinearLayout colorGridContainer;
    private LinearLayout moreColorsContainer;
    private TextView preloaderSigsLastUpdateText;
    private Button preloaderSigsUpdateButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        DynamicAnim.applyPressScaleRecursively(findViewById(android.R.id.content));

        setupNavBar();

        personalizationManager = new PersonalizationManager(this);

        if (savedInstanceState != null) {
            selectedTabIndex = savedInstanceState.getInt(KEY_SELECTED_TAB, 0);
        }

        permissionsHandler = PermissionsHandler.getInstance();
        permissionResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (permissionsHandler != null) {
                        permissionsHandler.onActivityResult(result.getResultCode(), result.getData());
                    }
                }
        );
        permissionsHandler.setActivity(this, permissionResultLauncher);


        initTabs();
        setupBasicSection();
        setupPersonalizeSection();
        setupUpdatesSection();

        TextView[] tabs = getSettingsTabs();
        if (selectedTabIndex >= tabs.length) {
            selectedTabIndex = 0;
        }
        selectTab(tabs[selectedTabIndex]);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_SELECTED_TAB, selectedTabIndex);
    }

    private void initTabs() {
        tabBasic = findViewById(R.id.tab_basic);
        tabPersonalize = findViewById(R.id.tab_personalize);
        tabUpdates = findViewById(R.id.tab_updates);

        sectionBasic = findViewById(R.id.section_basic);
        sectionPersonalize = findViewById(R.id.section_personalize);
        sectionUpdates = findViewById(R.id.section_updates);

        tabBasic.setOnClickListener(v -> { selectedTabIndex = 0; selectTab(tabBasic); });
        tabPersonalize.setOnClickListener(v -> { selectedTabIndex = 1; selectTab(tabPersonalize); });
        tabUpdates.setOnClickListener(v -> { selectedTabIndex = 2; selectTab(tabUpdates); });
    }

    private void selectTab(TextView selectedTab) {
        TextView[] tabs = getSettingsTabs();
        View[] sections = {sectionBasic, sectionPersonalize, sectionUpdates};

        int accent = personalizationManager.getAccentColor();

        for (int i = 0; i < tabs.length; i++) {
            boolean isSelected = tabs[i] == selectedTab;

            if (isSelected) {
                if (accent != 0) {
                    android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                    gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                    gd.setColor(accent);
                    gd.setCornerRadius(16 * getResources().getDisplayMetrics().density);
                    tabs[i].setBackground(gd);
                } else {
                    tabs[i].setBackgroundResource(R.drawable.bg_tab_selected);
                }
                tabs[i].setTextColor(Color.WHITE);
                tabs[i].setTextSize(13);
            } else {
                tabs[i].setBackgroundResource(R.drawable.bg_tab_unselected);
                tabs[i].setTextColor(getColor(R.color.text_secondary));
            }

            if (isSelected) {
                sections[i].setVisibility(View.VISIBLE);
                sections[i].setAlpha(0f);
                sections[i].animate().alpha(1f).setDuration(200).start();
            } else {
                sections[i].setVisibility(View.GONE);
            }
        }
    }

    private TextView[] getSettingsTabs() {
        return new TextView[]{tabBasic, tabPersonalize, tabUpdates};
    }

    private void setupBasicSection() {
        LanguageManager languageManager = new LanguageManager(this);
        FeatureSettings fs = FeatureSettings.getInstance();

        String[] languageOptions = {
                getString(R.string.english),
                getString(R.string.chinese),
                getString(R.string.russian),
                getString(R.string.indonesian),
                getString(R.string.spanish),
                getString(R.string.portuguese),
                getString(R.string.french),
                getString(R.string.japanese),
                getString(R.string.hindi),
                        getString(R.string.turkish),
                            getString(R.string.vietnamese)
        };

        String currentCode = languageManager.getCurrentLanguage();
        int defaultIdx = switch (currentCode) {
            case "zh", "zh-CN" -> 1;
            case "ru" -> 2;
            case "idn" -> 3;
            case "es" -> 4;
            case "pt" -> 5;
            case "fr" -> 6;
            case "ja" -> 7;
            case "hi" -> 8;
                case "tr", "tr-TR" -> 9;
                        case "vi" -> 10;
            default -> 0;
        };

        TextView languageCurrent = findViewById(R.id.language_current);
        languageCurrent.setText(languageOptions[defaultIdx]);

        Spinner languageSpinner = findViewById(R.id.language_spinner);
        ArrayAdapter<String> langAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, languageOptions);
        langAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        languageSpinner.setAdapter(langAdapter);
        languageSpinner.setPopupBackgroundResource(R.drawable.bg_popup_menu_rounded);
        languageSpinner.setSelection(defaultIdx);
        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String code = switch (position) {
                    case 1 -> "zh-CN";
                    case 2 -> "ru";
                    case 3 -> "idn";
                    case 4 -> "es";
                    case 5 -> "pt";
                    case 6 -> "fr";
                    case 7 -> "ja";
                    case 8 -> "hi";
                            case 9 -> "tr";
                                        case 10 -> "vi";
                    default -> "en";
                };
                if (!code.equals(languageManager.getCurrentLanguage())) {
                    languageManager.setAppLanguage(code);
                }
                languageCurrent.setText(languageOptions[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        SwitchMaterial switchCrashUpload = findViewById(R.id.switch_crash_upload);
        switchCrashUpload.setChecked(fs.isCrashUploadEnabled());
        switchCrashUpload.setOnCheckedChangeListener((btn, checked) -> {
            fs.setCrashUploadEnabled(checked);
            CrashReporter.refreshCrashlyticsCollection(this);
        });

        SwitchMaterial switchManagedLogin = findViewById(R.id.switch_managed_login);
        switchManagedLogin.setChecked(fs.isLauncherManagedMcLoginEnabled());
        switchManagedLogin.setOnCheckedChangeListener((btn, checked) -> fs.setLauncherManagedMcLoginEnabled(checked));
    }

    private void setupPersonalizeSection() {
        ThemeManager themeManager = new ThemeManager(this);

        View itemSystem = findViewById(R.id.theme_item_system);
        View itemLight = findViewById(R.id.theme_item_light);
        View itemDark = findViewById(R.id.theme_item_dark);

        refreshThemeSelectionUI();

        if (itemSystem != null && itemLight != null && itemDark != null) {
            itemSystem.setOnClickListener(v -> { themeManager.setThemeMode(0); });
            itemLight.setOnClickListener(v -> { themeManager.setThemeMode(1); });
            itemDark.setOnClickListener(v -> { themeManager.setThemeMode(2); });
        }

        setupColorPicker();
    }

    private void refreshThemeSelectionUI() {
        ThemeManager themeManager = new ThemeManager(this);
        int currentMode = themeManager.getCurrentMode();

        TextView textSystem = findViewById(R.id.theme_text_system);
        TextView textLight = findViewById(R.id.theme_text_light);
        TextView textDark = findViewById(R.id.theme_text_dark);

        ImageView iconSystem = findViewById(R.id.theme_icon_system);
        ImageView iconLight = findViewById(R.id.theme_icon_light);
        ImageView iconDark = findViewById(R.id.theme_icon_dark);

        int accent = personalizationManager.getAccentColor();
        int selectedColor = accent != 0 ? accent : getColor(R.color.on_surface);
        int unselectedColor = getColor(R.color.text_secondary);

        if (textSystem != null) {
            textSystem.setTypeface(null, currentMode == 0 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            textSystem.setTextColor(currentMode == 0 ? selectedColor : getColor(R.color.on_surface));
        }
        if (textLight != null) {
            textLight.setTypeface(null, currentMode == 1 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            textLight.setTextColor(currentMode == 1 ? selectedColor : getColor(R.color.on_surface));
        }
        if (textDark != null) {
            textDark.setTypeface(null, currentMode == 2 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            textDark.setTextColor(currentMode == 2 ? selectedColor : getColor(R.color.on_surface));
        }

        if (iconSystem != null) iconSystem.setImageTintList(android.content.res.ColorStateList.valueOf(currentMode == 0 ? selectedColor : unselectedColor));
        if (iconLight != null) iconLight.setImageTintList(android.content.res.ColorStateList.valueOf(currentMode == 1 ? selectedColor : unselectedColor));
        if (iconDark != null) iconDark.setImageTintList(android.content.res.ColorStateList.valueOf(currentMode == 2 ? selectedColor : unselectedColor));
    }

    private void setupColorPicker() {
        colorGridContainer = findViewById(R.id.color_preset_grid);
        moreColorsContainer = findViewById(R.id.color_more_grid);

        if (colorGridContainer != null && moreColorsContainer != null) {
            int currentAccent = personalizationManager.getAccentColor();
            buildColorGrid(colorGridContainer, PersonalizationManager.PRESET_COLORS, currentAccent);
            buildColorGrid(moreColorsContainer, PersonalizationManager.MORE_COLORS, currentAccent);
        }

        android.widget.EditText inputCustomColor = findViewById(R.id.input_custom_color);
        Button btnApplyColor = findViewById(R.id.btn_apply_color);
        if (inputCustomColor != null && btnApplyColor != null) {
            btnApplyColor.setOnClickListener(v -> {
                String input = inputCustomColor.getText().toString().trim();
                try {
                    int color;
                    if (input.startsWith("#")) {
                        color = Color.parseColor(input);
                    } else if (input.contains(",")) {
                        String[] parts = input.split(",");
                        if (parts.length == 3) {
                            color = Color.rgb(Integer.parseInt(parts[0].trim()),
                                    Integer.parseInt(parts[1].trim()),
                                    Integer.parseInt(parts[2].trim()));
                        } else {
                            throw new IllegalArgumentException("Invalid RGB format");
                        }
                    } else {
                        color = Color.parseColor("#" + input);
                    }
                    personalizationManager.setAccentColor(color);
                    refreshColorPickerInPlace();
                } catch (Exception e) {
                    Toast.makeText(this, "Invalid color format", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void buildColorGrid(LinearLayout container, int[] colors, int selectedColor) {
        container.removeAllViews();

        float density = getResources().getDisplayMetrics().density;
        int circleSize = (int) (32 * density);
        int margin = (int) (4 * density);
        int checkSize = (int) (14 * density);

        int columns = 15;
        int index = 0;
        while (index < colors.length) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            for (int col = 0; col < columns && index < colors.length; col++, index++) {
                int color = colors[index];

                FrameLayout wrapper = new FrameLayout(this);
                LinearLayout.LayoutParams wrapParams = new LinearLayout.LayoutParams(circleSize, circleSize);
                wrapParams.setMargins(margin, margin, margin, margin);
                wrapper.setLayoutParams(wrapParams);

                View circle = new View(this);
                circle.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                GradientDrawable circleDrawable = new GradientDrawable();
                circleDrawable.setShape(GradientDrawable.OVAL);
                circleDrawable.setColor(color);
                if (color == selectedColor) {
                    circleDrawable.setStroke((int) (2 * density), Color.WHITE);
                }
                circle.setBackground(circleDrawable);
                wrapper.addView(circle);

                if (color == selectedColor) {
                    ImageView check = new ImageView(this);
                    FrameLayout.LayoutParams checkParams = new FrameLayout.LayoutParams(checkSize, checkSize);
                    checkParams.gravity = Gravity.CENTER;
                    check.setLayoutParams(checkParams);
                    check.setImageResource(R.drawable.ic_check);
                    check.setColorFilter(Color.WHITE);
                    wrapper.addView(check);
                }

                wrapper.setClickable(true);
                wrapper.setFocusable(true);
                final int finalColor = color;
                wrapper.setOnClickListener(v -> {
                    personalizationManager.setAccentColor(finalColor);
                    refreshColorPickerInPlace();
                });
                DynamicAnim.applyPressScale(wrapper);

                row.addView(wrapper);
            }

            container.addView(row);
        }

    }

    private void refreshColorPickerInPlace() {
        setupColorPicker();
        PersonalizationManager pm = new PersonalizationManager(this);
        int accent = pm.getAccentColor();
        
        pm.applyToActivity(this);

        refreshThemeSelectionUI();
        
        TextView[] tabs = getSettingsTabs();
        selectTab(tabs[selectedTabIndex]);
        
        View settingsTitle = findViewById(R.id.settings_title);
        if (settingsTitle instanceof TextView && accent != 0) {
            ((TextView) settingsTitle).setTextColor(accent);
        }

        Button btnCheckUpdate = findViewById(R.id.btn_check_update);
        if (btnCheckUpdate != null && accent != 0) {
            btnCheckUpdate.setBackgroundTintList(ColorStateList.valueOf(accent));
            btnCheckUpdate.setTextColor(Color.WHITE);
        }

        Button btnUpdatePreloaderSigs = findViewById(R.id.btn_update_preloader_sigs);
        if (btnUpdatePreloaderSigs != null && accent != 0) {
            btnUpdatePreloaderSigs.setBackgroundTintList(ColorStateList.valueOf(accent));
            btnUpdatePreloaderSigs.setTextColor(Color.WHITE);
        }

        SwitchMaterial switchManagedLogin = findViewById(R.id.switch_managed_login);
        if (switchManagedLogin != null && accent != 0) {
            int[][] states = {{android.R.attr.state_checked}, {}};
            switchManagedLogin.setThumbTintList(new ColorStateList(states, new int[]{accent, 0xFFAAAAAA}));
            int trackChecked = Color.argb(100, Color.red(accent), Color.green(accent), Color.blue(accent));
            switchManagedLogin.setTrackTintList(new ColorStateList(states, new int[]{trackChecked, 0xFF555555}));
        }

        SwitchMaterial switchCrashUpload = findViewById(R.id.switch_crash_upload);
        if (switchCrashUpload != null && accent != 0) {
            int[][] states = {{android.R.attr.state_checked}, {}};
            switchCrashUpload.setThumbTintList(new ColorStateList(states, new int[]{accent, 0xFFAAAAAA}));
            int trackChecked = Color.argb(100, Color.red(accent), Color.green(accent), Color.blue(accent));
            switchCrashUpload.setTrackTintList(new ColorStateList(states, new int[]{trackChecked, 0xFF555555}));
        }
        
        TextView navAppName = findViewById(R.id.nav_app_name);
        if (navAppName != null && accent != 0) {
            pm.applySolidAccentText(navAppName, accent);
        }
        
        Button navSignInBtn = findViewById(R.id.nav_sign_in_button);
        if (navSignInBtn != null && accent != 0) {
            navSignInBtn.setBackgroundTintList(ColorStateList.valueOf(accent));
            navSignInBtn.setTextColor(Color.WHITE);
        }
        
        int[] navTabIds = {R.id.nav_tab_launch, R.id.nav_tab_instances, R.id.nav_tab_settings};
        for (int id : navTabIds) {
            TextView navTab = findViewById(id);
            if (navTab != null && id == R.id.nav_tab_settings && accent != 0) {
                navTab.setTextColor(accent);
                navTab.setTypeface(navTab.getTypeface(), android.graphics.Typeface.BOLD);
                androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(navTab, ColorStateList.valueOf(accent));
            }
        }
    }


    private void setupNavBar() {
        setActiveNavTab(R.id.nav_tab_settings);
        findViewById(R.id.nav_tab_settings).setOnClickListener(v -> {});
    }

}
