// * meineGleitzeit Version 1.19 03.05.2026
// * Copyright (c) 2026 Michael Formanski
// * Leverkusen, Germany
// * Licensed under the MIT License.
// * See LICENSE file in the project root for full license information.
package de.leverbursche.meinegleitzeitapp;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import android.content.Intent;
import android.net.Uri;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.TooltipCompat;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity
        implements BlockFragment.BlockListener {

    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private ImageButton addBlockButton;
    private BlockPagerAdapter adapter1;
    private BlockPagerAdapter adapter2;
    private int currentJobIndex = 1;
    private TabLayoutMediator mediator;
    private AlertDialog verlaufDialog;
    private Menu optionsMenu;
    private ActivityResultLauncher<Intent> createDocumentLauncher;
    private String verlaufExportText = "";
    /** BlockIndex des fehlerhaften Tabs pro Job (-1 = kein Fehler). */
    private int job1FehlerBlockIndex = -1;
    private int job2FehlerBlockIndex = -1;
    /** Berechnetes Dienstende pro Job in Minuten seit Mitternacht (-1 = unbekannt). */
    private int job1DiensteEnde = -1;
    private int job2DiensteEnde = -1;
    private String job1AlarmZeit = null;
    private String job2AlarmZeit = null;
    private ImageButton sidebarAlarm;
    private static final int REQ_NOTIFICATION = 42;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                            if (os != null) {
                                os.write(verlaufExportText.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                                android.widget.Toast.makeText(this, R.string.verlauf_gespeichert,
                                        android.widget.Toast.LENGTH_SHORT).show();
                            }
                        } catch (IOException e) {
                            android.widget.Toast.makeText(this, e.getMessage(),
                                    android.widget.Toast.LENGTH_LONG).show();
                        }
                    }
                });
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_main);

        viewPager = findViewById(R.id.viewPager);
        tabLayout = findViewById(R.id.tabLayout);
        addBlockButton = findViewById(R.id.addBlockButton);

        currentJobIndex = getPreferences(MODE_PRIVATE).getInt("currentJobIndex", 1);

        adapter1 = new BlockPagerAdapter(this, 1, loadBlockIndices(1));
        adapter2 = new BlockPagerAdapter(this, 2, loadBlockIndices(2));

        viewPager.setAdapter(currentAdapter());
        viewPager.setOffscreenPageLimit(7);

        mediator = new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> setupTabView(tab, position));
        mediator.attach();

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                int pos = tab.getPosition();
                if (isTabGesperrt(pos)) {
                    // Zurück zum fehlerhaften Tab navigieren
                    int fehlerIdx = currentJobIndex == 1 ? job1FehlerBlockIndex : job2FehlerBlockIndex;
                    BlockPagerAdapter adp = currentAdapter();
                    for (int i = 0; i < adp.getItemCount(); i++) {
                        if (adp.getBlockIndex(i) == fehlerIdx) {
                            // Create a local final copy of the current index
                            final int targetItem = i;

                            // Use the final copy 'targetItem' instead of 'i'
                            viewPager.post(() -> viewPager.setCurrentItem(targetItem, false));
                            break;
                        }
                    }
                    return;
                }
                updateTabColor(tab, true);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {
                if (!isTabGesperrt(tab.getPosition())) {
                    updateTabColor(tab, false);
                }
            }
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setDisplayUseLogoEnabled(true);

            int size = (int) (46 * getResources().getDisplayMetrics().density);
            // Hier fügen wir den zusätzlichen Platz hinzu (z.B. 12dp Abstand)
            int paddingRight = (int) (12 * getResources().getDisplayMetrics().density);
            int totalWidth = size + paddingRight;

            android.graphics.drawable.Drawable drawable = androidx.core.content.ContextCompat.getDrawable(this, R.mipmap.ic_launcher_round);

            // Das Bitmap wird breiter erstellt als das Icon hoch ist
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(totalWidth, size, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);

            // Das Icon wird weiterhin bei 0,0 mit seiner ursprünglichen Größe gezeichnet
            if (drawable != null) {
                drawable.setBounds(0, 0, size, size);
                drawable.draw(canvas);
            }

            // Das Logo setzen (enthält jetzt den unsichtbaren Abstand rechts)
            getSupportActionBar().setLogo(new android.graphics.drawable.BitmapDrawable(getResources(), bitmap));
        }

        int lastTab = getPreferences(MODE_PRIVATE).getInt("job_" + currentJobIndex + "_lastTab", 0);
        if (lastTab > 0 && lastTab < currentAdapter().getItemCount()) {
            viewPager.setCurrentItem(lastTab, false);
        }

        addBlockButton.setOnClickListener(v -> addBlock());
        findViewById(R.id.infoButton).setOnClickListener(v -> zeigeZeitraumInfoDialog());
        View vTagSpeichern = findViewById(R.id.sidebarTagSpeichern);
        vTagSpeichern.setOnClickListener(v -> triggerMenuItem(R.id.tag_speichern));
        TooltipCompat.setTooltipText(vTagSpeichern, getString(R.string.tag_speichern));
        View vVerlauf = findViewById(R.id.sidebarVerlauf);
        vVerlauf.setOnClickListener(v -> triggerMenuItem(R.id.verlauf));
        TooltipCompat.setTooltipText(vVerlauf, getString(R.string.verlauf));
        View vSchliessen = findViewById(R.id.sidebarZeitraeumeSchliessen);
        vSchliessen.setOnClickListener(v -> triggerMenuItem(R.id.alleZeitraeumeLoeschen));
        TooltipCompat.setTooltipText(vSchliessen, getString(R.string.alle_zeitraeume_loeschen));
        View vJobWechseln = findViewById(R.id.sidebarJobWechseln);
        vJobWechseln.setOnClickListener(v -> triggerMenuItem(R.id.arbeitsstelle));
        TooltipCompat.setTooltipText(vJobWechseln, getString(R.string.arbeitsstelle_menue));
        sidebarAlarm = findViewById(R.id.sidebarAlarm);
        sidebarAlarm.setOnClickListener(v -> onSidebarAlarmClick());
        View vHilfe = findViewById(R.id.sidebarHilfe);
        vHilfe.setOnClickListener(v -> triggerMenuItem(R.id.hilfe));
        TooltipCompat.setTooltipText(vHilfe, getString(R.string.hilfe_menue));
        erstelleNotificationChannel();
        aktualisiereAlarmIcon();
        updateAddButtonState();
        updateSubtitle();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveBlockIndices();
        getPreferences(MODE_PRIVATE).edit()
                .putInt("currentJobIndex", currentJobIndex)
                .putInt("job_" + currentJobIndex + "_lastTab", viewPager.getCurrentItem())
                .apply();
    }

    // === BlockFragment.BlockListener ===

    @Override
    public int getPreviousBlocksArbeitszeit(int jobIndex, int blockIndex) {
        BlockPagerAdapter adp = jobIndex == 1 ? adapter1 : adapter2;
        int total = 0;
        for (int i = 0; i < adp.getItemCount(); i++) {
            int idx = adp.getBlockIndex(i);
            if (idx >= blockIndex) continue;
            Fragment f = getSupportFragmentManager()
                    .findFragmentByTag("f" + itemId(jobIndex, idx));
            if (f instanceof BlockFragment) {
                total += ((BlockFragment) f).getArbeitszeitMinuten();
            }
        }
        return total;
    }

    @Override
    public int getPreviousBlockDiensteende(int jobIndex, int blockIndex) {
        BlockPagerAdapter adp = jobIndex == 1 ? adapter1 : adapter2;
        for (int i = 1; i < adp.getItemCount(); i++) {
            if (adp.getBlockIndex(i) == blockIndex) {
                int prevIdx = adp.getBlockIndex(i - 1);
                // Live-Fragment zuerst
                Fragment f = getSupportFragmentManager()
                        .findFragmentByTag("f" + itemId(jobIndex, prevIdx));
                if (f instanceof BlockFragment) {
                    BlockFragment prev = (BlockFragment) f;
                    String deS = prev.getDiensteEndeStunde();
                    String deM = prev.getDiensteEndeMinute();
                    if (!deS.isEmpty() && !deM.isEmpty()) {
                        try {
                            return Integer.parseInt(deS) * 60 + Integer.parseInt(deM);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                // Fallback: Prefs
                SharedPreferences prefs = getSharedPreferences(
                        blockPrefsName(jobIndex, prevIdx), MODE_PRIVATE);
                String deS = prefs.getString("letzterDienstendeStunde", "");
                String deM = prefs.getString("letzterDienstendeMinute", "");
                if (!deS.isEmpty() && !deM.isEmpty()) {
                    try {
                        return Integer.parseInt(deS) * 60 + Integer.parseInt(deM);
                    } catch (NumberFormatException ignored) {}
                }
                return -1;
            }
        }
        return -1;
    }

    @Override
    public int getPreviousInterBlockPausen(int jobIndex, int blockIndex) {
        BlockPagerAdapter adp = jobIndex == 1 ? adapter1 : adapter2;
        String prefix = "job_" + jobIndex + "_auto_pause_";
        int minZwischenpause = getPreferences(MODE_PRIVATE).getInt(prefix + "min_zwischenpause", 15);
        int total = 0;
        int prevDienstende = -1;
        for (int i = 0; i < adp.getItemCount(); i++) {
            int idx = adp.getBlockIndex(i);
            // Dienstbeginn dieses Blocks ermitteln
            int db = -1;
            Fragment f = getSupportFragmentManager().findFragmentByTag("f" + itemId(jobIndex, idx));
            if (f instanceof BlockFragment) {
                BlockFragment bf = (BlockFragment) f;
                String dbS = bf.getDienstbeginnStunde();
                String dbM = bf.getDienstbeginnMinute();
                if (!dbS.isEmpty() && !dbM.isEmpty()) {
                    try { db = Integer.parseInt(dbS) * 60 + Integer.parseInt(dbM); } catch (NumberFormatException ignored) {}
                }
            }
            if (db == -1) {
                android.content.SharedPreferences bp = getSharedPreferences(blockPrefsName(jobIndex, idx), MODE_PRIVATE);
                String lb = bp.getString("letzterDienstbeginn", "");
                if (lb.length() >= 3) {
                    try { db = Integer.parseInt(lb.substring(0, lb.length() - 2)) * 60 + Integer.parseInt(lb.substring(lb.length() - 2)); } catch (NumberFormatException ignored) {}
                }
            }
            // Lücke zum Vorgänger addieren, nur wenn Mindestdauer erreicht
            if (prevDienstende >= 0 && db >= 0) {
                int gap = db - prevDienstende;
                if (gap < 0) gap += 1440; // Mitternachtsüberschreitung
                if (gap >= minZwischenpause) total += gap;
            }
            if (idx == blockIndex) break;
            // Dienstende dieses Blocks für nächste Iteration merken
            prevDienstende = -1;
            if (f instanceof BlockFragment) {
                BlockFragment bf = (BlockFragment) f;
                String deS = bf.getDiensteEndeStunde();
                String deM = bf.getDiensteEndeMinute();
                if (!deS.isEmpty() && !deM.isEmpty()) {
                    try { prevDienstende = Integer.parseInt(deS) * 60 + Integer.parseInt(deM); } catch (NumberFormatException ignored) {}
                }
            }
            if (prevDienstende == -1) {
                android.content.SharedPreferences bp = getSharedPreferences(blockPrefsName(jobIndex, idx), MODE_PRIVATE);
                String deS = bp.getString("letzterDienstendeStunde", "");
                String deM = bp.getString("letzterDienstendeMinute", "");
                if (!deS.isEmpty() && !deM.isEmpty()) {
                    try { prevDienstende = Integer.parseInt(deS) * 60 + Integer.parseInt(deM); } catch (NumberFormatException ignored) {}
                }
            }
        }
        return total;
    }

    @Override
    public boolean isLastBlock(int jobIndex, int blockIndex) {
        BlockPagerAdapter adp = jobIndex == 1 ? adapter1 : adapter2;
        return adp.getItemCount() > 0
                && adp.getBlockIndex(adp.getItemCount() - 1) == blockIndex;
    }

    @Override
    public void onBlockBerechnet(int jobIndex, int blockIndex) {
        if (jobIndex == currentJobIndex) markNachfolgerTabsNormal(blockIndex);
        BlockPagerAdapter adp = jobIndex == 1 ? adapter1 : adapter2;
        for (int i = 0; i < adp.getItemCount() - 1; i++) {
            if (adp.getBlockIndex(i) == blockIndex) {
                int nextIdx = adp.getBlockIndex(i + 1);
                BlockFragment next = getBlockFragment(jobIndex, nextIdx);
                if (next != null && next.allesFelderAusgefuellt()) {
                    next.berechneStill();
                }
                return;
            }
        }
    }

    @Override
    public void onVorgaengerNeuberechnung(int jobIndex, int blockIndex) {
        BlockPagerAdapter adp = jobIndex == 1 ? adapter1 : adapter2;
        for (int i = 0; i < adp.getItemCount(); i++) {
            int idx = adp.getBlockIndex(i);
            if (idx >= blockIndex) break;
            BlockFragment f = getBlockFragment(jobIndex, idx);
            if (f != null && f.allesFelderAusgefuellt()) {
                f.berechneStill();
            }
        }
    }

    @Override
    public void onBlockFehler(int jobIndex, int blockIndex) {
        if (jobIndex == 1) job1FehlerBlockIndex = blockIndex;
        else job2FehlerBlockIndex = blockIndex;
        if (jobIndex != currentJobIndex) return;
        BlockPagerAdapter adp = currentAdapter();
        for (int i = 0; i < adp.getItemCount(); i++) {
            if (adp.getBlockIndex(i) == blockIndex) {
                for (int j = i + 1; j < adp.getItemCount(); j++) {
                    setTabGesperrt(tabLayout.getTabAt(j), true);
                }
                return;
            }
        }
    }

    @Override
    public void onSollGeaendert(int jobIndex, String stunde, String minute) {
        if (stunde.isEmpty() || minute.isEmpty()) return;
        BlockPagerAdapter adp = jobIndex == 1 ? adapter1 : adapter2;
        for (int i = 0; i < adp.getItemCount(); i++) {
            int idx = adp.getBlockIndex(i);
            if (idx == 1) continue;
            BlockFragment f = getBlockFragment(jobIndex, idx);
            if (f != null) f.setSollWerte(stunde, minute);
        }
    }

    @Override
    public void onDienstendeBerechnet(int jobIndex, int diensteEndeMinuten) {
        if (jobIndex == 1) job1DiensteEnde = diensteEndeMinuten;
        else job2DiensteEnde = diensteEndeMinuten;
        if (isAlarmGesetzt(jobIndex)) setzeAlarm(diensteEndeMinuten);
        else aktualisiereAlarmIcon();
    }

    @Override
    public void markNachfolgerTabsRot(int jobIndex, int blockIndex) {
        if (jobIndex != currentJobIndex) return;
        BlockPagerAdapter adp = currentAdapter();
        for (int i = 0; i < adp.getItemCount(); i++) {
            if (adp.getBlockIndex(i) == blockIndex) {
                for (int j = i + 1; j < adp.getItemCount(); j++) {
                    int nachfolgerIdx = adp.getBlockIndex(j);
                    getSharedPreferences(blockPrefsName(currentJobIndex, nachfolgerIdx), MODE_PRIVATE)
                            .edit().putString("dirty", "1").apply();
                    setTabTextColor(tabLayout.getTabAt(j), 0xFFF44336);
                }
                return;
            }
        }
    }

    @Override
    public void loescheNachfolgerErgebnisse(int jobIndex, int blockIndex) {
        BlockPagerAdapter adp = jobIndex == 1 ? adapter1 : adapter2;
        for (int i = 0; i < adp.getItemCount(); i++) {
            if (adp.getBlockIndex(i) == blockIndex) {
                for (int j = i + 1; j < adp.getItemCount(); j++) {
                    int nachfolgerIdx = adp.getBlockIndex(j);
                    getSharedPreferences(blockPrefsName(jobIndex, nachfolgerIdx), MODE_PRIVATE)
                            .edit()
                            .remove("letzteErzielteZeit")
                            .remove("letzterSaldo")
                            .apply();
                    BlockFragment frag = getBlockFragment(jobIndex, nachfolgerIdx);
                    if (frag != null) frag.clearErgebnis();
                }
                return;
            }
        }
    }

    private void markNachfolgerTabsNormal(int blockIndex) {
        int fehlerIdx = currentJobIndex == 1 ? job1FehlerBlockIndex : job2FehlerBlockIndex;
        if (fehlerIdx == blockIndex) {
            if (currentJobIndex == 1) job1FehlerBlockIndex = -1;
            else job2FehlerBlockIndex = -1;
        }
        BlockPagerAdapter adp = currentAdapter();
        for (int i = 0; i < adp.getItemCount(); i++) {
            if (adp.getBlockIndex(i) == blockIndex) {
                for (int j = i + 1; j < adp.getItemCount(); j++) {
                    int nachfolgerIdx = adp.getBlockIndex(j);
                    getSharedPreferences(blockPrefsName(currentJobIndex, nachfolgerIdx), MODE_PRIVATE)
                            .edit().putString("dirty", "0").apply();
                    TabLayout.Tab tab = tabLayout.getTabAt(j);
                    setTabGesperrt(tab, false);
                    setTabTextColor(tab, tab != null && tab.isSelected() ? 0xFF4CAF50 : 0xFFAAAAAA);
                }
                return;
            }
        }
    }

    // === Tab-Verwaltung ===

    private void setupTabView(TabLayout.Tab tab, int position) {
        View customView = getLayoutInflater().inflate(R.layout.tab_item, null);
        TextView tabText = customView.findViewById(R.id.tabText);
        ImageButton closeBtn = customView.findViewById(R.id.tabClose);

        tabText.setText(getString(R.string.block_titel, position + 1));
        int blockIdx = currentAdapter().getBlockIndex(position);
        boolean dirty = "1".equals(getSharedPreferences(blockPrefsName(currentJobIndex, blockIdx), MODE_PRIVATE)
                .getString("dirty", "0"));
        boolean gesperrt = isTabGesperrt(position);
        if (gesperrt) {
            tabText.setTextColor(0xFFF44336);
            tabText.setAlpha(0.45f);
        } else {
            tabText.setTextColor(dirty ? 0xFFF44336 : (tab.isSelected() ? 0xFF4CAF50 : 0xFFAAAAAA));
            tabText.setAlpha(1.0f);
        }

        if (position > 0 && position == currentAdapter().getItemCount() - 1) {
            closeBtn.setVisibility(View.VISIBLE);
            closeBtn.setOnClickListener(v -> removeBlockByIndex(blockIdx));
        } else {
            closeBtn.setVisibility(View.GONE);
        }

        tab.setCustomView(customView);
    }

    private void updateTabColor(TabLayout.Tab tab, boolean selected) {
        View customView = tab.getCustomView();
        if (customView == null) return;
        TextView tabText = customView.findViewById(R.id.tabText);
        if (tabText != null) {
            tabText.setTextColor(selected ? 0xFF4CAF50 : 0xFFAAAAAA);
        }
    }

    private void setTabTextColor(TabLayout.Tab tab, int color) {
        if (tab == null) return;
        View cv = tab.getCustomView();
        if (cv == null) return;
        TextView tv = cv.findViewById(R.id.tabText);
        if (tv != null) tv.setTextColor(color);
    }

    private void setTabGesperrt(TabLayout.Tab tab, boolean gesperrt) {
        if (tab == null) return;
        View cv = tab.getCustomView();
        if (cv == null) return;
        TextView tv = cv.findViewById(R.id.tabText);
        if (tv != null) {
            tv.setAlpha(gesperrt ? 0.45f : 1.0f);
            if (gesperrt) tv.setTextColor(0xFFF44336);
        }
    }

    private boolean isTabGesperrt(int position) {
        int fehlerIdx = currentJobIndex == 1 ? job1FehlerBlockIndex : job2FehlerBlockIndex;
        if (fehlerIdx < 0) return false;
        BlockPagerAdapter adp = currentAdapter();
        for (int i = 0; i < adp.getItemCount(); i++) {
            if (adp.getBlockIndex(i) == fehlerIdx) {
                return position > i;
            }
        }
        return false;
    }

    private void rebuildAllTabs() {
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            if (tab != null) setupTabView(tab, i);
        }
    }

    private void updateAddButtonState() {
        addBlockButton.setAlpha(currentAdapter().canAddBlock() ? 1.0f : 0.3f);
    }

    // === Block hinzufügen / entfernen ===

    private void addBlock() {
        if (!currentAdapter().canAddBlock()) return;

        // Soll-Werte aus Block 1 holen (live oder aus Prefs)
        String[] soll = getSollFromBlock1();

        if (isAlarmGesetzt(currentJobIndex)) loescheAlarmStill();

        // Letzten Block berechnen – bei Fehler keinen neuen Block öffnen
        int lastPosition = currentAdapter().getItemCount() - 1;
        int lastBlockIndex = currentAdapter().getBlockIndex(lastPosition);
        BlockFragment lastFragment = getBlockFragment(lastBlockIndex);
        if (lastFragment != null && !lastFragment.berechnen()) return;
        String deStunde = lastFragment != null ? lastFragment.getDiensteEndeStunde() : "";
        String deMinute = lastFragment != null ? lastFragment.getDiensteEndeMinute() : "";
        if (deStunde.isEmpty()) {
            SharedPreferences lastPrefs = getSharedPreferences(
                    blockPrefsName(currentJobIndex, lastBlockIndex), MODE_PRIVATE);
            deStunde = lastPrefs.getString("letzterDienstendeStunde", "");
            deMinute = lastPrefs.getString("letzterDienstendeMinute", "");
        }

        int newBlockIndex = currentAdapter().addBlock();
        int newPosition = currentAdapter().getItemCount() - 1;
        // Alter letzter Block ist jetzt nicht mehr letzter → Auto-Pause-Label ausblenden
        if (lastFragment != null) lastFragment.aktualisierePauseHint();
        loeschePreviousErgebnisseBeiAutoPause(newPosition);

        // Dienstbeginn vorab in Prefs des neuen Blocks schreiben,
        // damit loadSavedData() ihn beim Fragment-Aufbau direkt findet
        if (!deStunde.isEmpty()) {
            getSharedPreferences(blockPrefsName(currentJobIndex, newBlockIndex), MODE_PRIVATE)
                    .edit()
                    .putString("letzterDienstbeginn", deStunde + deMinute)
                    .apply();
        }

        // Neuen Tab konfigurieren
        TabLayout.Tab newTab = tabLayout.getTabAt(newPosition);
        if (newTab != null) setupTabView(newTab, newPosition);

        viewPager.setCurrentItem(newPosition, true);

        // Soll setzen, sobald das Fragment erstellt ist
        final String[] sollFinal = soll;
        viewPager.post(() -> {
            BlockFragment newFragment = getBlockFragment(newBlockIndex);
            if (newFragment != null) {
                newFragment.setSollWerte(sollFinal[0], sollFinal[1]);
            }
        });

        if (newPosition >= 1)
            android.widget.Toast.makeText(this, R.string.hinweis_neuer_zeitraum, android.widget.Toast.LENGTH_LONG).show();

        updateAddButtonState();
    }

    private void loeschePreviousErgebnisseBeiAutoPause(int bisPosition) {
        if (!getPreferences(MODE_PRIVATE).getBoolean("job_" + currentJobIndex + "_auto_pause", false)) return;
        for (int i = 0; i < bisPosition; i++) {
            BlockFragment f = getBlockFragment(currentAdapter().getBlockIndex(i));
            if (f != null) f.clearErgebnis();
        }
    }

    private void loescheAlleZeitraeumeAusserErsten() {
        loescheAlarmStill();
        if (currentJobIndex == 1) job1FehlerBlockIndex = -1;
        else job2FehlerBlockIndex = -1;
        while (currentAdapter().getItemCount() > 1) {
            int lastPos = currentAdapter().getItemCount() - 1;
            int lastIdx = currentAdapter().getBlockIndex(lastPos);
            getSharedPreferences(blockPrefsName(currentJobIndex, lastIdx), MODE_PRIVATE)
                    .edit().clear().apply();
            currentAdapter().removeBlock(lastPos);
        }
        viewPager.setCurrentItem(0, false);
        viewPager.post(() -> {
            rebuildAllTabs();
            BlockFragment first = getBlockFragment(currentAdapter().getBlockIndex(0));
            if (first != null) first.clearErgebnis();
        });
        updateAddButtonState();
    }

    private void removeBlockByIndex(int blockIndex) {
        for (int i = 0; i < currentAdapter().getItemCount(); i++) {
            if (currentAdapter().getBlockIndex(i) == blockIndex) {
                removeBlock(i);
                return;
            }
        }
    }

    private void removeBlock(int position) {
        if (position <= 0 || position >= currentAdapter().getItemCount()) return;
        int removedIndex = currentAdapter().getBlockIndex(position);
        boolean wasLast = (position == currentAdapter().getItemCount() - 1);
        int newLastIndex = wasLast ? currentAdapter().getBlockIndex(position - 1) : -1;
        getSharedPreferences(blockPrefsName(currentJobIndex, removedIndex), MODE_PRIVATE)
                .edit().clear().apply();
        currentAdapter().removeBlock(position);
        loescheAlarmStill();
        loeschePreviousErgebnisseBeiAutoPause(currentAdapter().getItemCount());
        // Tabs nach der Änderung neu aufbauen (Positionen können sich verschoben haben)
        viewPager.post(this::rebuildAllTabs);
        // Neuer letzter Block → Auto-Pause-Label aktualisieren
        if (wasLast) {
            viewPager.post(() -> {
                BlockFragment newLast = getBlockFragment(newLastIndex);
                if (newLast != null) newLast.aktualisierePauseHint();
            });
        }
        updateAddButtonState();
    }

    // === Job wechseln ===

    private void switchToJob(int newJobIndex) {
        if (newJobIndex == currentJobIndex) return;
        saveBlockIndices();
        currentJobIndex = newJobIndex;
        mediator.detach();
        viewPager.setAdapter(currentAdapter());
        viewPager.setOffscreenPageLimit(7);
        mediator = new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> setupTabView(tab, position));
        mediator.attach();
        updateAddButtonState();
        updateSubtitle();
        invalidateOptionsMenu();
        aktualisiereAlarmIcon();
    }

    private void updateSubtitle() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(getJobName(currentJobIndex));
        }
    }

    private String getJobName(int jobIndex) {
        String defaultName = getString(R.string.arbeitsstelle_format, jobIndex);
        return getPreferences(MODE_PRIVATE).getString("job_" + jobIndex + "_name", defaultName);
    }

    private void saveJobName(int jobIndex, String name) {
        String trimmed = name.trim();
        String value = trimmed.isEmpty() ? getString(R.string.arbeitsstelle_format, jobIndex) : trimmed;
        getPreferences(MODE_PRIVATE).edit()
                .putString("job_" + jobIndex + "_name", value)
                .apply();
    }

    private void zeigeJobsBenennenDialog() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int dp16 = (int)(16 * getResources().getDisplayMetrics().density);
        int dp8  = (int)(8  * getResources().getDisplayMetrics().density);
        layout.setPadding(dp16 * 2, dp16, dp16 * 2, dp8);

        android.widget.TextView label1 = new android.widget.TextView(this);
        label1.setText(getString(R.string.arbeitsstelle_format, 1) + ":");
        android.widget.EditText edit1 = new android.widget.EditText(this);
        edit1.setText(getJobName(1));
        edit1.setSingleLine(true);

        android.widget.TextView label2 = new android.widget.TextView(this);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp16;
        label2.setLayoutParams(lp);
        label2.setText(getString(R.string.arbeitsstelle_format, 2) + ":");
        android.widget.EditText edit2 = new android.widget.EditText(this);
        edit2.setText(getJobName(2));
        edit2.setSingleLine(true);

        layout.addView(label1);
        layout.addView(edit1);
        layout.addView(label2);
        layout.addView(edit2);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.jobs_benennen))
                .setView(layout)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    saveJobName(1, edit1.getText().toString());
                    saveJobName(2, edit2.getText().toString());
                    updateSubtitle();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void zeigeArbeitsStelleDialog() {
        String[] optionen = {getJobName(1), getJobName(2)};
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.arbeitsstelle_waehlen))
                .setSingleChoiceItems(optionen, currentJobIndex - 1, (dialog, which) -> {
                    dialog.dismiss();
                    switchToJob(which + 1);
                })
                .show();
    }

    // === Hilfsmethoden ===

    private BlockPagerAdapter currentAdapter() {
        return currentJobIndex == 1 ? adapter1 : adapter2;
    }

    private long itemId(int jobIndex, int blockIndex) {
        return (long)(jobIndex - 1) * 100 + blockIndex;
    }

    private String blockPrefsName(int jobIndex, int blockIndex) {
        return "job_" + jobIndex + "_block_" + blockIndex + "_prefs";
    }

    private BlockFragment getBlockFragment(int blockIndex) {
        return getBlockFragment(currentJobIndex, blockIndex);
    }

    private BlockFragment getBlockFragment(int jobIndex, int blockIndex) {
        Fragment f = getSupportFragmentManager()
                .findFragmentByTag("f" + itemId(jobIndex, blockIndex));
        return (f instanceof BlockFragment) ? (BlockFragment) f : null;
    }

    private String[] getSollFromBlock1() {
        BlockFragment b1 = getBlockFragment(1);
        if (b1 != null) {
            String stunde = b1.getSollStunde();
            String minute = b1.getSollMinute();
            if (!stunde.isEmpty()) return new String[]{stunde, minute};
        }
        // Fallback: gespeicherte Werte
        SharedPreferences prefs = getSharedPreferences(
                blockPrefsName(currentJobIndex, 1), MODE_PRIVATE);
        String soll = prefs.getString("letzteSollarbeitszeit", "");
        if (soll.length() == 3) return new String[]{soll.substring(0, 1), soll.substring(1, 3)};
        if (soll.length() == 4) return new String[]{soll.substring(0, 2), soll.substring(2, 4)};
        return new String[]{"", ""};
    }

    // === Persistenz Block-Indizes ===

    private List<Integer> loadBlockIndices(int jobIndex) {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String newKey = "job_" + jobIndex + "_blockIndices";
        String saved;
        // Migration: Job 1 liest alten Key "blockIndices", falls neuer Key noch nicht existiert
        if (jobIndex == 1 && !prefs.contains(newKey)) {
            saved = prefs.getString("blockIndices", "1");
        } else {
            saved = prefs.getString(newKey, "1");
        }
        List<Integer> indices = new ArrayList<>();
        for (String s : saved.split(",")) {
            try { indices.add(Integer.parseInt(s.trim())); }
            catch (NumberFormatException ignored) {}
        }
        if (indices.isEmpty()) indices.add(1);
        return indices;
    }

    private void saveBlockIndices() {
        List<Integer> indices = currentAdapter().getBlockIndices();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indices.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(indices.get(i));
        }
        getPreferences(MODE_PRIVATE).edit()
                .putString("job_" + currentJobIndex + "_blockIndices", sb.toString())
                .apply();
    }

    // === Menü ===
    private void triggerMenuItem(int itemId) {
        if (optionsMenu != null) {
            MenuItem item = optionsMenu.findItem(itemId);
            if (item != null) onOptionsItemSelected(item);
        }
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        optionsMenu = menu;
        getMenuInflater().inflate(R.menu.menu_gleitzeit, menu);

        String format = getPreferences(MODE_PRIVATE).getString("zeitformat", "natuerlich");

        // Wir suchen die Items direkt im Menü-Baum
        MenuItem itemNatuerlich = menu.findItem(R.id.zeitformat_natuerlich);
        MenuItem itemIndustrie = menu.findItem(R.id.zeitformat_industrie);

        if (itemNatuerlich != null && itemIndustrie != null) {
            if ("industrie".equals(format)) {
                itemIndustrie.setChecked(true);
            } else {
                itemNatuerlich.setChecked(true);
            }
        }

        MenuItem itemAutoPause = menu.findItem(R.id.auto_pause);
        if (itemAutoPause != null) {
            boolean autoPause = getPreferences(MODE_PRIVATE).getBoolean("job_" + currentJobIndex + "_auto_pause", false);
            itemAutoPause.setChecked(autoPause);
        }
        return true;
    }

    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        if (menu != null) {
            try {
                java.lang.reflect.Method method = menu.getClass().getDeclaredMethod("setOptionalIconsVisible", Boolean.TYPE);
                method.setAccessible(true);
                method.invoke(menu, true);
            } catch (Exception ignored) {}
        }
        return super.onMenuOpened(featureId, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.beenden) {
            finish();
            return true;
        } else if (id == R.id.auto_pause) {
            zeigeAutoPauseDialog();
            return true;
        } else if (id == R.id.zeitformat_natuerlich || id == R.id.zeitformat_industrie) {
            String neuerWert = (id == R.id.zeitformat_industrie) ? "industrie" : "natuerlich";
            getPreferences(MODE_PRIVATE).edit().putString("zeitformat", neuerWert).apply();
            item.setChecked(true);
            // Alle aktiven Fragmente direkt aktualisieren (kein recreate nötig)
            for (int ji = 1; ji <= 2; ji++) {
                BlockPagerAdapter adp = ji == 1 ? adapter1 : adapter2;
                for (int i = 0; i < adp.getItemCount(); i++) {
                    BlockFragment f = getBlockFragment(ji, adp.getBlockIndex(i));
                    if (f != null) f.refreshFormat();
                }
            }
            return true;
        } else if (id == R.id.felderLoeschen) {
            BlockFragment current = getBlockFragment(
                    currentAdapter().getBlockIndex(viewPager.getCurrentItem()));
            if (current != null) current.felderLoeschen();
            loescheAlarmStill();
            return true;
        } else if (id == R.id.alleZeitraeumeLoeschen) {
            if (currentAdapter().getItemCount() > 1) {
                new AlertDialog.Builder(this)
                        .setMessage(getString(R.string.alle_zeitraeume_loeschen_bestaetigung))
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> loescheAlleZeitraeumeAusserErsten())
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
            return true;
        } else if (id == R.id.arbeitsstelle) {
            zeigeArbeitsStelleDialog();
            return true;
        } else if (id == R.id.jobs_benennen) {
            zeigeJobsBenennenDialog();
            return true;
        } else if (id == R.id.sprache) {
            zeigeSprachDialog();
            return true;
        } else if (id == R.id.tag_speichern) {
            speichereTagImVerlauf();
            return true;
        } else if (id == R.id.verlauf) {
            zeigeVerlaufDialog();
            return true;
        } else if (id == R.id.hilfe) {
            zeigeHilfeDialog();
            return true;
        } else if (id == R.id.ueber) {
            zeigeUeberDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void zeigeAutoPauseDialog() {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String prefix = "job_" + currentJobIndex + "_auto_pause_";
        String keyAktiv = "job_" + currentJobIndex + "_auto_pause";
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_auto_pause, null);

        SwitchCompat sw = dialogView.findViewById(R.id.switchAutoPause);
        sw.setChecked(prefs.getBoolean(keyAktiv, false));

        int[] abIds    = {R.id.editAb0, R.id.editAb1, R.id.editAb2, R.id.editAb3};
        int[] pauseIds = {R.id.editPause0, R.id.editPause1, R.id.editPause2, R.id.editPause3};
        int[] defaultsAb    = {360, 540, -1, -1};
        int[] defaultsPause = {30,  15,  -1, -1};

        android.widget.RadioGroup radioGroupModus = dialogView.findViewById(R.id.radioGroupModus);
        boolean isDifferenziell = "differenziell".equals(prefs.getString(prefix + "modus", "pauschal"));
        radioGroupModus.check(isDifferenziell ? R.id.radioDifferenziell : R.id.radioPauschal);

        EditText[] editAb    = new EditText[4];
        EditText[] editPause = new EditText[4];
        for (int i = 0; i < 4; i++) {
            editAb[i]    = dialogView.findViewById(abIds[i]);
            editPause[i] = dialogView.findViewById(pauseIds[i]);
            int ab    = prefs.getInt(prefix + "tier_" + i + "_ab",    defaultsAb[i]);
            int pause = prefs.getInt(prefix + "tier_" + i + "_pause", defaultsPause[i]);
            if (ab > 0)    editAb[i].setText(String.valueOf(ab));
            if (pause >= 0) editPause[i].setText(String.valueOf(pause));
        }

        EditText editMinZwischenpause = dialogView.findViewById(R.id.editMinZwischenpause);
        int minZwischenpause = prefs.getInt(prefix + "min_zwischenpause", 15);
        editMinZwischenpause.setText(String.valueOf(minZwischenpause));

        new AlertDialog.Builder(this)
                .setTitle(R.string.auto_pause_menue)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean(keyAktiv, sw.isChecked());
                    String modus = (radioGroupModus.getCheckedRadioButtonId() == R.id.radioDifferenziell)
                            ? "differenziell" : "pauschal";
                    editor.putString(prefix + "modus", modus);
                    for (int i = 0; i < 4; i++) {
                        String abStr    = editAb[i].getText().toString().trim();
                        String pauseStr = editPause[i].getText().toString().trim();
                        int ab    = abStr.isEmpty()    ? -1 : Integer.parseInt(abStr);
                        int pause = pauseStr.isEmpty() ? -1 : Integer.parseInt(pauseStr);
                        editor.putInt(prefix + "tier_" + i + "_ab",    ab);
                        editor.putInt(prefix + "tier_" + i + "_pause", pause);
                    }
                    String minZwStr = editMinZwischenpause.getText().toString().trim();
                    editor.putInt(prefix + "min_zwischenpause", minZwStr.isEmpty() ? 0 : Integer.parseInt(minZwStr));
                    editor.apply();
                    invalidateOptionsMenu();
                    BlockPagerAdapter adp = currentJobIndex == 1 ? adapter1 : adapter2;
                    for (int i = 0; i < adp.getItemCount(); i++) {
                        BlockFragment f = getBlockFragment(currentJobIndex, adp.getBlockIndex(i));
                        if (f == null) continue;
                        f.aktualisierePauseHint();
                        if (f.allesFelderAusgefuellt()) {
                            f.berechneStill();
                        } else {
                            f.clearErgebnis();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void zeigeSprachDialog() {
        String[] sprachTags = {"cs", "da", "de", "en", "es", "fr", "it", "nl", "pl", "sv", "tr", "uk", "zh", "ar", ""};
        String[] optionen = {"Čeština", "Dansk", "Deutsch", "English", "Español", "Français", "Italiano", "Nederlands", "Polski", "Svenska", "Türkçe", "Українська", "中文", "العربية", getString(R.string.systemsprache)};

        LocaleListCompat aktuelleLocales = AppCompatDelegate.getApplicationLocales();
        String aktuellerTag = aktuelleLocales.isEmpty() ? "" : aktuelleLocales.get(0).getLanguage();
        int aktuelleAuswahl = sprachTags.length - 1; // Default: Systemsprache
        for (int i = 0; i < sprachTags.length - 1; i++) {
            if (sprachTags[i].equals(aktuellerTag)) {
                aktuelleAuswahl = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.sprache_waehlen))
                .setSingleChoiceItems(optionen, aktuelleAuswahl, (dialog, which) -> {
                    LocaleListCompat locales = sprachTags[which].isEmpty()
                            ? LocaleListCompat.getEmptyLocaleList()
                            : LocaleListCompat.forLanguageTags(sprachTags[which]);
                    AppCompatDelegate.setApplicationLocales(locales);
                    dialog.dismiss();
                    recreate();
                })
                .show();
    }

    // === Verlauf ===

    private void speichereTagImVerlauf() {
        for (int i = 0; i < currentAdapter().getItemCount(); i++) {
            BlockFragment f = getBlockFragment(currentAdapter().getBlockIndex(i));
            if (f != null && f.hasPendingChanges()) {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setMessage(R.string.hinweis_berechnen_vor_speichern)
                        .setPositiveButton(R.string.hinweis_verstanden, null)
                        .show();
                return;
            }
        }
        int lastPos = currentAdapter().getItemCount() - 1;
        int lastIdx = currentAdapter().getBlockIndex(lastPos);
        SharedPreferences lastPrefs = getSharedPreferences(blockPrefsName(currentJobIndex, lastIdx), MODE_PRIVATE);
        String erzieltText = lastPrefs.getString("letzteErzielteZeit", "");
        String saldoText = lastPrefs.getString("letzterSaldo", "");
        if (erzieltText.isEmpty()) {
            android.widget.Toast.makeText(this, R.string.verlauf_keine_berechnung, android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        // Alle Blöcke durchlaufen: DB, DE, Pause je Block sammeln
        List<String[]> blockDataList = new ArrayList<>();
        int runningArbeitszeit = 0;
        for (int i = 0; i < currentAdapter().getItemCount(); i++) {
            int idx = currentAdapter().getBlockIndex(i);
            String bDb = "", bDe = "";
            int bPause = 0;
            boolean bAutoPause = false;
            BlockFragment f = getBlockFragment(idx);
            if (f != null) {
                String dbS = f.getDienstbeginnStunde(), dbM = f.getDienstbeginnMinute();
                if (!dbS.isEmpty()) { if (dbS.length() == 1) dbS = "0" + dbS; if (dbM.length() == 1) dbM = "0" + dbM; bDb = dbS + ":" + dbM; }
                String deS = f.getDiensteEndeStunde(), deM = f.getDiensteEndeMinute();
                if (!deS.isEmpty()) { if (deS.length() == 1) deS = "0" + deS; if (deM.length() == 1) deM = "0" + deM; bDe = deS + ":" + deM; }
                bPause = f.getPauseMinuten(i == lastPos, runningArbeitszeit);
                bAutoPause = (i == lastPos) && f.isAutoPauseAktiv();
                int bAutoPauseMin = bAutoPause ? f.getAutoPauseMinutenWirksam(runningArbeitszeit) : 0;
                runningArbeitszeit += f.getArbeitszeitMinuten();
                blockDataList.add(new String[]{bDb, bDe, String.valueOf(bPause), bAutoPause ? "A" : "", String.valueOf(bAutoPauseMin)});
                continue;
            } else {
                SharedPreferences bp = getSharedPreferences(blockPrefsName(currentJobIndex, idx), MODE_PRIVATE);
                String lb = bp.getString("letzterDienstbeginn", "");
                if (lb.length() >= 3) { String h = lb.substring(0, lb.length() - 2); if (h.length() == 1) h = "0" + h; bDb = h + ":" + lb.substring(lb.length() - 2); }
                String deS = bp.getString("letzterDienstendeStunde", ""), deM = bp.getString("letzterDienstendeMinute", "");
                if (!deS.isEmpty()) { if (deS.length() == 1) deS = "0" + deS; if (deM.length() == 1) deM = "0" + deM; bDe = deS + ":" + deM; }
                String p = bp.getString("letztePause", "0");
                try { bPause = Integer.parseInt(p); } catch (NumberFormatException ignored) {}
            }
            blockDataList.add(new String[]{bDb, bDe, String.valueOf(bPause), bAutoPause ? "A" : "", "0"});
        }
        String dbDisplay = blockDataList.isEmpty() ? "" : blockDataList.get(0)[0];
        String deDisplay = blockDataList.isEmpty() ? "" : blockDataList.get(blockDataList.size() - 1)[1];
        int pauseGesamt = 0;
        StringBuilder blocksBuilder = new StringBuilder();
        for (int i = 0; i < blockDataList.size(); i++) {
            String[] bd = blockDataList.get(i);
            pauseGesamt += Integer.parseInt(bd[2]);
            if (i > 0) blocksBuilder.append(";");
            blocksBuilder.append(bd[0]).append("~").append(bd[1]).append("~").append(bd[2]);
            if (bd.length > 3 && "A".equals(bd[3])) {
                blocksBuilder.append("~A");
                if (bd.length > 4) blocksBuilder.append("~").append(bd[4]);
            }
        }
        String[] sollArr = getSollFromBlock1();
        String sollText = (!sollArr[0].isEmpty() && !sollArr[1].isEmpty())
                ? sollArr[0] + ":" + sollArr[1] : "";
        final String fDb = dbDisplay, fDe = deDisplay, fErzielt = erzieltText, fSaldo = saldoText, fSoll = sollText;
        final int fPause = pauseGesamt;
        final String fBlocks = blocksBuilder.toString();
        java.util.Calendar cal = java.util.Calendar.getInstance();
        new android.app.DatePickerDialog(this, (dpView, year, month, day) -> {
            String dateKey = String.format(java.util.Locale.US, "%04d-%02d-%02d", year, month + 1, day);
            java.util.Calendar sel = java.util.Calendar.getInstance();
            sel.set(year, month, day);
            String dateDisplay = android.text.format.DateFormat.getDateFormat(this).format(sel.getTime());
            doSpeichereTag(dateKey, dateDisplay, fDb, fDe, fPause, fErzielt, fSaldo, fSoll, fBlocks);
        }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show();
    }

    private void doSpeichereTag(String dateKey, String dateDisplay, String db, String de, int pauseMin, String erzieltText, String saldoText, String sollText, String blocksData) {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String historyKey = "job_" + currentJobIndex + "_verlauf_" + dateKey;
        String datesKey = "job_" + currentJobIndex + "_verlauf_dates";
        boolean exists = prefs.contains(historyKey);
        String msg = exists
                ? getString(R.string.tag_ueberschreiben, dateDisplay)
                : getString(R.string.tag_speichern_bestaetigung, dateDisplay);
        new AlertDialog.Builder(this)
                .setMessage(msg)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String entry = db + "|" + de + "|" + pauseMin + "|" + erzieltText + "|" + saldoText + "|" + sollText;
                    prefs.edit()
                            .putString(historyKey, entry)
                            .putString(historyKey + "_blocks", blocksData)
                            .apply();
                    if (!exists) {
                        String dates = prefs.getString(datesKey, "");
                        if (dates.isEmpty()) {
                            dates = dateKey;
                        } else {
                            List<String> dateList = new ArrayList<>(java.util.Arrays.asList(dates.split(",")));
                            int pos = 0;
                            while (pos < dateList.size() && dateList.get(pos).compareTo(dateKey) > 0) pos++;
                            dateList.add(pos, dateKey);
                            StringBuilder sb = new StringBuilder();
                            for (int di = 0; di < dateList.size(); di++) {
                                if (di > 0) sb.append(",");
                                sb.append(dateList.get(di));
                            }
                            dates = sb.toString();
                        }
                        prefs.edit().putString(datesKey, dates).apply();
                    }
                    android.widget.Toast.makeText(this, R.string.tag_gespeichert, android.widget.Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void zeigeVerlaufDialog() {
        if (verlaufDialog != null && verlaufDialog.isShowing()) verlaufDialog.dismiss();
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String datesKey = "job_" + currentJobIndex + "_verlauf_dates";
        String datesStr = prefs.getString(datesKey, "");
        float dp = getResources().getDisplayMetrics().density;
        int pad = (int)(12 * dp);
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(pad, pad, pad, pad);
        scrollView.addView(layout);
        int totalMinutes = 0;
        int totalSollMinutes = 0;
        int totalSaldoMinutes = 0;
        boolean hasSaldo = false;
        boolean hasAutoPause = false;
        if (datesStr.isEmpty()) {
            android.widget.TextView empty = new android.widget.TextView(this);
            empty.setText(R.string.verlauf_leer);
            layout.addView(empty);
        } else {
            for (String dateKey : datesStr.split(",")) {
                if (dateKey.isEmpty()) continue;
                String historyKey = "job_" + currentJobIndex + "_verlauf_" + dateKey;
                String entryValue = prefs.getString(historyKey, "");
                if (entryValue.isEmpty()) continue;
                String[] parts = entryValue.split("\\|", 6);
                if (parts.length < 5) continue;
                String dateDisplay = dateKey;
                try {
                    java.util.Date d = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(dateKey);
                    if (d != null) {
                        String dayName = new java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(d);
                        dateDisplay = dayName + ", " + android.text.format.DateFormat.getDateFormat(this).format(d);
                    }
                } catch (Exception ignored) {}
                android.widget.LinearLayout entry = new android.widget.LinearLayout(this);
                entry.setOrientation(android.widget.LinearLayout.VERTICAL);
                android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, 0, (int)(8 * dp));
                entry.setLayoutParams(lp);
                android.widget.TextView dateView = new android.widget.TextView(this);
                dateView.setText(dateDisplay);
                dateView.setTypeface(null, android.graphics.Typeface.BOLD);
                entry.addView(dateView);
                String blocksData = prefs.getString("job_" + currentJobIndex + "_verlauf_" + dateKey + "_blocks", "");
                int entryIstMin = calcArbeitszeitMin(parts, blocksData);
                totalMinutes += entryIstMin;
                String[] blocks = blocksData.isEmpty() ? new String[0] : blocksData.split(";");
                if (blocks.length > 1) {
                    int autoPauseLineMin = -1;
                    for (int bi = 0; bi < blocks.length; bi++) {
                        String[] bd = blocks[bi].split("~", 5);
                        String bDb = bd.length > 0 ? bd[0] : "";
                        String bDe = bd.length > 1 ? bd[1] : "";
                        String bPause = bd.length > 2 ? bd[2] : "0";
                        boolean bAuto = bd.length > 3 && "A".equals(bd[3]);
                        int bAutoPauseMin = 0;
                        if (bAuto && bd.length > 4) {
                            try { bAutoPauseMin = Integer.parseInt(bd[4]); } catch (NumberFormatException ignored) {}
                            autoPauseLineMin = bAutoPauseMin;
                        }
                        String displayPause = bPause;
                        if (bi == blocks.length - 1 && bAutoPauseMin > 0) {
                            try { displayPause = String.valueOf(Math.max(0, Integer.parseInt(bPause) - bAutoPauseMin)); } catch (NumberFormatException ignored) {}
                        }
                        android.widget.TextView blockView = new android.widget.TextView(this);
                        blockView.setText(getString(R.string.block_titel, bi + 1) + ": "
                                + bDb + " \u2013 " + bDe + "   "
                                + getString(R.string.verlauf_pause_label) + " " + displayPause + " min");
                        entry.addView(blockView);
                    }
                    if (autoPauseLineMin >= 0) {
                        hasAutoPause = true;
                        android.widget.TextView apView = new android.widget.TextView(this);
                        apView.setText(getString(R.string.verlauf_auto_pause_abzug, autoPauseLineMin));
                        entry.addView(apView);
                    }
                } else {
                    String[] singleBd = blocks.length == 1 ? blocks[0].split("~", 5) : new String[0];
                    boolean singleAuto = singleBd.length > 3 && "A".equals(singleBd[3]);
                    int singleAutoPauseMin = -1;
                    if (singleAuto && singleBd.length > 4) {
                        try { singleAutoPauseMin = Integer.parseInt(singleBd[4]); } catch (NumberFormatException ignored) {}
                    }
                    String singleDisplayPause = parts[2];
                    if (singleAuto && singleAutoPauseMin > 0) {
                        try { singleDisplayPause = String.valueOf(Math.max(0, Integer.parseInt(parts[2]) - singleAutoPauseMin)); } catch (NumberFormatException ignored) {}
                    }
                    android.widget.TextView timesView = new android.widget.TextView(this);
                    timesView.setText(getString(R.string.verlauf_beginn) + " " + parts[0] + "   "
                            + getString(R.string.verlauf_ende) + " " + parts[1] + "   "
                            + getString(R.string.verlauf_pause_label) + " " + singleDisplayPause + " min");
                    entry.addView(timesView);
                    if (singleAutoPauseMin >= 0) {
                        hasAutoPause = true;
                        android.widget.TextView apView = new android.widget.TextView(this);
                        apView.setText(getString(R.string.verlauf_auto_pause_abzug, singleAutoPauseMin));
                        entry.addView(apView);
                    }
                }
                int entrySollMin = 0;
                boolean thisHasSoll = false;
                if (parts.length >= 6 && !parts[5].isEmpty()) {
                    try {
                        String[] sp = parts[5].split(":");
                        entrySollMin = Integer.parseInt(sp[0]) * 60 + Integer.parseInt(sp[1]);
                        totalSollMinutes += entrySollMin;
                        totalSaldoMinutes += entryIstMin - entrySollMin;
                        hasSaldo = true;
                        thisHasSoll = true;
                    } catch (Exception ignored) {}
                    android.widget.TextView sollView = new android.widget.TextView(this);
                    String sollDisplay = thisHasSoll ? formatVerlaufSollMin(entrySollMin) : parts[5];
                    sollView.setText(getString(R.string.verlauf_soll_label) + " " + sollDisplay);
                    entry.addView(sollView);
                }
                android.widget.TextView resultView = new android.widget.TextView(this);
                String resultText = formatVerlaufZeit(entryIstMin);
                if (thisHasSoll) resultText += formatVerlaufSaldo(entryIstMin - entrySollMin);
                resultView.setText(resultText);
                entry.addView(resultView);
                android.widget.Button deleteBtn = new android.widget.Button(this);
                deleteBtn.setText(R.string.verlauf_loeschen);
                final String fKey = dateKey, fDisplay = dateDisplay;
                deleteBtn.setOnClickListener(v -> new AlertDialog.Builder(this)
                        .setMessage(getString(R.string.tag_loeschen_bestaetigung, fDisplay))
                        .setPositiveButton(android.R.string.ok, (dd, ww) -> {
                            getPreferences(MODE_PRIVATE).edit()
                                    .remove("job_" + currentJobIndex + "_verlauf_" + fKey)
                                    .remove("job_" + currentJobIndex + "_verlauf_" + fKey + "_blocks")
                                    .apply();
                            String ds = getPreferences(MODE_PRIVATE).getString(datesKey, "");
                            ds = ds.replace(fKey + ",", "").replace("," + fKey, "").replace(fKey, "");
                            getPreferences(MODE_PRIVATE).edit().putString(datesKey, ds).apply();
                            zeigeVerlaufDialog();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show());
                entry.addView(deleteBtn);
                android.view.View divider = new android.view.View(this);
                android.widget.LinearLayout.LayoutParams divLp = new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1);
                divLp.setMargins(0, (int)(4 * dp), 0, (int)(4 * dp));
                divider.setLayoutParams(divLp);
                divider.setBackgroundColor(0xFFCCCCCC);
                layout.addView(entry);
                layout.addView(divider);
            }
            if (totalSollMinutes > 0 || totalMinutes > 0) {
                android.view.View dividerTotal = new android.view.View(this);
                android.widget.LinearLayout.LayoutParams divTotalLp = new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 2);
                divTotalLp.setMargins(0, (int)(4 * dp), 0, (int)(4 * dp));
                dividerTotal.setLayoutParams(divTotalLp);
                dividerTotal.setBackgroundColor(0xFF888888);
                layout.addView(dividerTotal);
                // Einheiten und Label aus den Format-Strings extrahieren
                String sollTemplate = getResources().getString(R.string.verlauf_gesamt_soll);
                String istTemplate = getResources().getString(R.string.verlauf_gesamt);
                String sollLabel = sollTemplate.contains("%") ? sollTemplate.substring(0, sollTemplate.indexOf('%')).trim() : sollTemplate;
                String istLabel = istTemplate.contains("%") ? istTemplate.substring(0, istTemplate.indexOf('%')).trim() : istTemplate;
                java.util.regex.Matcher unitMatcher = java.util.regex.Pattern.compile("%1\\$d(.*?)%2\\$d(.*)").matcher(sollTemplate);
                String hUnit = "h ", minUnit = "min";
                if (unitMatcher.find()) { hUnit = unitMatcher.group(1); minUnit = unitMatcher.group(2); }
                int totalSollH = totalSollMinutes / 60, totalSollM = totalSollMinutes % 60;
                int totalH = totalMinutes / 60, totalM = totalMinutes % 60;
                int absSaldoH = Math.abs(totalSaldoMinutes) / 60, absSaldoM = Math.abs(totalSaldoMinutes) % 60;
                int hWidth = String.valueOf(Math.max(Math.max(totalSollH, totalH), hasSaldo ? absSaldoH : 0)).length();
                android.widget.TableLayout tableTotal = new android.widget.TableLayout(this);
                tableTotal.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
                tableTotal.setPadding(0, (int)(4 * dp), 0, 0);
                if (totalSollMinutes > 0) {
                    android.widget.TableRow rowSoll = new android.widget.TableRow(this);
                    android.widget.TextView lblSoll = new android.widget.TextView(this);
                    lblSoll.setTypeface(null, android.graphics.Typeface.BOLD);
                    lblSoll.setText(sollLabel);
                    lblSoll.setPadding(0, 0, (int)(8 * dp), 0);
                    android.widget.TextView valSoll = new android.widget.TextView(this);
                    valSoll.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
                    valSoll.setGravity(android.view.Gravity.END);
                    String valSollStr = formatVerlaufGesamtWert(totalSollMinutes);
                    valSoll.setText(valSollStr != null ? valSollStr : String.format(java.util.Locale.getDefault(), "%" + hWidth + "d", totalSollH) + hUnit + String.format(java.util.Locale.getDefault(), "%2d", totalSollM) + minUnit);
                    rowSoll.addView(lblSoll);
                    rowSoll.addView(valSoll);
                    tableTotal.addView(rowSoll);
                }
                if (totalMinutes > 0) {
                    android.widget.TableRow rowIst = new android.widget.TableRow(this);
                    android.widget.TextView lblIst = new android.widget.TextView(this);
                    lblIst.setTypeface(null, android.graphics.Typeface.BOLD);
                    lblIst.setText(istLabel);
                    lblIst.setPadding(0, 0, (int)(8 * dp), 0);
                    android.widget.TextView valIst = new android.widget.TextView(this);
                    valIst.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
                    valIst.setGravity(android.view.Gravity.END);
                    String valIstStr = formatVerlaufGesamtWert(totalMinutes);
                    valIst.setText(valIstStr != null ? valIstStr : String.format(java.util.Locale.getDefault(), "%" + hWidth + "d", totalH) + hUnit + String.format(java.util.Locale.getDefault(), "%2d", totalM) + minUnit);
                    rowIst.addView(lblIst);
                    rowIst.addView(valIst);
                    tableTotal.addView(rowIst);
                }
                if (hasSaldo) {
                    android.widget.TableRow rowSaldo = new android.widget.TableRow(this);
                    android.widget.TextView lblSaldo = new android.widget.TextView(this);
                    lblSaldo.setTypeface(null, android.graphics.Typeface.BOLD);
                    String saldoTemplate = getResources().getString(R.string.verlauf_gesamt_saldo);
                    String saldoLabel = saldoTemplate.contains("%") ? saldoTemplate.substring(0, saldoTemplate.indexOf('%')).trim() : saldoTemplate;
                    lblSaldo.setText(saldoLabel);
                    lblSaldo.setPadding(0, 0, (int)(8 * dp), 0);
                    android.widget.TextView valSaldo = new android.widget.TextView(this);
                    valSaldo.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
                    valSaldo.setGravity(android.view.Gravity.END);
                    String sign = totalSaldoMinutes >= 0 ? "+" : "-";
                    String valSaldoStr = formatVerlaufGesamtWert(Math.abs(totalSaldoMinutes));
                    valSaldo.setText(valSaldoStr != null ? sign + valSaldoStr : sign + String.format(java.util.Locale.getDefault(), "%" + hWidth + "d", absSaldoH) + hUnit + String.format(java.util.Locale.getDefault(), "%2d", absSaldoM) + minUnit);
                    rowSaldo.addView(lblSaldo);
                    rowSaldo.addView(valSaldo);
                    tableTotal.addView(rowSaldo);
                }
                layout.addView(tableTotal);
            }
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.verlauf) + " – " + getJobName(currentJobIndex))
                .setView(scrollView)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.verlauf_exportieren, (d, w) -> exportiereVerlauf());
        if (!datesStr.isEmpty()) {
            builder.setNegativeButton(R.string.verlauf_alle_loeschen, (d, w) ->
                    new AlertDialog.Builder(this)
                            .setMessage(R.string.verlauf_alle_loeschen_bestaetigung)
                            .setPositiveButton(android.R.string.ok, (dd, ww) -> loescheAllenVerlauf())
                            .setNegativeButton(android.R.string.cancel, null)
                            .show());
        }
        verlaufDialog = builder.create();
        verlaufDialog.show();
    }

    private void loescheAllenVerlauf() {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String datesKey = "job_" + currentJobIndex + "_verlauf_dates";
        String datesStr = prefs.getString(datesKey, "");
        SharedPreferences.Editor editor = prefs.edit();
        if (!datesStr.isEmpty()) {
            for (String dateKey : datesStr.split(",")) {
                if (dateKey.isEmpty()) continue;
                editor.remove("job_" + currentJobIndex + "_verlauf_" + dateKey);
                editor.remove("job_" + currentJobIndex + "_verlauf_" + dateKey + "_blocks");
            }
        }
        editor.remove(datesKey).apply();
        zeigeVerlaufDialog();
    }

    private void exportiereVerlauf() {
        verlaufExportText = buildVerlaufText();
        String jobName = getJobName(currentJobIndex);
        String filename = jobName.replaceAll("[^a-zA-Z0-9_\\-]", "_") + "_verlauf.txt";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, filename);
        createDocumentLauncher.launch(intent);
    }

    private String buildVerlaufText() {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String datesKey = "job_" + currentJobIndex + "_verlauf_dates";
        String datesStr = prefs.getString(datesKey, "");
        StringBuilder sb = new StringBuilder();
        sb.append("(c) meineGleitzeit Version 1.18\n\n");
        sb.append(getJobName(currentJobIndex)).append("\n");
        sb.append("----------------------------------------\n\n");
        int totalMinutes = 0;
        int totalSollMinutes = 0;
        int totalSaldoMinutes = 0;
        boolean hasSaldo = false;
        boolean hasAutoPause = false;
        if (datesStr.isEmpty()) {
            sb.append(getString(R.string.verlauf_leer)).append("\n");
        } else {
            for (String dateKey : datesStr.split(",")) {
                if (dateKey.isEmpty()) continue;
                String historyKey = "job_" + currentJobIndex + "_verlauf_" + dateKey;
                String entryValue = prefs.getString(historyKey, "");
                if (entryValue.isEmpty()) continue;
                String[] parts = entryValue.split("\\|", 6);
                if (parts.length < 5) continue;
                String dateDisplay = dateKey;
                try {
                    java.util.Date d = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(dateKey);
                    if (d != null) {
                        String dayName = new java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(d);
                        dateDisplay = dayName + ", " + android.text.format.DateFormat.getDateFormat(this).format(d);
                    }
                } catch (Exception ignored) {}
                sb.append(dateDisplay).append("\n");
                String bData = prefs.getString("job_" + currentJobIndex + "_verlauf_" + dateKey + "_blocks", "");
                int entryIstMin = calcArbeitszeitMin(parts, bData);
                totalMinutes += entryIstMin;
                String[] blocks = bData.isEmpty() ? new String[0] : bData.split(";");
                int exportAutoPauseMin = -1;
                if (blocks.length > 1) {
                    for (int bi = 0; bi < blocks.length; bi++) {
                        String[] bd = blocks[bi].split("~", 5);
                        String bDb = bd.length > 0 ? bd[0] : "";
                        String bDe = bd.length > 1 ? bd[1] : "";
                        String bPause = bd.length > 2 ? bd[2] : "0";
                        boolean bAuto = bd.length > 3 && "A".equals(bd[3]);
                        if (bAuto) hasAutoPause = true;
                        int bAutoPauseMinExp = 0;
                        if (bAuto && bd.length > 4) {
                            try { bAutoPauseMinExp = Integer.parseInt(bd[4]); } catch (NumberFormatException ignored) {}
                            exportAutoPauseMin = bAutoPauseMinExp;
                        }
                        String displayPauseExp = bPause;
                        if (bi == blocks.length - 1 && bAutoPauseMinExp > 0) {
                            try { displayPauseExp = String.valueOf(Math.max(0, Integer.parseInt(bPause) - bAutoPauseMinExp)); } catch (NumberFormatException ignored) {}
                        }
                        sb.append(getString(R.string.block_titel, bi + 1)).append(": ")
                                .append(bDb).append(" \u2013 ").append(bDe).append("   ")
                                .append(getString(R.string.verlauf_pause_label)).append(" ").append(displayPauseExp).append(" min")
                                .append("\n");
                    }
                } else {
                    String[] singleBdExp = blocks.length == 1 ? blocks[0].split("~", 5) : new String[0];
                    boolean singleAuto = singleBdExp.length > 3 && "A".equals(singleBdExp[3]);
                    if (singleAuto) hasAutoPause = true;
                    int singleAutoPauseMinExp = 0;
                    if (singleAuto && singleBdExp.length > 4) {
                        try { singleAutoPauseMinExp = Integer.parseInt(singleBdExp[4]); } catch (NumberFormatException ignored) {}
                        exportAutoPauseMin = singleAutoPauseMinExp;
                    }
                    String singleDisplayPauseExp = parts[2];
                    if (singleAuto && singleAutoPauseMinExp > 0) {
                        try { singleDisplayPauseExp = String.valueOf(Math.max(0, Integer.parseInt(parts[2]) - singleAutoPauseMinExp)); } catch (NumberFormatException ignored) {}
                    }
                    sb.append(getString(R.string.verlauf_beginn)).append(" ").append(parts[0]).append("   ");
                    sb.append(getString(R.string.verlauf_ende)).append(" ").append(parts[1]).append("   ");
                    sb.append(getString(R.string.verlauf_pause_label)).append(" ").append(singleDisplayPauseExp).append(" min")
                            .append("\n");
                }
                if (exportAutoPauseMin >= 0) {
                    sb.append(getString(R.string.verlauf_auto_pause_abzug, exportAutoPauseMin)).append("\n");
                }
                int entrySollMinExp = 0;
                boolean thisHasSollExp = false;
                if (parts.length >= 6 && !parts[5].isEmpty()) {
                    try {
                        String[] sp = parts[5].split(":");
                        entrySollMinExp = Integer.parseInt(sp[0]) * 60 + Integer.parseInt(sp[1]);
                        totalSollMinutes += entrySollMinExp;
                        totalSaldoMinutes += entryIstMin - entrySollMinExp;
                        hasSaldo = true;
                        thisHasSollExp = true;
                    } catch (Exception ignored) {}
                    String sollDisplayExp = thisHasSollExp ? formatVerlaufSollMin(entrySollMinExp) : parts[5];
                    sb.append(getString(R.string.verlauf_soll_label)).append(" ").append(sollDisplayExp).append("\n");
                }
                String exportResult = formatVerlaufZeit(entryIstMin);
                if (thisHasSollExp) exportResult += formatVerlaufSaldo(entryIstMin - entrySollMinExp);
                sb.append(exportResult).append("\n\n");
            }
            if (totalSollMinutes > 0 || totalMinutes > 0) {
                sb.append("----------------------------------------\n");
                boolean industrie = isIndustrieFormat();
                if (totalSollMinutes > 0) {
                    if (industrie) {
                        String label = getResources().getString(R.string.verlauf_gesamt_soll).replaceAll("%.*", "").trim();
                        sb.append(label).append(" ").append(String.format(java.util.Locale.getDefault(), "%.2f h", totalSollMinutes / 60f)).append("\n");
                    } else {
                        sb.append(getString(R.string.verlauf_gesamt_soll, totalSollMinutes / 60, totalSollMinutes % 60)).append("\n");
                    }
                }
                if (totalMinutes > 0) {
                    if (industrie) {
                        String label = getResources().getString(R.string.verlauf_gesamt).replaceAll("%.*", "").trim();
                        sb.append(label).append(" ").append(String.format(java.util.Locale.getDefault(), "%.2f h", totalMinutes / 60f)).append("\n");
                    } else {
                        sb.append(getString(R.string.verlauf_gesamt, totalMinutes / 60, totalMinutes % 60)).append("\n");
                    }
                }
                if (hasSaldo) {
                    String sign = totalSaldoMinutes >= 0 ? "+" : "-";
                    if (industrie) {
                        String label = getResources().getString(R.string.verlauf_gesamt_saldo).replaceAll("%.*", "").trim();
                        sb.append(label).append(" ").append(sign).append(String.format(java.util.Locale.getDefault(), "%.2f h", Math.abs(totalSaldoMinutes) / 60f)).append("\n");
                    } else {
                        int absSaldoH = Math.abs(totalSaldoMinutes) / 60, absSaldoM = Math.abs(totalSaldoMinutes) % 60;
                        sb.append(getString(R.string.verlauf_gesamt_saldo, sign, absSaldoH, absSaldoM)).append("\n");
                    }
                }
            }
        }
        return sb.toString();
    }

    private int calcArbeitszeitMin(String[] parts, String blocksData) {
        String[] blocks = blocksData.isEmpty() ? new String[0] : blocksData.split(";");
        if (blocks.length >= 1 && !blocksData.isEmpty()) {
            int total = 0;
            for (String block : blocks) {
                String[] bd = block.split("~", 4);
                if (bd.length < 3) continue;
                total += calcZeitDiff(bd[0], bd[1], bd[2]);
            }
            return total;
        } else if (parts.length >= 3) {
            return calcZeitDiff(parts[0], parts[1], parts[2]);
        }
        return 0;
    }

    private int calcZeitDiff(String db, String de, String pauseStr) {
        try {
            String[] dbP = db.split(":");
            String[] deP = de.split(":");
            if (dbP.length < 2 || deP.length < 2) return 0;
            int dbMin = Integer.parseInt(dbP[0]) * 60 + Integer.parseInt(dbP[1]);
            int deMin = Integer.parseInt(deP[0]) * 60 + Integer.parseInt(deP[1]);
            int pause = Integer.parseInt(pauseStr);
            int diff = deMin - dbMin - pause;
            return diff > 0 ? diff : 0;
        } catch (Exception e) { return 0; }
    }

    private boolean isIndustrieFormat() {
        return "industrie".equals(getPreferences(MODE_PRIVATE).getString("zeitformat", "natuerlich"));
    }

    private String formatVerlaufZeit(int minuten) {
        if (isIndustrieFormat())
            return getString(R.string.erzielte_zeit_format_industrie,
                    String.format(java.util.Locale.getDefault(), "%.2f", minuten / 60f));
        return getString(R.string.erzielte_zeit_format, minuten / 60, minuten % 60);
    }

    private String formatVerlaufSaldo(int saldoMin) {
        String sign = saldoMin >= 0 ? "+" : "-";
        int abs = Math.abs(saldoMin);
        if (isIndustrieFormat())
            return getString(R.string.saldo_format_industrie, sign,
                    String.format(java.util.Locale.getDefault(), "%.2f", abs / 60f));
        return getString(R.string.saldo_format, sign, abs / 60, abs % 60);
    }

    private String formatVerlaufGesamtWert(int minuten) {
        if (isIndustrieFormat())
            return String.format(java.util.Locale.getDefault(), "%.2f h", minuten / 60f);
        return null; // natürliches Format: bestehende Logik verwenden
    }

    private String formatVerlaufSollMin(int minuten) {
        if (isIndustrieFormat())
            return String.format(java.util.Locale.getDefault(), "%.2f h", minuten / 60f);
        return String.format("%02d", minuten / 60) + ":" + String.format("%02d", minuten % 60);
    }

    private void zeigeZeitraumInfoDialog() {
        String text = getString(R.string.zeitraum_info_text);
        android.text.SpannableString spannable = new android.text.SpannableString(text);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '+') {
                spannable.setSpan(new android.text.style.ForegroundColorSpan(0xFF4CAF50), i, i + 1, 0);
            } else if (c == '×') {
                spannable.setSpan(new android.text.style.ForegroundColorSpan(0xFFF44336), i, i + 1, 0);
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.zeitraum_info_titel))
                .setMessage(spannable)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void zeigeHilfeDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.hilfe_dialog_titel))
                .setMessage(machAbschnittstitelFett(getString(R.string.hilfe_dialog_text)))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private SpannableString machAbschnittstitelFett(String text) {
        SpannableString spannable = new SpannableString(text);
        Pattern pattern = Pattern.compile("(?:^|\\n\\n)([^\\n]+:\\n)");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            int start = matcher.start(1);
            int end = matcher.end(1) - 1; // Zeilenumbruch nicht fett
            spannable.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return spannable;
    }

    private void zeigeUeberDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.ueber_dialog_titel))
                .setMessage(getString(R.string.ueber_dialog_text))
                .show();
        ((TextView) dialog.findViewById(android.R.id.message))
                .setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        android.text.util.Linkify.addLinks(
                ((TextView) dialog.findViewById(android.R.id.message)),
                android.text.util.Linkify.WEB_URLS);
    }

    // === Alarm / Erinnerung ===

    private void erstelleNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(
                    AlarmReceiver.CHANNEL_ID,
                    getString(R.string.alarm_kanal_name),
                    NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(channel);
        }
    }

    private PendingIntent getAlarmPendingIntent(int jobIndex) {
        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.putExtra("job_index", jobIndex);
        intent.putExtra("job_name", getJobName(jobIndex));
        return PendingIntent.getBroadcast(this, jobIndex, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private boolean isAlarmGesetzt(int jobIndex) {
        Intent intent = new Intent(this, AlarmReceiver.class);
        return PendingIntent.getBroadcast(this, jobIndex, intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE) != null;
    }

    private void berechneAlleAktuellenBloecke() {
        BlockPagerAdapter adp = currentAdapter();
        int last = adp.getItemCount() - 1;
        for (int i = 0; i <= last; i++) {
            BlockFragment f = getBlockFragment(adp.getBlockIndex(i));
            if (f != null && f.allesFelderAusgefuellt()) {
                if (i == last) {
                    f.berechnen();
                } else {
                    f.berechneStill();
                }
            }
        }
    }

    private void onSidebarAlarmClick() {
        if (isAlarmGesetzt(currentJobIndex)) {
            loescheAlarm();
        } else {
            berechneAlleAktuellenBloecke();
            int de = currentJobIndex == 1 ? job1DiensteEnde : job2DiensteEnde;
            if (de < 0) {
                Toast.makeText(this, R.string.alarm_kein_dienstende, Toast.LENGTH_SHORT).show();
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
                return;
            }
            setzeAlarm(de);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATION &&
                grantResults.length > 0 &&
                grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            int de = currentJobIndex == 1 ? job1DiensteEnde : job2DiensteEnde;
            if (de >= 0) setzeAlarm(de);
        }
    }

    private void setzeAlarm(int diensteEndeMinuten) {
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            Toast.makeText(this, R.string.alarm_keine_berechtigung, Toast.LENGTH_LONG).show();
            Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        }
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, diensteEndeMinuten / 60);
        cal.set(Calendar.MINUTE, diensteEndeMinuten % 60);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), getAlarmPendingIntent(currentJobIndex));
        String zeit = String.format(Locale.getDefault(), "%02d:%02d",
                diensteEndeMinuten / 60, diensteEndeMinuten % 60);
        if (currentJobIndex == 1) job1AlarmZeit = zeit; else job2AlarmZeit = zeit;
        Toast.makeText(this, getString(R.string.alarm_gesetzt, zeit), Toast.LENGTH_SHORT).show();
        aktualisiereAlarmIcon();
    }

    private void loescheAlarm() {
        loescheAlarmStill();
        Toast.makeText(this, R.string.alarm_geloescht, Toast.LENGTH_SHORT).show();
    }

    private void loescheAlarmStill() {
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        am.cancel(getAlarmPendingIntent(currentJobIndex));
        getAlarmPendingIntent(currentJobIndex).cancel();
        if (currentJobIndex == 1) job1AlarmZeit = null; else job2AlarmZeit = null;
        aktualisiereAlarmIcon();
    }

    private void aktualisiereAlarmIcon() {
        if (sidebarAlarm == null) return;
        boolean gesetzt = isAlarmGesetzt(currentJobIndex);
        sidebarAlarm.setImageResource(gesetzt ? R.drawable.ic_menu_alarm_on : R.drawable.ic_menu_alarm);
        String alarmZeit = currentJobIndex == 1 ? job1AlarmZeit : job2AlarmZeit;
        TooltipCompat.setTooltipText(sidebarAlarm, gesetzt && alarmZeit != null
                ? getString(R.string.alarm_gesetzt, alarmZeit)
                : getString(R.string.alarm_icon_hinweis));
    }
}
