// * meineGleitzeit Version 1.5
// * Copyright (c) 2026 Michael Formanski
// * Leverkusen, Germany
// * Licensed under the MIT License.
// * See LICENSE file in the project root for full license information.
package de.leverbursche.meinegleitzeitapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.icu.text.SimpleDateFormat;
import android.icu.util.Calendar;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import java.util.Locale;

public class BlockFragment extends Fragment {

    private static final String ARG_JOB_INDEX = "job_index";
    private static final String ARG_BLOCK_INDEX = "block_index";
    private static final int COLOR_GREEN = 0xFF00FF00;
    private static final int COLOR_RED = 0xFFFF0000;
    private static final int COLOR_ORANGE = 0xFFFF8800;

    /**
     * Wird von MainActivity implementiert.
     * Liefert die Summe der Arbeitszeiten aller Blöcke vor dem angegebenen Block.
     */
    public interface BlockListener {
        int getPreviousBlocksArbeitszeit(int jobIndex, int blockIndex);
        /** Gibt Dienstende des Vorgänger-Blocks in Minuten zurück, oder -1 wenn unbekannt. */
        int getPreviousBlockDiensteende(int jobIndex, int blockIndex);
        /** Wird nach erfolgreicher Berechnung aufgerufen, damit Folgeblöcke neu berechnet werden. */
        void onBlockBerechnet(int jobIndex, int blockIndex);
        /** Gibt true zurück, wenn dieser Block der letzte in der aktuellen Job-Liste ist. */
        boolean isLastBlock(int jobIndex, int blockIndex);
        /** Markiert alle Tabs nach diesem Block rot (Änderung nicht neu berechnet). */
        void markNachfolgerTabsRot(int jobIndex, int blockIndex);
        /** Löscht Berechnungsergebnisse in allen Nachfolger-Blöcken. */
        void loescheNachfolgerErgebnisse(int jobIndex, int blockIndex);
        /** Wird nach Berechnen/Frühestens aufgerufen, damit Vorgänger-Blöcke neu berechnet werden. */
        void onVorgaengerNeuberechnung(int jobIndex, int blockIndex);
        /** Wird aufgerufen, wenn die Berechnung mit einer Fehlermeldung fehlschlug. */
        void onBlockFehler(int jobIndex, int blockIndex);
        /** Gibt die Summe aller Lücken zwischen aufeinanderfolgenden Zeiträumen zurück (in Minuten). */
        int getPreviousInterBlockPausen(int jobIndex, int blockIndex);
        /** Wird aufgerufen, wenn die Soll-Arbeitszeit in Block 1 geändert wurde. */
        void onSollGeaendert(int jobIndex, String stunde, String minute);
        /** Wird aufgerufen wenn der letzte Block erfolgreich berechnet wurde; diensteEndeMinuten = Minuten seit Mitternacht. */
        void onDienstendeBerechnet(int jobIndex, int diensteEndeMinuten);
    }

    private int jobIndex;
    private int blockIndex;
    private BlockListener listener;

    private EditText eingabeDienstbeginnStunde;
    private EditText eingabeDienstbeginnMinute;
    private EditText eingabeDienstendeStunde;
    private EditText eingabeDienstendeMinute;
    private EditText eingabePause;
    private TextView textViewAutoPauseHinweis;
    private EditText eingabeSollarbeitszeitStunde;
    private EditText eingabeSollarbeitszeitMinute;
    private TextView erzielteZeit;
    private TextView sollIndustrieLabel;
    private RadioGroup rgdb;
    private RadioGroup rgde;
    private RadioButton radio_dbjetzt;
    private RadioButton radio_dbletzter;
    private RadioButton radio_dejetzt;
    private RadioButton radio_defruehestens;
    private Button berechnenButton;
    private View rootLayout;
    private int defaultTextColor;
    private boolean validierungAktiv = false;
    private boolean radioDbAktiv = false;
    private boolean radioDeAktiv = false;
    private boolean hinweisGezeigt = false;
    private boolean pendingChanges = false;

