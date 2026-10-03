package com.fa.baiboly.ui.mofonaina;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.text.LineBreaker;
import android.os.Build;
import android.os.Bundle;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.*;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import com.fa.baiboly.R;
import com.fa.baiboly.data.ColorManager;
import com.fa.baiboly.SettingsFragment;
import com.fa.baiboly.data.MofonainaRepository;
import com.fa.baiboly.data.parser.BibleRefParser;
import com.fa.baiboly.data.perikopa.PerikopaService;
import com.fa.baiboly.databinding.ActivityMofonainaBinding;
import com.fa.baiboly.models.MofonainaData;
import com.fa.baiboly.models.ReflectionSection;
import com.fa.baiboly.models.Song;
import com.fa.baiboly.ui.fihirana.SongDetailActivity;
import com.fa.baiboly.ui.verses.VerseBottomSheetFragment;
import com.fa.baiboly.ui.verses.VersesActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MofonainaActivity extends AppCompatActivity {

    private ActivityMofonainaBinding binding;
    private SharedPreferences preferences;

    private static final String PREFS_NAME = "app_settings";

    public static final String KEY_THEME = "theme_mode";
    public static final String KEY_TEXT_SIZE = "text_size";
    public static final String KEY_JUSTIFICATION = "justification_mode";

    @SuppressLint("WrongConstant")
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        applyGlobalTheme();

        super.onCreate(savedInstanceState);

        binding = ActivityMofonainaBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());

        preferences =
                getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                );

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        WindowManager.LayoutParams params =
                getWindow().getAttributes();

        params.preferredRefreshRate = 120.0f;

        getWindow().setAttributes(params);

        PerikopaService perikopaService =
                new PerikopaService(this);

        String title =
                perikopaService.getTodayLohahevitra();

        setSupportActionBar(binding.toolbar);

        if (getSupportActionBar() != null) {

            getSupportActionBar()
                    .setDisplayShowTitleEnabled(false);

            getSupportActionBar()
                    .setDisplayHomeAsUpEnabled(true);
        }

        binding.toolbarTitle.setText(title);

        binding.toolbar.setNavigationOnClickListener(v -> {

            if (isSettingsOpen()) {

                closeSettings();

            } else {

                finish();
            }
        });

        fetchMofonainaData();
    }

    private void applyGlobalTheme() {

        SharedPreferences prefs =
                getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                );

        int savedMode =
                prefs.getInt(
                        KEY_THEME,
                        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                );

        AppCompatDelegate.setDefaultNightMode(savedMode);

        // Couleurs de base choisies par l'utilisateur
        ColorManager.applyTheme(this);
    }

    // =========================================================
    // JUSTIFICATION — CONFIGURATION "À LA SOURCE"
    // =========================================================

    /**
     * FIX JUSTIFICATION (Android 10+) :
     *
     * Le bug historique n'est pas que justificationMode soit mal réglé,
     * mais QUAND il est réglé. Si on attend la fin de updateUI() pour
     * l'appliquer via une passe récursive (comme avant), le TextView a
     * déjà un texte + un Layout construits, et sur beaucoup d'appareils
     * (notamment Android 10 et certains OEM), changer justificationMode
     * après coup est silencieusement ignoré même avec requestLayout()/
     * invalidate().
     *
     * La solution fiable : régler breakStrategy + hyphenation +
     * justificationMode DÈS LA CRÉATION de la TextView, AVANT setText().
     * Cette méthode doit donc être appelée juste après `new TextView(this)`,
     * jamais après.
     */
    private void configureJustificationEngine(TextView tv, boolean justified) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            tv.setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY);
            tv.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            tv.setJustificationMode(
                    justified
                            ? LineBreaker.JUSTIFICATION_MODE_INTER_WORD
                            : LineBreaker.JUSTIFICATION_MODE_NONE
            );
        }
    }

    /**
     * Applique la taille de texte et la justification à un TextView déjà
     * affiché (cas du toggle en direct dans les Settings). Contrairement à
     * configureJustificationEngine(), on force ici un re-setText() pour
     * contourner la mise en cache du Layout sur Android 10 / certains OEM :
     * requestLayout()/invalidate() seuls ne suffisent pas toujours à faire
     * reconstruire le StaticLayout avec le nouveau justificationMode.
     */
    private void refreshJustification(TextView tv, boolean justified) {

        if (tv.getId() == R.id.toolbarTitle) {
            return;
        }

        configureJustificationEngine(tv, justified);

        // Sécurité largeur : la justification a besoin de toute la largeur
        // disponible pour avoir de l'espace à répartir entre les mots.
        if (tv.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams lp =
                    (LinearLayout.LayoutParams) tv.getLayoutParams();
            if (lp.width != ViewGroup.LayoutParams.MATCH_PARENT) {
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                tv.setLayoutParams(lp);
            }
        }

        // Force la reconstruction complète du Layout : re-assigner le même
        // texte relance StaticLayout.Builder avec les nouveaux paramètres.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence current = tv.getText();
            tv.setText(current);
        }

        tv.requestLayout();
        tv.invalidate();
    }

    /**
     * Applique la taille de texte et la justification
     */
    private void applyTextSettings() {

        float size =
                preferences.getFloat(KEY_TEXT_SIZE, 18f);

        boolean isJustified =
                preferences.getBoolean(
                        KEY_JUSTIFICATION,
                        true
                );

        applyTextEngine(
                binding.mainScroll,
                size,
                isJustified
        );
    }

    /**
     * Applique récursivement taille + justification (utilisé pour le
     * changement de réglage EN DIRECT, ex. depuis SettingsFragment).
     *
     * Pour la création initiale des TextView, préférer
     * configureJustificationEngine() appelée avant setText() — voir
     * updateUI(). Cette méthode reste nécessaire pour re-synchroniser
     * l'ensemble de l'arbre quand l'utilisateur change un réglage
     * pendant que l'écran est déjà affiché.
     */
    private void applyTextEngine(View view,
                                 float size,
                                 boolean justified) {

        if (view instanceof TextView) {

            TextView tv = (TextView) view;

            if (tv.getId() != R.id.toolbarTitle) {
                tv.setTextSize(size);
            }

            // IMPORTANT : setTextIsSelectable() recrée l'Editor interne de la
            // TextView et peut, sur certaines versions d'Android, réinitialiser
            // breakStrategy/justificationMode s'il est appelé APRÈS eux.
            // On le règle donc en premier, puis refreshJustification()
            // reconfigure tout proprement derrière (avec re-setText()).
            tv.setTextIsSelectable(!justified);

            refreshJustification(tv, justified);
        }

        if (view instanceof ViewGroup) {

            ViewGroup group = (ViewGroup) view;

            for (int i = 0; i < group.getChildCount(); i++) {

                applyTextEngine(
                        group.getChildAt(i),
                        size,
                        justified
                );
            }
        }
    }

    // =========================================================
    // MENU
    // =========================================================

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.menu_main, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == R.id.action_settings) {

            openSettings();

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // =========================================================
    // SETTINGS
    // =========================================================

    private void openSettings() {

        binding.settingsContainer
                .setVisibility(View.VISIBLE);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(
                        R.id.settings_container,
                        new SettingsFragment()
                )
                .addToBackStack(null)
                .commit();
    }

    private void closeSettings() {

        getSupportFragmentManager().popBackStack();

        binding.settingsContainer
                .setVisibility(View.GONE);

        applyTextSettings();
    }

    private boolean isSettingsOpen() {

        return binding.settingsContainer.getVisibility()
                == View.VISIBLE;
    }

    @Override
    public void onBackPressed() {

        if (isSettingsOpen()) {

            closeSettings();

        } else {

            super.onBackPressed();
        }
    }

    // =========================================================
    // DATA LOADING
    // =========================================================

    private void fetchMofonainaData() {

        binding.loader.setVisibility(View.VISIBLE);

        binding.mainScroll.setVisibility(View.GONE);

        new Thread(() -> {

            try {

                MofonainaRepository repo =
                        new MofonainaRepository(this);

                MofonainaData data =
                        repo.get("https://www.fjkm.mg");

                runOnUiThread(() -> {

                    if (data != null && !isFinishing()) {

                        updateUI(data);

                    } else {

                        binding.loader
                                .setVisibility(View.GONE);

                        Toast.makeText(
                                this,
                                "Tsy nandeha ny fampitahana",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                });

            } catch (Exception e) {

                runOnUiThread(() ->
                        binding.loader.setVisibility(View.GONE)
                );
            }

        }).start();
    }

    // =========================================================
    // UI UPDATE
    // =========================================================

    private void updateUI(MofonainaData data) {

        binding.loader.setVisibility(View.GONE);

        // Fondu + léger slide-up à l'apparition du contenu (UX)
        binding.mainScroll.setVisibility(View.VISIBLE);
        binding.mainScroll.setAlpha(0f);
        binding.mainScroll.setTranslationY(24f);
        binding.mainScroll.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(280)
                .start();

        binding.textTitle.setText(data.getTitle());
        binding.textDate.setText(data.getDate());
        binding.reflectionContainer.removeAllViews();

        // Lu une seule fois ici : sert à configurer chaque TextView dès sa
        // création (voir configureJustificationEngine), AVANT setText().
        boolean isJustified =
                preferences.getBoolean(KEY_JUSTIFICATION, true);

        // =====================================================
        // MAIN TITLE
        // =====================================================

        if (data.getTitle() != null) {

            TextView mainTitle = new TextView(this);

            // Justification réglée AVANT setText() : voir configureJustificationEngine().
            configureJustificationEngine(mainTitle, isJustified);

            // Titre cliquable : les références du jour ("and12-13", "Jao 3:16"...)
            // y sont détectées et ouvertes au tap, comme dans les paragraphes.
            setBibleLinks(
                    mainTitle,
                    data.getTitle(),
                    data.getVerseOfDay() != null
                            ? data.getVerseOfDay().getBook().getShortName() : null,
                    data.getVerseOfDay() != null
                            ? data.getVerseOfDay().getStartChapter() : -1
            );

            mainTitle.setTypeface(Typeface.DEFAULT_BOLD);

            mainTitle.setTextSize(24);

            mainTitle.setTextColor(
                    ContextCompat.getColor(
                            this,
                            R.color.text_full
                    )
            );

            mainTitle.setPadding(0, 0, 0, 32);

            addJustifiableView(binding.reflectionContainer, mainTitle);
        }

        // =====================================================
        // SECTIONS
        // =====================================================

        if (data.getSections() != null) {

            for (ReflectionSection section
                    : data.getSections()) {

                // Skip title for intro section (null title)
                if (section.getTitle() != null) {
                    TextView sectionTitle =
                            new TextView(this);

                    configureJustificationEngine(sectionTitle, isJustified);

                    // Titres de sections cliquables aussi ("and.5", "Jao 3"...)
                    setBibleLinks(
                            sectionTitle,
                            "\n" + section.getTitle(),
                            data.getVerseOfDay() != null
                                    ? data.getVerseOfDay().getBook().getShortName() : null,
                            data.getVerseOfDay() != null
                                    ? data.getVerseOfDay().getStartChapter() : -1
                    );

                    sectionTitle.setTypeface(
                            Typeface.DEFAULT_BOLD
                    );

                    sectionTitle.setTextSize(20);

                    sectionTitle.setTextColor(
                            ContextCompat.getColor(
                                    this,
                                    R.color.green
                            )
                    );

                    sectionTitle.setPadding(
                            0,
                            24,
                            0,
                            16
                    );

                    addJustifiableView(binding.reflectionContainer, sectionTitle);
                }

                if (section.getParagraphs() != null) {

                    for (String paragraph
                            : section.getParagraphs()) {

                        TextView paragraphView = new TextView(this);

                        // CRITIQUE : configurer breakStrategy/justificationMode
                        // ICI, avant que setBibleLinks() ne pose le texte
                        // (Spannable) et le MovementMethod. C'est cet ordre
                        // qui corrige le bug de justification sur Android 10+.
                        configureJustificationEngine(paragraphView, isJustified);

                        setBibleLinks(
                                paragraphView,
                                paragraph,
                                data.getVerseOfDay().getBook().getShortName(),
                                data.getVerseOfDay().getStartChapter()
                        );

                        paragraphView.setLineSpacing(
                                1.3f,
                                1.3f
                        );

                        paragraphView.setPadding(
                                0,
                                0,
                                0,
                                24
                        );

                        addJustifiableView(binding.reflectionContainer, paragraphView);
                    }
                }
            }
        }

        // =====================================================
        // AUTHOR
        // =====================================================

        if (data.getAuthor() != null
                && !data.getAuthor().isEmpty()) {

            TextView authorView =
                    new TextView(this);

            configureJustificationEngine(authorView, isJustified);

            authorView.setText(data.getAuthor());

            authorView.setTypeface(
                    Typeface.create(
                            Typeface.DEFAULT,
                            Typeface.ITALIC
                    )
            );

            authorView.setPadding(
                    0,
                    32,
                    0,
                    0
            );

            addJustifiableView(binding.reflectionContainer, authorView);
        }

        // =====================================================
        // VERSE OF THE DAY -> navigation directe
        // =====================================================

        if (data.getVerseOfDay() != null) {

            String ref =
                    data.getVerseOfDay().toString();

            binding.textVerseOfDay.setText(ref);

            binding.cardVerseOfDay
                    .setOnClickListener(v -> openVerses(ref));
        }

        // =====================================================
        // BIBLE READING -> navigation directe
        // =====================================================

        if (data.getBibleReading1() != null) {

            String ref =
                    data.getBibleReading1().toString();

            binding.textReading.setText(ref);

            binding.cardReading
                    .setOnClickListener(v -> openVerses(ref));
        }

        // =====================================================
        // SONGS
        // =====================================================

        binding.songsContainer.removeAllViews();

        setupSong(data.getSong1());

        setupSong(data.getSong2());

        // =====================================================
        // APPLY USER SETTINGS
        // =====================================================
        // NOTE : les TextView ont déjà été créées avec la bonne
        // justificationMode (voir configureJustificationEngine ci-dessus).
        // Cet appel reste utile pour appliquer la taille de texte et pour
        // les cas où applyTextSettings() est rappelée après fermeture des
        // Settings (closeSettings()), où refreshJustification() gère alors
        // le re-setText() nécessaire sur des vues déjà affichées.
        applyTextSettings();
    }

    /**
     * Navigation directe (cartes "Fitarihana" et "Mofonaina anio").
     */
    private void openVerses(String reading) {

        Intent intent = new Intent(this, VersesActivity.class);
        intent.putExtra("reading", reading);
        startActivity(intent);
    }

    /**
     * Ajoute une TextView au container en forçant une largeur MATCH_PARENT,
     * condition nécessaire pour que la justification fonctionne réellement.
     */
    private void addJustifiableView(LinearLayout container, TextView view) {

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        view.setLayoutParams(lp);

        container.addView(view);
    }

    // =========================================================
    // LIENS BIBLIQUES -> modal moderne
    // =========================================================

    /**
     * Détecte deux familles de références dans un paragraphe :
     *
     * 1) Le marqueur "and" (placeholder pour "lecture du jour"), avec ou
     *    sans verset précis :
     *      "and"          -> livre + chapitre du jour entier
     *      "and.1" / "and:1" / "and 1"  -> livre + chapitre du jour, verset 1
     *      "and.1-3"      -> livre + chapitre du jour, versets 1 à 3
     *
     * 2) Les références classiques abrégées : "Jao 3.1", "1 Kor 13:4-7", etc.
     *
     * Les deux familles ouvrent le modal ; le bouton "Hijery feno" à
     * l'intérieur permet ensuite d'aller vers la vue complète.
     */
    private void setBibleLinks(TextView textView,
                               String text,
                               String dayBook,
                               int dayChapter) {

        SpannableString spannable = new SpannableString(text);

        List<int[]> handledRanges = new ArrayList<>();

        // ---------------------------------------------------
        // 1) Marqueur "and" / "andininy" (lecture du jour).
        //    Nécessite un verset du jour connu : sinon on saute (les
        //    références classiques restent détectées plus bas).
        // ---------------------------------------------------

        Pattern andPattern = Pattern.compile(
                "\\b(?:andininy|and)[ \\t]*[.:]?[ \\t]*(\\d+(?:[ \\t]?-[ \\t]?\\d+)?)"
        );

        if (dayBook != null && dayChapter > 0) {

            Matcher andMatcher = andPattern.matcher(text);

            while (andMatcher.find()) {

                int start = andMatcher.start();
                int end = andMatcher.end();

                String verseSuffix = andMatcher.group(1); // toujours non-null désormais

                String reference = dayBook + " " + dayChapter
                        + ":" + verseSuffix;

                addBibleClickableSpan(spannable, start, end, reference);
                handledRanges.add(new int[]{start, end});
            }
        }

        // ---------------------------------------------------
        // 2) Références classiques (détection centralisée) : "Jao 3.1",
        //    "Heb11,1", "1 Kor 13:4-7"... Validées contre la table des
        //    livres via BibleRefParser -> pas de faux positifs.
        // ---------------------------------------------------

        List<BibleRefParser.DetectedRef> detected =
                BibleRefParser.detectReferences(text);

        for (BibleRefParser.DetectedRef dr : detected) {

            int start = dr.start;
            int end = dr.end;

            if (overlapsHandledRange(handledRanges, start, end)) {
                continue;
            }

            addBibleClickableSpan(spannable, start, end, dr.reference);
        }

        textView.setText(spannable);

        // IMPORTANT : on n'appelle PAS setMovementMethod(LinkMovementMethod).
        // C'est exactement ce qui casse la justification sur Android 10+ dans
        // nos tests — comportement documenté côté Android/React Native où
        // rendre le texte "sélectionnable/cliquable" via un MovementMethod
        // désactive silencieusement JUSTIFICATION_MODE_INTER_WORD.
        //
        // À la place, on reproduit le pattern déjà éprouvé dans
        // VersesActivity.handleAnnotationTouch() : détection manuelle du
        // ClickableSpan via un OnTouchListener + Layout.getOffsetForHorizontal(),
        // sans jamais toucher au MovementMethod.
        textView.setOnTouchListener(this::handleParagraphSpanTouch);
        textView.setHighlightColor(Color.TRANSPARENT);

        // CRITIQUE : sans clickable/focusable, la TextView ne consomme pas
        // ACTION_DOWN (son onTouchEvent() par défaut renvoie false), donc
        // aucun ACTION_UP n'est jamais délivré à handleParagraphSpanTouch.
        // C'est exactement ce que fait VersesActivity.applyTextSettings()
        // pour bibleTextFull — même pattern ici.
        textView.setClickable(true);
        textView.setFocusable(true);
    }

    /**
     * Détection tactile manuelle des ClickableSpan dans un paragraphe,
     * sans MovementMethod (voir setBibleLinks ci-dessus pour le pourquoi).
     * Reprend le même principe que VersesActivity.handleAnnotationTouch().
     */
    private boolean handleParagraphSpanTouch(View view, MotionEvent motionEvent) {

        if (motionEvent == null || motionEvent.getAction() != MotionEvent.ACTION_UP) {
            return false;
        }

        TextView textView = (TextView) view;

        CharSequence text = textView.getText();
        if (!(text instanceof Spanned)) {
            return false;
        }
        Spanned spanned = (Spanned) text;

        Layout layout = textView.getLayout();
        if (layout == null) {
            return false;
        }

        int x = (int) motionEvent.getX();
        int y = (int) motionEvent.getY();

        x -= textView.getTotalPaddingLeft();
        y -= textView.getTotalPaddingTop();

        x += textView.getScrollX();
        y += textView.getScrollY();

        int line = layout.getLineForVertical(y);
        int offset = layout.getOffsetForHorizontal(line, x);

        // Évite de détecter un clic dans le vide à droite de la ligne
        if (x > layout.getLineWidth(line)) {
            return false;
        }

        ClickableSpan[] spans = spanned.getSpans(
                Math.max(0, offset - 1),
                Math.min(spanned.length(), offset + 1),
                ClickableSpan.class
        );

        for (ClickableSpan span : spans) {

            int start = spanned.getSpanStart(span);
            int end = spanned.getSpanEnd(span);

            if (offset >= (start - 1) && offset <= (end + 1)) {
                span.onClick(textView);
                return true;
            }
        }

        return false;
    }

    private boolean overlapsHandledRange(List<int[]> ranges, int start, int end) {

        for (int[] r : ranges) {
            if (start < r[1] && end > r[0]) {
                return true;
            }
        }

        return false;
    }

    private void addBibleClickableSpan(SpannableString spannable,
                                       int start,
                                       int end,
                                       String reference) {

        ClickableSpan clickableSpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {

                VerseBottomSheetFragment.display(
                        getSupportFragmentManager(),
                        reference
                );
            }
        };

        spannable.setSpan(
                clickableSpan,
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
    }

    private void setupSong(Song song) {

        if (song == null) return;

        TextView tv = new TextView(this);

        tv.setText(
                song.getCategory().toUpperCase()
                        + " "
                        + song.getNumber()
        );

        tv.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.music_svgrepo_com,
                0,
                0,
                0
        );

        tv.setCompoundDrawablePadding(16);

        tv.setTextColor(
                ContextCompat.getColor(
                        this,
                        R.color.purple
                )
        );

        if (tv.getCompoundDrawables()[0] != null) {
            tv.getCompoundDrawables()[0].setTint(
                    ContextCompat.getColor(
                            this,
                            R.color.purple
                    )
            );
        }

        tv.setTypeface(Typeface.DEFAULT_BOLD);

        tv.setPadding(0, 20, 0, 20);

        // Petit effet ripple au clic (UX)
        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground,
                outValue,
                true
        );
        tv.setBackgroundResource(outValue.resourceId);
        tv.setClickable(true);
        tv.setFocusable(true);

        tv.setOnClickListener(v ->
                SongDetailActivity.open(
                        this,
                        song.getId(),
                        song.getTitle()
                )
        );

        binding.songsContainer.addView(tv);
    }
}