package com.fa.baiboly;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.fa.baiboly.data.ColorManager;

public class SplashActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "app_settings";

    private static final String[][] VERSES = {
            {"Fa velona sy mahery ny tenin'Andriamanitra ka maranitra noho ny sabatra roa lela", "Hebreo 4:12"},
            {"Izay soratra rehetra nomen'ny tsindrimandrin'Andriamanitra dia mahasoa koa ho fampianarana, ho fandresen-dahatra, ho fanitsiana izay diso, ho fitaizana amin'ny fahamarinana,", "2 Timoty 3:16"},
            {"Aza matahotra ianao, fa momba anao Aho; Ary aza miherikerika foana, fa Izaho no Andriamanitrao; Mampahery anao Aho sady mamonjy anao; Eny, mitantana anao amin'ny tanana ankavanan'ny fahamarinako Aho.", "Isaia 41:10"},
            {"Ampianaro ny lalanao aho, Jehovah ô; ary tariho amin'ny lalana marina aho noho ny fahavaloko..", "Salamo 27:11"},
            {"Hisaotra an'i Jehovah lalandava aho; ho eo am-bavako mandrakariva ny fiderana Azy.", "Salamo 34:1"},
            {"Mitady an'i Jehovah aho, dia mamaly ahy Izy ka manafaka ahy amin'ny tahotro rehetra.", "Salamo 34:4"},
            {"Ity olo-mahantra ity niantso, dia nihaino Jehovah ka namonjy azy tamin'ny fahoriany rehetra.", "Salamo 34:6"}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyGlobalTheme();
        WindowManager.LayoutParams params =
                getWindow().getAttributes();

        params.preferredRefreshRate = 120.0f;

        getWindow().setAttributes(params);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Pick random verse
        ImageView icon = findViewById(R.id.splashIcon);
        TextView title = findViewById(R.id.splashTitle);
        TextView subtitle = findViewById(R.id.splashSubtitle);
        View divider = findViewById(R.id.splashDivider);
        TextView verse = findViewById(R.id.splashVerse);
        TextView reference = findViewById(R.id.splashReference);

        int randomIndex = (int) (Math.random() * VERSES.length);
        verse.setText("\u201C" + VERSES[randomIndex][0] + "\u201D");
        reference.setText(VERSES[randomIndex][1]);

        // Play animations directly
        playAnimations(icon, title, subtitle, divider, verse, reference);
        goToMain();
    }

    private void goToMain() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isFinishing()) return;
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, 3000);
    }

    private void applyGlobalTheme() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int savedMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedMode);

        // Couleurs de base choisies par l'utilisateur
        ColorManager.applyTheme(this);
    }

    private void playAnimations(ImageView icon, TextView title, TextView subtitle,
                                 View divider, TextView verse, TextView reference) {
        long d = 600;
        DecelerateInterpolator ease = new DecelerateInterpolator(2f);

        AnimatorSet iconAnim = new AnimatorSet();
        iconAnim.playTogether(
                ObjectAnimator.ofFloat(icon, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(icon, "scaleX", 0.5f, 1f),
                ObjectAnimator.ofFloat(icon, "scaleY", 0.5f, 1f)
        );
        iconAnim.setDuration(d); iconAnim.setInterpolator(ease); iconAnim.setStartDelay(100);

        AnimatorSet titleAnim = new AnimatorSet();
        titleAnim.playTogether(
                ObjectAnimator.ofFloat(title, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(title, "translationY", 20f, 0f)
        );
        titleAnim.setDuration(d); titleAnim.setInterpolator(ease); titleAnim.setStartDelay(300);

        ObjectAnimator subAlpha = ObjectAnimator.ofFloat(subtitle, "alpha", 0f, 1f);
        subAlpha.setDuration(d); subAlpha.setInterpolator(ease); subAlpha.setStartDelay(400);

        AnimatorSet divAnim = new AnimatorSet();
        divAnim.playTogether(
                ObjectAnimator.ofFloat(divider, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(divider, "scaleX", 0f, 1f)
        );
        divAnim.setDuration(500); divAnim.setInterpolator(ease); divAnim.setStartDelay(500);

        AnimatorSet verseAnim = new AnimatorSet();
        verseAnim.playTogether(
                ObjectAnimator.ofFloat(verse, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(verse, "translationY", 15f, 0f)
        );
        verseAnim.setDuration(800); verseAnim.setInterpolator(ease); verseAnim.setStartDelay(600);

        ObjectAnimator refAlpha = ObjectAnimator.ofFloat(reference, "alpha", 0f, 1f);
        refAlpha.setDuration(600); refAlpha.setInterpolator(ease); refAlpha.setStartDelay(900);

        AnimatorSet all = new AnimatorSet();
        all.playTogether(iconAnim, titleAnim, subAlpha, divAnim, verseAnim, refAlpha);
        all.start();
    }
}