    public static BlockFragment newInstance(int jobIndex, int blockIndex) {
        BlockFragment fragment = new BlockFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_JOB_INDEX, jobIndex);
        args.putInt(ARG_BLOCK_INDEX, blockIndex);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof BlockListener) {
            listener = (BlockListener) context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            jobIndex = getArguments().getInt(ARG_JOB_INDEX, 1);
            blockIndex = getArguments().getInt(ARG_BLOCK_INDEX, 1);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_block, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        loadSavedData();
        if (blockIndex == 1) {
            ladeLetztenDienstbeginn();
        } else {
            sperreSollFelder();
        }
        // Radio-Buttons VOR setupListeners setzen, damit keine Neuberechnungen ausgelöst werden
        // Nur setzen, wenn beide Dienstbeginn-Felder befüllt sind
        boolean dbGefuellt = !eingabeDienstbeginnStunde.getText().toString().isEmpty()
                && !eingabeDienstbeginnMinute.getText().toString().isEmpty();
        if (dbGefuellt) {
            String savedRadioDe = getPrefsForBlock().getString("letzterRadioDe", "");
            if (savedRadioDe.equals("2")) {
                radio_defruehestens.setChecked(true);
            } else if (savedRadioDe.equals("1")) {
                radio_dejetzt.setChecked(true);
            }
            if (blockIndex == 1) {
                String savedRadioDb = getPrefsForBlock().getString("letzterRadioDb", "2");
                if (savedRadioDb.equals("1")) {
                    radio_dbjetzt.setChecked(true);
                } else {
                    radio_dbletzter.setChecked(true);
                }
            }
        }
        setupListeners();
        // Gespeichertes Berechnungsergebnis immer anzeigen (unabhängig von Sperrung)
        SharedPreferences blockPrefs = getPrefsForBlock();
        String savedErzieltText;
        String savedSaldoText;
        if (blockPrefs.contains("letzteErzielteMinuten")) {
            int savedMinuten = blockPrefs.getInt("letzteErzielteMinuten", 0);
            int savedSaldo = blockPrefs.getInt("letzterSaldoMinuten", 0);
            String vorzeichen = savedSaldo >= 0 ? "+" : "-";
            boolean industrie = "industrie".equals(
                    requireActivity().getPreferences(Context.MODE_PRIVATE)
                            .getString("zeitformat", "natuerlich"));
            if (industrie) {
                String azStr = String.format(Locale.getDefault(), "%.2f", savedMinuten / 60f);
                savedErzieltText = getString(R.string.erzielte_zeit_format_industrie, azStr);
                String saldoStr = String.format(Locale.getDefault(), "%.2f", Math.abs(savedSaldo) / 60f);
                savedSaldoText = getString(R.string.saldo_format_industrie, vorzeichen, saldoStr);
            } else {
                int azStunden = savedMinuten / 60;
                int azMinuten = savedMinuten % 60;
                savedErzieltText = getString(R.string.erzielte_zeit_format, azStunden, azMinuten);
                int saldoStunden = Math.abs(savedSaldo / 60);
                int saldoMinuten = Math.abs(savedSaldo % 60);
                savedSaldoText = getString(R.string.saldo_format, vorzeichen, saldoStunden, saldoMinuten);
            }
        } else {
            savedErzieltText = blockPrefs.getString("letzteErzielteZeit", "");
            savedSaldoText = blockPrefs.getString("letzterSaldo", "");
        }
        if (!savedErzieltText.isEmpty() && !savedSaldoText.isEmpty()) {
            erzielteZeit.setTextColor(COLOR_GREEN);
            erzielteZeit.setText(savedErzieltText);
            erzielteZeit.append(savedSaldoText);
            if (savedSaldoText.contains("-")) {
                Spannable sp = (Spannable) erzielteZeit.getText();
                int split = savedErzieltText.length();
                sp.setSpan(new ForegroundColorSpan(COLOR_GREEN), 0, split, 0);
                sp.setSpan(new ForegroundColorSpan(COLOR_RED), split, split + savedSaldoText.length(), 0);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (eingabeDienstbeginnStunde.getText().length() > 0
                && eingabeDienstbeginnMinute.getText().length() > 0
                && eingabeSollarbeitszeitStunde.getText().length() > 0
                && eingabeSollarbeitszeitMinute.getText().length() > 0) {
            speichereDaten("letzterDienstbeginn",
                    eingabeDienstbeginnStunde.getText().toString()
                    + eingabeDienstbeginnMinute.getText().toString());
            speichereDaten("letztePause", eingabePause.getText().toString());
            speichereDaten("letzterDienstendeStunde",
                    eingabeDienstendeStunde.getText().toString());
            speichereDaten("letzterDienstendeMinute",
                    eingabeDienstendeMinute.getText().toString());
            speichereDaten("letzterRadioDe",
                    radio_dejetzt.isChecked() ? "1" : (radio_defruehestens.isChecked() ? "2" : "0"));
            if (blockIndex == 1) {
                speichereDaten("letzteSollarbeitszeit",
                        eingabeSollarbeitszeitStunde.getText().toString()
                        + eingabeSollarbeitszeitMinute.getText().toString());
                speichereDaten("letzterRadioDb",
                        radio_dbjetzt.isChecked() ? "1" : (radio_dbletzter.isChecked() ? "2" : "0"));
            }
        }
    }

    // === Initialisierung ===

    private void initViews(View view) {
        eingabeDienstbeginnStunde = view.findViewById(R.id.editTextDienstbeginnStunde);
        eingabeDienstbeginnMinute = view.findViewById(R.id.editTextDienstbeginnMinute);
        eingabeDienstendeStunde = view.findViewById(R.id.editTextDienstendeStunde);
        eingabeDienstendeMinute = view.findViewById(R.id.editTextDienstendeMinute);
        eingabePause = view.findViewById(R.id.editTextPause);
        textViewAutoPauseHinweis = view.findViewById(R.id.textViewAutoPauseHinweis);
        eingabeSollarbeitszeitStunde = view.findViewById(R.id.editTextSollarbeitszeitStunde);
        eingabeSollarbeitszeitMinute = view.findViewById(R.id.editTextSollarbeitszeitMinute);
        erzielteZeit = view.findViewById(R.id.textViewErzielteZeit);
        sollIndustrieLabel = view.findViewById(R.id.textViewSollIndustrie);
        rgdb = view.findViewById(R.id.radio_gruppedb);
        rgde = view.findViewById(R.id.radio_gruppede);
        radio_dbjetzt = view.findViewById(R.id.radio_dbjetzt);
        radio_dbletzter = view.findViewById(R.id.radio_dbletzter);
        radio_dejetzt = view.findViewById(R.id.radio_dejetzt);
        radio_defruehestens = view.findViewById(R.id.radio_defruehestens);
        berechnenButton = view.findViewById(R.id.button);
        rootLayout = view.findViewById(R.id.focusDummy);
        defaultTextColor = eingabeDienstbeginnStunde.getCurrentTextColor();

        setupDoubleTapCursorToEnd(eingabeDienstbeginnStunde);
        setupDoubleTapCursorToEnd(eingabeDienstbeginnMinute);
        setupDoubleTapCursorToEnd(eingabeDienstendeStunde);
        setupDoubleTapCursorToEnd(eingabeDienstendeMinute);
        setupDoubleTapCursorToEnd(eingabePause);
        setupDoubleTapCursorToEnd(eingabeSollarbeitszeitStunde);
        setupDoubleTapCursorToEnd(eingabeSollarbeitszeitMinute);
    }

    private void loadSavedData() {
        if (getPrefsForBlock().contains("letztePause")) {
            String letztePause = leseDaten("letztePause");
            if (letztePause.length() > 0 && letztePause.length() < 4) {
                eingabePause.setText(letztePause);
            }
        }
        if (blockIndex == 1) {
            String letzteSollarbeitszeit = leseDaten("letzteSollarbeitszeit");
            if (letzteSollarbeitszeit.length() == 3) {
                eingabeSollarbeitszeitStunde.setText(letzteSollarbeitszeit.substring(0, 1));
                eingabeSollarbeitszeitMinute.setText(letzteSollarbeitszeit.substring(1, 3));
            } else if (letzteSollarbeitszeit.length() == 4) {
                eingabeSollarbeitszeitStunde.setText(letzteSollarbeitszeit.substring(0, 2));
                eingabeSollarbeitszeitMinute.setText(letzteSollarbeitszeit.substring(2, 4));
            }
            String deStunde = getPrefsForBlock().getString("letzterDienstendeStunde", "");
            String deMinute = getPrefsForBlock().getString("letzterDienstendeMinute", "");
            if (!deStunde.isEmpty()) eingabeDienstendeStunde.setText(deStunde);
            if (!deMinute.isEmpty()) eingabeDienstendeMinute.setText(deMinute);
        } else {
            // Sollarbeitszeit aus Block-1-Prefs laden (für Neustart der App)
            SharedPreferences b1prefs = requireActivity()
                    .getSharedPreferences("job_" + jobIndex + "_block_1_prefs", Context.MODE_PRIVATE);
            String letzteSoll = b1prefs.getString("letzteSollarbeitszeit", "");
            if (letzteSoll.length() == 3) {
                eingabeSollarbeitszeitStunde.setText(letzteSoll.substring(0, 1));
                eingabeSollarbeitszeitMinute.setText(letzteSoll.substring(1, 3));
            } else if (letzteSoll.length() == 4) {
                eingabeSollarbeitszeitStunde.setText(letzteSoll.substring(0, 2));
                eingabeSollarbeitszeitMinute.setText(letzteSoll.substring(2, 4));
            }

            String dbStr = leseDaten("letzterDienstbeginn");
            if (dbStr.length() == 3) {
                eingabeDienstbeginnStunde.setText(dbStr.substring(0, 1));
                eingabeDienstbeginnMinute.setText(dbStr.substring(1, 3));
            } else if (dbStr.length() == 4) {
                eingabeDienstbeginnStunde.setText(dbStr.substring(0, 2));
                eingabeDienstbeginnMinute.setText(dbStr.substring(2, 4));
            }
            String deStunde = getPrefsForBlock().getString("letzterDienstendeStunde", "");
            String deMinute = getPrefsForBlock().getString("letzterDienstendeMinute", "");
            if (!deStunde.isEmpty()) eingabeDienstendeStunde.setText(deStunde);
            if (!deMinute.isEmpty()) eingabeDienstendeMinute.setText(deMinute);
        }
        aktualisiereSollLabel();
        aktualisierePauseHint();
    }

    // === Öffentliche Methoden (aufgerufen von MainActivity) ===

    public boolean isAutoPauseAktiv() {
        return requireActivity().getPreferences(Context.MODE_PRIVATE)
                .getBoolean("job_" + jobIndex + "_auto_pause", false);
    }

    private int gesetzlichePause(int anwesenheitMin) {
        android.content.SharedPreferences prefs =
                requireActivity().getPreferences(android.content.Context.MODE_PRIVATE);
        String prefix = "job_" + jobIndex + "_auto_pause_";
        int[] defaultsAb    = {360, 540, -1, -1};
        int[] defaultsPause = {30,  15,  -1, -1};
        java.util.List<int[]> tiers = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            int ab    = prefs.getInt(prefix + "tier_" + i + "_ab",    defaultsAb[i]);
            int pause = prefs.getInt(prefix + "tier_" + i + "_pause", defaultsPause[i]);
            if (ab > 0 && pause >= 0) tiers.add(new int[]{ab, pause});
        }
        tiers.sort((a, b) -> Integer.compare(a[0], b[0]));
        boolean differenziell = "differenziell".equals(
                prefs.getString(prefix + "modus", "pauschal"));
        int totalPause = 0;
        if (differenziell) {
            int netTime = anwesenheitMin;
            for (int[] tier : tiers) {
                if (netTime > tier[0]) {
                    int added = Math.min(netTime - tier[0], tier[1]);
                    totalPause += added;
                    netTime -= added;
                }
            }
        } else {
            int netTime = anwesenheitMin;
            for (int[] tier : tiers) {
                if (netTime > tier[0]) {
                    totalPause += tier[1];
                    netTime -= tier[1];
                }
            }
        }
        return totalPause;
    }

    /** Summe der Lücken zwischen den Zeiträumen (bereits geleistete Pause). */
    private int getInterBlockPausen() {
        return listener != null ? listener.getPreviousInterBlockPausen(jobIndex, blockIndex) : 0;
    }

    /** Auto-Pause nach Abzug bereits geleisteter Zwischenpausen (nie negativ). */
    private int netAutoPause(int workTimeMin) {
        int interBlock = getInterBlockPausen();
        return Math.max(0, gesetzlichePause(workTimeMin + interBlock) - interBlock);
    }

    public void aktualisierePauseHint() {
        if (eingabePause == null) return;
        boolean aktiv = isAutoPauseAktiv();
        boolean isLast = listener != null && listener.isLastBlock(jobIndex, blockIndex);
        eingabePause.setHint(getString(R.string.minute));
        if (textViewAutoPauseHinweis != null) {
            if (aktiv && isLast) {
                int autoPauseMin = berechneAktuelleAutoPauseSafe();
                textViewAutoPauseHinweis.setText(getString(R.string.auto_pause_abzug_format, autoPauseMin));
                textViewAutoPauseHinweis.setVisibility(View.VISIBLE);
            } else {
                textViewAutoPauseHinweis.setVisibility(View.GONE);
            }
        }
    }

    private int berechneAktuelleAutoPauseSafe() {
        String dbH = eingabeDienstbeginnStunde.getText().toString().trim();
        String dbM = eingabeDienstbeginnMinute.getText().toString().trim();
        String deH = eingabeDienstendeStunde.getText().toString().trim();
        String deM = eingabeDienstendeMinute.getText().toString().trim();
        if (dbH.isEmpty() || dbM.isEmpty() || deH.isEmpty() || deM.isEmpty()) return 0;
        try {
            int db = Integer.parseInt(dbH) * 60 + Integer.parseInt(dbM);
            int de = Integer.parseInt(deH) * 60 + Integer.parseInt(deM);
            int anwesenheit = de - db;
            if (anwesenheit < 0) anwesenheit += 1440;
            int previousArbeitszeit = listener != null
                    ? listener.getPreviousBlocksArbeitszeit(jobIndex, blockIndex) : 0;
            return netAutoPause(anwesenheit + previousArbeitszeit);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void aktualisiereSollLabel() {
        if (sollIndustrieLabel == null) return;
        boolean industrie = "industrie".equals(
                requireActivity().getPreferences(Context.MODE_PRIVATE)
                        .getString("zeitformat", "natuerlich"));
        if (!industrie) {
            sollIndustrieLabel.setVisibility(android.view.View.GONE);
            return;
        }
        String sH = eingabeSollarbeitszeitStunde.getText().toString().trim();
        String sM = eingabeSollarbeitszeitMinute.getText().toString().trim();
        if (sH.isEmpty() || sM.isEmpty()) {
            sollIndustrieLabel.setVisibility(android.view.View.GONE);
            return;
        }
        try {
            int minuten = Integer.parseInt(sH) * 60 + Integer.parseInt(sM);
            sollIndustrieLabel.setText(String.format(Locale.getDefault(), "= %.2f h", minuten / 60f));
            sollIndustrieLabel.setVisibility(android.view.View.VISIBLE);
        } catch (NumberFormatException e) {
            sollIndustrieLabel.setVisibility(android.view.View.GONE);
        }
    }

    /** Sperrt die Soll-Felder (für Blöcke 2+). */
    public void sperreSollFelder() {
        eingabeSollarbeitszeitStunde.setFocusable(false);
        eingabeSollarbeitszeitStunde.setFocusableInTouchMode(false);
        eingabeSollarbeitszeitStunde.setTextColor(0xFFAAAAAA);
        eingabeSollarbeitszeitMinute.setFocusable(false);
        eingabeSollarbeitszeitMinute.setFocusableInTouchMode(false);
        eingabeSollarbeitszeitMinute.setTextColor(0xFFAAAAAA);
    }

    /** Sperrt alle Eingabefelder (Block ist nicht mehr der letzte). */
    public void sperreAlleEingabefelder() {
        for (android.widget.EditText et : new android.widget.EditText[]{
                eingabeDienstbeginnStunde, eingabeDienstbeginnMinute,
                eingabeDienstendeStunde, eingabeDienstendeMinute,
                eingabePause, eingabeSollarbeitszeitStunde, eingabeSollarbeitszeitMinute}) {
            et.setFocusable(false);
            et.setFocusableInTouchMode(false);
            et.setTextColor(0xFFAAAAAA);
        }
        radio_dbjetzt.setEnabled(false);
        radio_dbletzter.setEnabled(false);
        radio_dejetzt.setEnabled(false);
        radio_defruehestens.setEnabled(false);
    }

    /** Entsperrt alle Eingabefelder (Block ist wieder der letzte). */
    public void entsperreAlleEingabefelder() {
        for (android.widget.EditText et : new android.widget.EditText[]{
                eingabeDienstbeginnStunde, eingabeDienstbeginnMinute,
                eingabeDienstendeStunde, eingabeDienstendeMinute, eingabePause}) {
            et.setFocusable(true);
            et.setFocusableInTouchMode(true);
            et.setTextColor(defaultTextColor);
        }
        if (blockIndex == 1) {
            eingabeSollarbeitszeitStunde.setFocusable(true);
            eingabeSollarbeitszeitStunde.setFocusableInTouchMode(true);
            eingabeSollarbeitszeitStunde.setTextColor(defaultTextColor);
            eingabeSollarbeitszeitMinute.setFocusable(true);
            eingabeSollarbeitszeitMinute.setFocusableInTouchMode(true);
            eingabeSollarbeitszeitMinute.setTextColor(defaultTextColor);
        }
        radio_dbjetzt.setEnabled(true);
        radio_dbletzter.setEnabled(true);
        radio_dejetzt.setEnabled(true);
        radio_defruehestens.setEnabled(true);
    }

    /** Setzt die Soll-Werte (übertragen aus Block 1). */
    public void setSollWerte(String stunde, String minute) {
        if (eingabeSollarbeitszeitStunde != null) {
            eingabeSollarbeitszeitStunde.setText(stunde);
            eingabeSollarbeitszeitMinute.setText(minute);
            aktualisiereSollLabel();
            if (radio_defruehestens != null && radio_defruehestens.isChecked()) {
                berechneFruehestesEnde();
            } else if (allesFelderAusgefuellt()) {
                berechneStill();
            }
        }
    }

    /** Belegt die Dienstbeginn-Felder vor (z.B. mit Dienstende des Vorgänger-Blocks). */
    public void setDienstbeginnVorbelegung(String stunde, String minute) {
        if (eingabeDienstbeginnStunde != null && stunde != null && !stunde.isEmpty()) {
            eingabeDienstbeginnStunde.setText(stunde);
            if (minute != null) eingabeDienstbeginnMinute.setText(minute);
        }
    }

    /** Gibt die Arbeitszeit dieses Blocks in Minuten zurück (für Folge-Blöcke).
     *  Kein Auto-Pause-Abzug: Auto-Pause wird nur im letzten Zeitraum angewendet. */
    public int getArbeitszeitMinuten() {
        if (eingabeDienstbeginnStunde == null) return 0;
        String dbS = eingabeDienstbeginnStunde.getText().toString().trim();
        String dbM = eingabeDienstbeginnMinute.getText().toString().trim();
        String deS = eingabeDienstendeStunde.getText().toString().trim();
        String deM = eingabeDienstendeMinute.getText().toString().trim();
        String pauseStr = eingabePause.getText().toString().trim();
        if (dbS.isEmpty() || dbM.isEmpty() || deS.isEmpty() || deM.isEmpty()) return 0;
        try {
            int db = Integer.parseInt(dbS) * 60 + Integer.parseInt(dbM);
            int de = Integer.parseInt(deS) * 60 + Integer.parseInt(deM);
            int anwesenheit = de - db;
            if (anwesenheit < 0) anwesenheit += 1440;
            int pauseManual = pauseStr.isEmpty() ? 0 : Integer.parseInt(pauseStr);
            return Math.max(0, anwesenheit - pauseManual);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public String getDienstbeginnStunde() {
        return eingabeDienstbeginnStunde != null
                ? eingabeDienstbeginnStunde.getText().toString() : "";
    }

    public String getDienstbeginnMinute() {
        return eingabeDienstbeginnMinute != null
                ? eingabeDienstbeginnMinute.getText().toString() : "";
    }

    /** applyAutoPause=true nur für den letzten Zeitraum aufrufen. */
    public int getPauseMinuten(boolean applyAutoPause) {
        return getPauseMinuten(applyAutoPause, 0);
    }

    /** applyAutoPause=true nur für den letzten Zeitraum aufrufen.
     *  previousArbeitszeit: Arbeitszeit aller vorherigen Blöcke (für korrekten Auto-Pause-Schwellwert). */
    public int getPauseMinuten(boolean applyAutoPause, int previousArbeitszeit) {
        if (eingabePause == null) return 0;
        String p = eingabePause.getText().toString().trim();
        int pauseManual = 0;
        if (!p.isEmpty()) {
            try { pauseManual = Integer.parseInt(p); } catch (NumberFormatException e) { return 0; }
        }
        if (applyAutoPause && isAutoPauseAktiv()) {
            String dbS = eingabeDienstbeginnStunde.getText().toString().trim();
            String dbM = eingabeDienstbeginnMinute.getText().toString().trim();
            String deS = eingabeDienstendeStunde.getText().toString().trim();
            String deM = eingabeDienstendeMinute.getText().toString().trim();
            if (dbS.isEmpty() || dbM.isEmpty() || deS.isEmpty() || deM.isEmpty()) return pauseManual;
            try {
                int db = Integer.parseInt(dbS) * 60 + Integer.parseInt(dbM);
                int de = Integer.parseInt(deS) * 60 + Integer.parseInt(deM);
                int anwesenheit = de - db;
                if (anwesenheit < 0) anwesenheit += 1440;
                return pauseManual + netAutoPause(anwesenheit + previousArbeitszeit);
            } catch (NumberFormatException e) { return pauseManual; }
        }
        return pauseManual;
    }

    /** Gibt true zurück, wenn Auto-Pause aktiv ist UND tatsächlich Minuten beisteuert. */
    public boolean isAutoPauseWirksam(boolean applyAutoPause, int previousArbeitszeit) {
        if (!applyAutoPause || !isAutoPauseAktiv()) return false;
        String dbS = eingabeDienstbeginnStunde != null ? eingabeDienstbeginnStunde.getText().toString().trim() : "";
        String dbM = eingabeDienstbeginnMinute != null ? eingabeDienstbeginnMinute.getText().toString().trim() : "";
        String deS = eingabeDienstendeStunde != null ? eingabeDienstendeStunde.getText().toString().trim() : "";
        String deM = eingabeDienstendeMinute != null ? eingabeDienstendeMinute.getText().toString().trim() : "";
        if (dbS.isEmpty() || dbM.isEmpty() || deS.isEmpty() || deM.isEmpty()) return false;
        try {
            int db = Integer.parseInt(dbS) * 60 + Integer.parseInt(dbM);
            int de = Integer.parseInt(deS) * 60 + Integer.parseInt(deM);
            int anwesenheit = de - db;
            if (anwesenheit < 0) anwesenheit += 1440;
            return netAutoPause(anwesenheit + previousArbeitszeit) > 0;
        } catch (NumberFormatException e) { return false; }
    }

    public int getAutoPauseMinutenWirksam(int previousArbeitszeit) {
        if (!isAutoPauseAktiv()) return 0;
        String dbS = eingabeDienstbeginnStunde.getText().toString().trim();
        String dbM = eingabeDienstbeginnMinute.getText().toString().trim();
        String deS = eingabeDienstendeStunde.getText().toString().trim();
        String deM = eingabeDienstendeMinute.getText().toString().trim();
        if (dbS.isEmpty() || dbM.isEmpty() || deS.isEmpty() || deM.isEmpty()) return 0;
        try {
            int db = Integer.parseInt(dbS) * 60 + Integer.parseInt(dbM);
            int de = Integer.parseInt(deS) * 60 + Integer.parseInt(deM);
            int anwesenheit = de - db;
            if (anwesenheit < 0) anwesenheit += 1440;
            return netAutoPause(anwesenheit + previousArbeitszeit);
        } catch (NumberFormatException e) { return 0; }
    }

    public String getDiensteEndeStunde() {
        return eingabeDienstendeStunde != null
                ? eingabeDienstendeStunde.getText().toString() : "";
    }

    public String getDiensteEndeMinute() {
        return eingabeDienstendeMinute != null
                ? eingabeDienstendeMinute.getText().toString() : "";
    }

    public String getSollStunde() {
        return eingabeSollarbeitszeitStunde != null
                ? eingabeSollarbeitszeitStunde.getText().toString() : "";
    }

    public String getSollMinute() {
        return eingabeSollarbeitszeitMinute != null
                ? eingabeSollarbeitszeitMinute.getText().toString() : "";
    }

    /** Löst die Berechnung aus (wie Berechnen-Button). Gibt true zurück wenn erfolgreich. */
    public boolean berechnen() {
        fokusEntziehen();
        boolean ok = berechneGleitzeit();
        if (ok && listener != null) listener.onVorgaengerNeuberechnung(jobIndex, blockIndex);
        return ok;
    }

    /** Berechnung ohne Fokus-Entzug – für Kaskaden-Neuberechnung durch Folgeblöcke. */
    public void berechneStill() {
        berechneGleitzeit();
    }

    public boolean allesFelderAusgefuellt() {
        boolean pauseOk = isAutoPauseAktiv() || !eingabePause.getText().toString().trim().isEmpty();
        return !eingabeDienstbeginnStunde.getText().toString().trim().isEmpty()
                && !eingabeDienstbeginnMinute.getText().toString().trim().isEmpty()
                && !eingabeDienstendeStunde.getText().toString().trim().isEmpty()
                && !eingabeDienstendeMinute.getText().toString().trim().isEmpty()
                && pauseOk
                && !eingabeSollarbeitszeitStunde.getText().toString().trim().isEmpty()
                && !eingabeSollarbeitszeitMinute.getText().toString().trim().isEmpty();
    }

    public void felderLoeschen() {
        rgdb.clearCheck();
        rgde.clearCheck();
        eingabeDienstbeginnStunde.setText("");
        eingabeDienstbeginnMinute.setText("");
        eingabeDienstendeStunde.setText("");
        eingabeDienstendeMinute.setText("");
        eingabePause.setText("");
        if (blockIndex == 1) {
            eingabeSollarbeitszeitStunde.setText("");
            eingabeSollarbeitszeitMinute.setText("");
        }
        erzielteZeit.setTextColor(COLOR_ORANGE);
        erzielteZeit.setText(getString(R.string.fehler_fehlende_zeiten));
        pendingChanges = true;
    }

    public void clearErgebnis() {
        getPrefsForBlock().edit()
                .remove("letzteErzielteZeit")
                .remove("letzterSaldo")
                .apply();
        if (erzielteZeit != null) {
            erzielteZeit.setTextColor(COLOR_ORANGE);
            erzielteZeit.setText(getString(R.string.bitte_neu_berechnen));
        }
        pendingChanges = true;
    }

    // === Listeners ===

    public boolean hasPendingChanges() {
        return pendingChanges;
    }

    /** Aktualisiert Anzeige nach Zeitformat-Wechsel (ohne recreate). */
    public void refreshFormat() {
        aktualisiereSollLabel();
        aktualisierePauseHint();
        if (pendingChanges) return;
        SharedPreferences blockPrefs = getPrefsForBlock();
        if (!blockPrefs.contains("letzteErzielteMinuten")) return;

        int savedMinuten = blockPrefs.getInt("letzteErzielteMinuten", 0);
        int savedSaldo = blockPrefs.getInt("letzterSaldoMinuten", 0);
        String vorzeichen = savedSaldo >= 0 ? "+" : "-";
        boolean industrie = "industrie".equals(
                requireActivity().getPreferences(Context.MODE_PRIVATE)
                        .getString("zeitformat", "natuerlich"));

        String erzieltText;
        String saldoText;
        if (industrie) {
            String azStr = String.format(Locale.getDefault(), "%.2f", savedMinuten / 60f);
            erzieltText = getString(R.string.erzielte_zeit_format_industrie, azStr);
            String saldoStr = String.format(Locale.getDefault(), "%.2f", Math.abs(savedSaldo) / 60f);
            saldoText = getString(R.string.saldo_format_industrie, vorzeichen, saldoStr);
        } else {
            int azStunden = savedMinuten / 60;
            int azMinuten = savedMinuten % 60;
            erzieltText = getString(R.string.erzielte_zeit_format, azStunden, azMinuten);
            int saldoStunden = Math.abs(savedSaldo / 60);
            int saldoMinuten = Math.abs(savedSaldo % 60);
            saldoText = getString(R.string.saldo_format, vorzeichen, saldoStunden, saldoMinuten);
        }

        speichereDaten("letzteErzielteZeit", erzieltText);
        speichereDaten("letzterSaldo", saldoText);

        erzielteZeit.setTextColor(COLOR_GREEN);
        erzielteZeit.setText(erzieltText);
        erzielteZeit.append(saldoText);

        if (savedSaldo < 0) {
            Spannable spannableText = (Spannable) erzielteZeit.getText();
            int textlaenge1 = erzieltText.length();
            spannableText.setSpan(new ForegroundColorSpan(COLOR_GREEN), 0, textlaenge1, 0);
            spannableText.setSpan(new ForegroundColorSpan(COLOR_RED),
                    textlaenge1, textlaenge1 + saldoText.length(), 0);
        }
    }

    private void loescheErgebnisAnzeige() {
        if (!validierungAktiv) {
            erzielteZeit.setText("");
            pendingChanges = true;
        }
    }

    private void zeigeAenderungsHinweis() {
        if (listener == null || listener.isLastBlock(jobIndex, blockIndex)) return;
        listener.markNachfolgerTabsRot(jobIndex, blockIndex);
        listener.loescheNachfolgerErgebnisse(jobIndex, blockIndex);
        if (!hinweisGezeigt) {
            hinweisGezeigt = true;
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setMessage(R.string.hinweis_aenderung_zeitraum)
                    .setPositiveButton(R.string.hinweis_verstanden, null)
                    .show();
        }
    }

    private void setupListeners() {
        eingabeDienstbeginnStunde.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                zeigeAenderungsHinweis();
                rgdb.clearCheck();
                rgde.clearCheck();
                loescheErgebnisAnzeige();
                eingabeDienstbeginnStunde.post(() -> eingabeDienstbeginnStunde.selectAll());
            } else {
                if (!radioDbAktiv)
                    validiereFeld(eingabeDienstbeginnStunde, 0, 23, getString(R.string.fehler_stunden));
            }
        });
        eingabeDienstbeginnMinute.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                zeigeAenderungsHinweis();
                rgdb.clearCheck();
                rgde.clearCheck();
                loescheErgebnisAnzeige();
                eingabeDienstbeginnMinute.post(() -> eingabeDienstbeginnMinute.selectAll());
            } else {
                if (!radioDbAktiv) {
                    formatMinutenFeld(eingabeDienstbeginnMinute);
                    String mText = eingabeDienstbeginnMinute.getText().toString().trim();
                    boolean minuteGueltig = !mText.isEmpty()
                            && istFeldGueltig(eingabeDienstbeginnMinute, 0, 59);
                    validiereFeld(eingabeDienstbeginnMinute, 0, 59, getString(R.string.fehler_minuten));
                    if (minuteGueltig && blockIndex > 1) pruefeDbNachVorgaengerDe();
                }
            }
        });
        eingabeDienstendeStunde.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                zeigeAenderungsHinweis();
                rgde.clearCheck();
                loescheErgebnisAnzeige();
                eingabeDienstendeStunde.post(() -> eingabeDienstendeStunde.selectAll());
            } else {
                if (!radioDeAktiv)
                    validiereFeld(eingabeDienstendeStunde, 0, 23, getString(R.string.fehler_stunden));
            }
        });
        eingabeDienstendeMinute.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                zeigeAenderungsHinweis();
                rgde.clearCheck();
                loescheErgebnisAnzeige();
                eingabeDienstendeMinute.post(() -> eingabeDienstendeMinute.selectAll());
            } else {
                if (!radioDeAktiv) {
                    formatMinutenFeld(eingabeDienstendeMinute);
                    validiereFeld(eingabeDienstendeMinute, 0, 59, getString(R.string.fehler_minuten));
                }
            }
        });
        eingabePause.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                zeigeAenderungsHinweis();
                loescheErgebnisAnzeige();
                rgde.clearCheck();
                eingabePause.post(() -> eingabePause.selectAll());
            } else {
                validiereFeld(eingabePause, 0, 999, getString(R.string.fehler_pause));
            }
        });
        eingabeSollarbeitszeitStunde.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                zeigeAenderungsHinweis();
                loescheErgebnisAnzeige();
                rgde.clearCheck();
                eingabeSollarbeitszeitStunde.post(() -> eingabeSollarbeitszeitStunde.selectAll());
            } else {
                formatMinutenFeld(eingabeSollarbeitszeitMinute);
                validiereFeld(eingabeSollarbeitszeitStunde, 0, 23, getString(R.string.fehler_stunden));
                aktualisiereSollLabel();
                if (blockIndex == 1 && listener != null)
                    listener.onSollGeaendert(jobIndex, getSollStunde(), getSollMinute());
            }
        });
        eingabeSollarbeitszeitStunde.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(android.text.Editable s) { aktualisiereSollLabel(); }
        });
        eingabeSollarbeitszeitMinute.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                zeigeAenderungsHinweis();
                loescheErgebnisAnzeige();
                rgde.clearCheck();
                eingabeSollarbeitszeitMinute.post(() -> eingabeSollarbeitszeitMinute.selectAll());
            } else {
                formatMinutenFeld(eingabeSollarbeitszeitMinute);
                validiereFeld(eingabeSollarbeitszeitMinute, 0, 59, getString(R.string.fehler_minuten));
                aktualisiereSollLabel();
                if (blockIndex == 1 && listener != null)
                    listener.onSollGeaendert(jobIndex, getSollStunde(), getSollMinute());
            }
        });
        eingabeSollarbeitszeitMinute.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(android.text.Editable s) { aktualisiereSollLabel(); }
        });

        // RadioGroup Dienstbeginn
        rgdb.setOnCheckedChangeListener((group, checkedId) -> {
            radioDbAktiv = true;
            if (eingabeDienstbeginnStunde.isFocusable()) eingabeDienstbeginnStunde.setTextColor(defaultTextColor);
            if (eingabeDienstbeginnMinute.isFocusable()) eingabeDienstbeginnMinute.setTextColor(defaultTextColor);
            if (checkedId == R.id.radio_dbjetzt && radio_dbjetzt.isChecked()) {
                fokusEntziehen();
                zeigeAenderungsHinweis();
                setzeAktuelleZeit(eingabeDienstbeginnStunde, eingabeDienstbeginnMinute);
                loescheErgebnisAnzeige();
                if (istFeldGueltig(eingabeDienstendeStunde, 0, 23)
                        && istFeldGueltig(eingabeDienstendeMinute, 0, 59)) {
                    rgde.clearCheck();
                    radio_defruehestens.setChecked(true);
                }
                clearFocusIfTimeField();
            } else if (checkedId == R.id.radio_dbletzter && radio_dbletzter.isChecked()) {
                fokusEntziehen();
                zeigeAenderungsHinweis();
                radio_dbletzter.requestFocus();
                ladeLetztenDienstbeginn();
                loescheErgebnisAnzeige();
                if (istFeldGueltig(eingabeDienstendeStunde, 0, 23)
                        && istFeldGueltig(eingabeDienstendeMinute, 0, 59)) {
                    rgde.clearCheck();
                    radio_defruehestens.setChecked(true);
                }
                clearFocusIfTimeField();
            }
            radioDbAktiv = false;
        });

        // RadioGroup Dienstende
        rgde.setOnCheckedChangeListener((group, checkedId) -> {
            radioDeAktiv = true;
            if (eingabeDienstendeStunde.isFocusable()) eingabeDienstendeStunde.setTextColor(defaultTextColor);
            if (eingabeDienstendeMinute.isFocusable()) eingabeDienstendeMinute.setTextColor(defaultTextColor);
            if (checkedId == R.id.radio_defruehestens) {
                zeigeAenderungsHinweis();
                View current = requireActivity().getCurrentFocus();
                if (current != null
                        && (current.getId() == R.id.editTextDienstendeStunde
                        || current.getId() == R.id.editTextDienstendeMinute)) {
                    current.clearFocus();
                }
                berechneFruehestesEnde();
            } else if (checkedId == R.id.radio_dejetzt) {
                fokusEntziehen();
                zeigeAenderungsHinweis();
                setzeAktuelleZeit(eingabeDienstendeStunde, eingabeDienstendeMinute);
                erzielteZeit.setTextColor(COLOR_GREEN);
                loescheErgebnisAnzeige();
                View current = requireActivity().getCurrentFocus();
                if (current != null
                        && (current.getId() == R.id.editTextDienstendeStunde
                        || current.getId() == R.id.editTextDienstendeMinute)) {
                    current.clearFocus();
                }
            }
            radioDeAktiv = false;
        });

        // Berechnen-Button
        berechnenButton.setOnClickListener(view -> {
            berechnen();
            View current = requireActivity().getCurrentFocus();
            if (current != null) current.clearFocus();
        });

        // Auto-Advance: nach 2 Ziffern automatisch ins nächste Feld springen
        addAutoAdvance(eingabeDienstbeginnStunde, eingabeDienstbeginnMinute);
        addAutoAdvance(eingabeDienstbeginnMinute, eingabeDienstendeStunde);
        addAutoAdvance(eingabeDienstendeStunde, eingabeDienstendeMinute);
        addAutoAdvance(eingabeDienstendeMinute, eingabePause);
        addAutoAdvance(eingabeSollarbeitszeitStunde, eingabeSollarbeitszeitMinute);

        // Auto-Pause-Label live aktualisieren wenn DB oder DE geändert wird
        android.text.TextWatcher autoPauseHintWatcher = new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(android.text.Editable s) { aktualisierePauseHint(); }
        };
        eingabeDienstbeginnStunde.addTextChangedListener(autoPauseHintWatcher);
        eingabeDienstbeginnMinute.addTextChangedListener(autoPauseHintWatcher);
        eingabeDienstendeStunde.addTextChangedListener(autoPauseHintWatcher);
        eingabeDienstendeMinute.addTextChangedListener(autoPauseHintWatcher);
    }

    private void addAutoAdvance(EditText from, EditText to) {
        from.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (s.length() == 2 && from.hasFocus()) {
                    to.requestFocus();
                }
            }
        });
    }

    /** Löscht Fokus falls ein Zeitfeld fokussiert ist. */
    private void clearFocusIfTimeField() {
        View current = requireActivity().getCurrentFocus();
        if (current != null) {
            int id = current.getId();
            if (id == R.id.editTextDienstbeginnStunde
                    || id == R.id.editTextDienstbeginnMinute
                    || id == R.id.editTextDienstendeStunde
                    || id == R.id.editTextDienstendeMinute) {
                current.clearFocus();
            }
        }
    }

    private void setupDoubleTapCursorToEnd(EditText feld) {
        final long[] lastClickTime = {0};
        feld.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (now - lastClickTime[0] < ViewConfiguration.getDoubleTapTimeout()) {
                feld.setSelection(feld.getText().length());
            }
            lastClickTime[0] = now;
        });
    }

    // === Validierung ===

    private void validiereFeld(EditText feld, int min, int max, String fehlermeldung) {
        String text = feld.getText().toString().trim();
        if (text.isEmpty()) {
            feld.setTextColor(defaultTextColor);
            return;
        }
        boolean ungueltig;
        try {
            int wert = Integer.parseInt(text);
            ungueltig = wert < min || wert > max;
        } catch (NumberFormatException e) {
            ungueltig = true;
        }
        if (ungueltig) {
            feld.setTextColor(COLOR_RED);
            feld.post(() -> {
                validierungAktiv = true;
                feld.requestFocus();
                validierungAktiv = false;
                feld.setTextColor(COLOR_RED);
                erzielteZeit.setTextColor(COLOR_RED);
                erzielteZeit.setText(fehlermeldung);
                ((InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE))
                        .hideSoftInputFromWindow(feld.getWindowToken(), 0);
            });
        } else {
            feld.setTextColor(defaultTextColor);
        }
    }

    private boolean pruefeUndMarkiereFeld(EditText feld, int min, int max) {
        String text = feld.getText().toString().trim();
        if (text.isEmpty()) {
            if (feld.isFocusable()) feld.setTextColor(defaultTextColor);
            return true;
        }
        try {
            int wert = Integer.parseInt(text);
            if (wert >= min && wert <= max) {
                if (feld.isFocusable()) feld.setTextColor(defaultTextColor);
                return true;
            }
        } catch (NumberFormatException ignored) {}
        if (feld.isFocusable()) feld.setTextColor(COLOR_RED);
        return false;
    }

    private boolean validiereAlleFelder() {
        EditText erstesUngueltig = null;
        String ersteFehlermeldung = null;
        if (!pruefeUndMarkiereFeld(eingabeSollarbeitszeitMinute, 0, 59)) {
            erstesUngueltig = eingabeSollarbeitszeitMinute;
            ersteFehlermeldung = getString(R.string.fehler_minuten);
        }
        if (!pruefeUndMarkiereFeld(eingabeSollarbeitszeitStunde, 0, 23)) {
            erstesUngueltig = eingabeSollarbeitszeitStunde;
            ersteFehlermeldung = getString(R.string.fehler_stunden);
        }
        if (!(isAutoPauseAktiv() && eingabePause.getText().toString().trim().isEmpty())) {
            if (!pruefeUndMarkiereFeld(eingabePause, 0, 999)) {
                erstesUngueltig = eingabePause;
                ersteFehlermeldung = getString(R.string.fehler_pause);
            }
        }
        if (!pruefeUndMarkiereFeld(eingabeDienstendeMinute, 0, 59)) {
            erstesUngueltig = eingabeDienstendeMinute;
            ersteFehlermeldung = getString(R.string.fehler_minuten);
        }
        if (!pruefeUndMarkiereFeld(eingabeDienstendeStunde, 0, 23)) {
            erstesUngueltig = eingabeDienstendeStunde;
            ersteFehlermeldung = getString(R.string.fehler_stunden);
        }
        if (!pruefeUndMarkiereFeld(eingabeDienstbeginnMinute, 0, 59)) {
            erstesUngueltig = eingabeDienstbeginnMinute;
            ersteFehlermeldung = getString(R.string.fehler_minuten);
        }
        if (!pruefeUndMarkiereFeld(eingabeDienstbeginnStunde, 0, 23)) {
            erstesUngueltig = eingabeDienstbeginnStunde;
            ersteFehlermeldung = getString(R.string.fehler_stunden);
        }
        if (erstesUngueltig == null) {
            if (blockIndex > 1) return pruefeDbNachVorgaengerDe();
            return true;
        }

        final EditText feldZuFokussieren = erstesUngueltig;
        final String meldung = ersteFehlermeldung;
        feldZuFokussieren.post(() -> {
            validierungAktiv = true;
            feldZuFokussieren.requestFocus();
            validierungAktiv = false;
            feldZuFokussieren.setTextColor(COLOR_RED);
            erzielteZeit.setTextColor(COLOR_RED);
            erzielteZeit.setText(meldung);
            ((InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(feldZuFokussieren.getWindowToken(), 0);
        });
        return false;
    }

    private boolean pruefeDbNachVorgaengerDe() {
        if (listener == null) return true;
        int prevDeMinuten = listener.getPreviousBlockDiensteende(jobIndex, blockIndex);
        if (prevDeMinuten < 0) return true;
        String dbS = eingabeDienstbeginnStunde.getText().toString().trim();
        String dbM = eingabeDienstbeginnMinute.getText().toString().trim();
        if (dbS.isEmpty() || dbM.isEmpty()) return true;
        try {
            int dbMinuten = Integer.parseInt(dbS) * 60 + Integer.parseInt(dbM);
            if (dbMinuten < prevDeMinuten) {
                eingabeDienstbeginnStunde.setTextColor(COLOR_RED);
                eingabeDienstbeginnMinute.setTextColor(COLOR_RED);
                erzielteZeit.setTextColor(COLOR_RED);
                erzielteZeit.setText(getString(R.string.fehler_db_vor_vorgaenger_de));
                ((InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE))
                        .hideSoftInputFromWindow(eingabeDienstbeginnStunde.getWindowToken(), 0);
                return false;
            }
        } catch (NumberFormatException e) {
            return true;
        }
        return true;
    }

    // === Berechnungen ===

    private void berechneFruehestesEnde() {
        if (eingabeDienstbeginnStunde.getText().toString().trim().isEmpty()
                || eingabeDienstbeginnMinute.getText().toString().trim().isEmpty()
                || eingabeSollarbeitszeitStunde.getText().toString().trim().isEmpty()
                || eingabeSollarbeitszeitMinute.getText().toString().trim().isEmpty()) {
            return;
        }
        int dbMinuten = parseZeit(eingabeDienstbeginnStunde, eingabeDienstbeginnMinute);
        int sollMinuten = parseZeit(eingabeSollarbeitszeitStunde, eingabeSollarbeitszeitMinute);
        int previousArbeitszeit = listener != null
                ? listener.getPreviousBlocksArbeitszeit(jobIndex, blockIndex) : 0;
        int verbleibendeSoll = Math.max(0, sollMinuten - previousArbeitszeit);

        int pauseManual = parseMinuten(eingabePause, 999);
        boolean isLast = listener != null && listener.isLastBlock(jobIndex, blockIndex);
        int pauseMinuten;
        if (isAutoPauseAktiv() && isLast) {
            int previousNettoWork = sollMinuten - verbleibendeSoll;
            int embeddedAutoPause = Math.max(0, previousArbeitszeit - previousNettoWork);
            int additionalAutoPause = Math.max(0, gesetzlichePause(sollMinuten) - getInterBlockPausen() - embeddedAutoPause);
            pauseMinuten = pauseManual + additionalAutoPause;
        } else {
            pauseMinuten = pauseManual;
        }

        int endeMinuten = dbMinuten + verbleibendeSoll + pauseMinuten;
        int stunden = endeMinuten / 60;
        int minuten = endeMinuten % 60;
        if (stunden > 23) stunden -= 24;

        eingabeDienstendeStunde.setText(String.valueOf(stunden));
        eingabeDienstendeMinute.setText(String.format("%02d", minuten));
        erzielteZeit.setTextColor(COLOR_GREEN);
        if (!validierungAktiv) erzielteZeit.setText("");
        if (listener != null) listener.onVorgaengerNeuberechnung(jobIndex, blockIndex);
    }

    private boolean berechneGleitzeit() {
        if (!allesFelderAusgefuellt()) {
            erzielteZeit.setTextColor(COLOR_ORANGE);
            erzielteZeit.setText(getString(R.string.fehler_fehlende_zeiten));
            if (listener != null) listener.onBlockFehler(jobIndex, blockIndex);
            return false;
        }

        rgdb.clearCheck();
        rgde.clearCheck();

        if (!validiereAlleFelder()) {
            if (listener != null) listener.onBlockFehler(jobIndex, blockIndex);
            return false;
        }

        int dbMinuten = parseZeit(eingabeDienstbeginnStunde, eingabeDienstbeginnMinute);
        int deMinuten = parseZeit(eingabeDienstendeStunde, eingabeDienstendeMinute);
        int sollMinuten = parseZeit(eingabeSollarbeitszeitStunde, eingabeSollarbeitszeitMinute);

        int anwesenheit = deMinuten - dbMinuten;
        if (anwesenheit < 0) anwesenheit += 1440;

        int pauseManual = parseMinuten(eingabePause, 999);
        boolean isLast = listener != null && listener.isLastBlock(jobIndex, blockIndex);
        int previousArbeitszeit = listener != null
                ? listener.getPreviousBlocksArbeitszeit(jobIndex, blockIndex) : 0;
        int pauseMinuten = (isAutoPauseAktiv() && isLast)
                ? pauseManual + netAutoPause(anwesenheit + previousArbeitszeit)
                : pauseManual;

        int pauseFuerPruefung = (isAutoPauseAktiv() && isLast) ? pauseManual : pauseMinuten;
        if (pauseFuerPruefung >= anwesenheit) {
            erzielteZeit.setTextColor(COLOR_RED);
            erzielteZeit.setText(getString(R.string.fehler_pause_zu_lang));
            if (listener != null) listener.onBlockFehler(jobIndex, blockIndex);
            return false;
        }

        int eigeneArbeitszeit = anwesenheit - pauseMinuten;
        int gesamtArbeitszeit = eigeneArbeitszeit + previousArbeitszeit;

        int saldo = gesamtArbeitszeit - sollMinuten;
        String vorzeichen = saldo >= 0 ? "+" : "-";

        boolean industrie = "industrie".equals(
                requireActivity().getPreferences(Context.MODE_PRIVATE)
                        .getString("zeitformat", "natuerlich"));

        String erzieltText;
        String saldoText;
        if (industrie) {
            String azStr = String.format(Locale.getDefault(), "%.2f", gesamtArbeitszeit / 60f);
            erzieltText = getString(R.string.erzielte_zeit_format_industrie, azStr);
            String saldoStr = String.format(Locale.getDefault(), "%.2f", Math.abs(saldo) / 60f);
            saldoText = getString(R.string.saldo_format_industrie, vorzeichen, saldoStr);
        } else {
            int azStunden = gesamtArbeitszeit / 60;
            int azMinuten = gesamtArbeitszeit % 60;
            erzieltText = getString(R.string.erzielte_zeit_format, azStunden, azMinuten);
            int saldoStunden = Math.abs(saldo / 60);
            int saldoMinuten = Math.abs(saldo % 60);
            saldoText = getString(R.string.saldo_format, vorzeichen, saldoStunden, saldoMinuten);
        }

        erzielteZeit.setText(erzieltText);
        erzielteZeit.setTextColor(COLOR_GREEN);
        erzielteZeit.append(saldoText);

        if (saldo < 0) {
            Spannable spannableText = (Spannable) erzielteZeit.getText();
            int textlaenge1 = erzieltText.length();
            spannableText.setSpan(new ForegroundColorSpan(COLOR_GREEN), 0, textlaenge1, 0);
            spannableText.setSpan(new ForegroundColorSpan(COLOR_RED),
                    textlaenge1, textlaenge1 + saldoText.length(), 0);
        }

        getPrefsForBlock().edit()
                .putInt("letzteErzielteMinuten", gesamtArbeitszeit)
                .putInt("letzterSaldoMinuten", saldo)
                .apply();
        speichereDaten("letzteErzielteZeit", erzieltText);
        speichereDaten("letzterSaldo", saldoText);
        hinweisGezeigt = false;
        pendingChanges = false;
        aktualisierePauseHint();
        if (listener != null && isLast) listener.onDienstendeBerechnet(jobIndex, deMinuten);
        if (listener != null) listener.onBlockBerechnet(jobIndex, blockIndex);
        return true;
    }

    // === Hilfsmethoden ===

    private void fokusEntziehen() {
        rootLayout.requestFocus();
        InputMethodManager imm = (InputMethodManager)
                requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(rootLayout.getWindowToken(), 0);
    }

    private void formatMinutenFeld(EditText feld) {
        if (feld.length() == 1) {
            feld.setText("0" + feld.getText());
        }
    }

    private void setzeAktuelleZeit(EditText stundenFeld, EditText minutenFeld) {
        String[] zeit = aktuelleZeit().split(":");
        stundenFeld.setText(zeit[0]);
        minutenFeld.setText(zeit[1]);
    }

    private void ladeLetztenDienstbeginn() {
        try {
            String letzterDienstbeginn = leseDaten("letzterDienstbeginn");
            if (letzterDienstbeginn.length() == 3) {
                eingabeDienstbeginnStunde.setText(letzterDienstbeginn.substring(0, 1));
                eingabeDienstbeginnMinute.setText(letzterDienstbeginn.substring(1, 3));
            } else if (letzterDienstbeginn.length() == 4) {
                eingabeDienstbeginnStunde.setText(letzterDienstbeginn.substring(0, 2));
                eingabeDienstbeginnMinute.setText(letzterDienstbeginn.substring(2, 4));
            }
        } catch (Exception e) {
            erzielteZeit.setTextColor(COLOR_ORANGE);
            erzielteZeit.setText(getString(R.string.fehler_fehlende_zeiten));
        }
    }

    private String aktuelleZeit() {
        Calendar kalender = Calendar.getInstance();
        SimpleDateFormat format = new SimpleDateFormat("HH:mm");
        return format.format(kalender.getTime());
    }

    private int parseZeit(EditText stundenFeld, EditText minutenFeld) {
        return parseStunden(stundenFeld) * 60 + parseMinuten(minutenFeld, 59);
    }

    private int parseStunden(EditText feld) {
        String text = feld.getText().toString().trim();
        if (text.isEmpty()) { feld.setText("0"); return 0; }
        try {
            int wert = Integer.parseInt(text);
            if (wert < 0 || wert > 23) return 0;
            return wert;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int parseMinuten(EditText feld, int maxWert) {
        String text = feld.getText().toString().trim();
        if (text.isEmpty()) { feld.setText("0"); return 0; }
        if (text.length() == 1 && maxWert == 59) feld.setText("0" + text);
        try {
            int wert = Integer.parseInt(feld.getText().toString());
            if (wert < 0 || wert > maxWert) return 0;
            return wert;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private SharedPreferences getPrefsForBlock() {
        return requireActivity().getSharedPreferences(
                "job_" + jobIndex + "_block_" + blockIndex + "_prefs", Context.MODE_PRIVATE);
    }

    private void speichereDaten(String schluessel, String wert) {
        SharedPreferences.Editor editor = getPrefsForBlock().edit();
        editor.putString(schluessel, wert);
        editor.apply();
    }

    private String leseDaten(String schluessel) {
        String wert = getPrefsForBlock().getString(schluessel, "0");
        try {
            return String.valueOf(Integer.parseInt(wert));
        } catch (NumberFormatException e) {
            return "0";
        }
    }

    private boolean istFeldGueltig(EditText feld, int min, int max) {
        String text = feld.getText().toString().trim();
        if (text.isEmpty()) return true;
        try {
            int wert = Integer.parseInt(text);
            return wert >= min && wert <= max;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
