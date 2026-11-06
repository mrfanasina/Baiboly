package com.fa.baiboly.ui.litorjia;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.fa.baiboly.R;
import com.fa.baiboly.data.litorjia.LitorjiaJsonService;
import com.fa.baiboly.data.litorjia.LitorjiaRepository;
import com.fa.baiboly.data.litorjia.Program;
import com.fa.baiboly.data.litorjia.ProgramNode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Import JSON (§4.5) : coller le JSON produit par une IA externe ou
 * ouvrir un fichier. Aide copiable (« Copier le prompt », §8), rapport
 * de validation avant confirmation, création d'un nouveau programme ou
 * fusion dans le programme courant (retour via intent des deux modes).
 */
public class LitorjiaImportActivity extends AppCompatActivity {

    private static final int REQ_FILE = 901;
    /** Mode fusion : renvoie le JSON validé à l'appelant. */
    public static final int RESULT_MERGED = RESULT_FIRST_USER + 5;

    private LitorjiaRepository repo;
    private EditText inputJson;
    private TextView textReport;
    private boolean mergeMode = false;
    private LitorjiaJsonService.Report lastReport;

    /** Mode « créer un nouveau programme » depuis la liste. */
    public static void openCreate(Context ctx) {
        Intent i = new Intent(ctx, LitorjiaImportActivity.class);
        i.putExtra("merge", false);
        ctx.startActivity(i);
    }

    /** Mode « fusionner dans le programme ouvert » (retour via result). */
    public static Intent intentForMerge(Context ctx) {
        Intent i = new Intent(ctx, LitorjiaImportActivity.class);
        i.putExtra("merge", true);
        return i;
    }

    /** Aide seule (depuis le menu de la liste). */
    public static void openHelp(Context ctx) {
        openCreate(ctx); // même écran : l'aide y est en tête
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_litorjia_import);

        repo = new LitorjiaRepository(this);
        mergeMode = getIntent().getBooleanExtra("merge", false);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
            getSupportActionBar().setTitle(R.string.lit_import_title);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        inputJson = findViewById(R.id.inputJson);
        textReport = findViewById(R.id.textReport);

        findViewById(R.id.btnCopyPrompt).setOnClickListener(v -> copyToClipboard(
                getString(R.string.lit_prompt_copied), promptText()));
        findViewById(R.id.btnCopySchema).setOnClickListener(v -> copyToClipboard(
                getString(R.string.lit_schema_copied), schemaText()));

        findViewById(R.id.btnPaste).setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || cm.getPrimaryClip() == null
                    || cm.getPrimaryClip().getItemAt(0) == null) return;
            CharSequence data = cm.getPrimaryClip().getItemAt(0).getText();
            if (data != null) {
                inputJson.setText(data.toString());
                Toast.makeText(this, getString(R.string.lit_json_copied_to_field),
                        Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btnOpenFile).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "application/json", "text/plain"});
            startActivityForResult(i, REQ_FILE);
        });

        findViewById(R.id.btnImport).setOnClickListener(v -> validateAndImport());
    }

    // =========================================================
    // Validation + import
    // =========================================================

    private void validateAndImport() {
        String raw = inputJson.getText().toString();
        if (raw.trim().isEmpty()) {
            Toast.makeText(this, getString(R.string.lit_import_no_json),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        LitorjiaJsonService.Report r = LitorjiaJsonService.parse(raw, repo);
        lastReport = r;
        showReport(r);
        if (!r.ok) return;

        // Le rapport est affiché AVANT la confirmation (§4.5) : rien n'est
        // écrit en base tant que l'utilisateur n'a pas validé. Depuis un
        // programme ouvert, les deux modes restent proposés.
        int count = countNodes(r.root);
        final String[] choices = mergeMode
                ? new String[]{getString(R.string.lit_import_confirm_merge),
                               getString(R.string.lit_import_confirm_create)}
                : new String[]{getString(R.string.lit_import_confirm_create)};
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.lit_import_report_title)
                .setMessage(getString(R.string.lit_report_check, count, r.warnings.size())
                        + "\n\n" + getString(R.string.lit_import_confirm))
                .setItems(choices, (d, w) ->
                        commitImport(r, mergeMode && w == 0))
                .setNegativeButton(R.string.lit_cancel, null)
                .show();
    }

    /** Écriture effective, après confirmation du rapport. */
    private void commitImport(LitorjiaJsonService.Report r, boolean merge) {
        if (merge) {
            // Fusion : renvoie le JSON au programme ouvert ; lui repasse par
            // le parseur pour éviter les doublons d'ids.
            Intent data = new Intent();
            data.putExtra("json", LitorjiaJsonService.exportOf(r.root, r));
            setResult(RESULT_MERGED, data);
            finish();
            Toast.makeText(this, getString(R.string.lit_import_merged),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Création d'un nouveau programme. template est facultatif à
        // l'import : on retient le premier gabarit du catalogue.
        Program p = new Program();
        p.id = UUID.randomUUID().toString();
        p.title = r.fallbackTitle;
        p.serviceDate = r.date;
        p.templateId = r.template;
        if (p.templateId == null) {
            java.util.List<com.fa.baiboly.data.litorjia.TemplateCatalog.TemplateInfo> tpl =
                    repo.templates();
            p.templateId = tpl.isEmpty() ? null : tpl.get(0).id;
        }
        p.notes = r.notes;
        p.createdAt = System.currentTimeMillis();
        p.updatedAt = p.createdAt;
        p.root = r.root;
        r.root.title = p.title;
        if (p.templateId == null) {
            Toast.makeText(this, getString(R.string.lit_no_catalog), Toast.LENGTH_LONG).show();
            return;
        }
        repo.insertProgram(p);
        repo.saveTree(p, p.root);
        Toast.makeText(this, getString(R.string.lit_import_created),
                Toast.LENGTH_SHORT).show();
        LitorjiaDetailActivity.openProgram(this, p.id);
        finish();
    }

    private void showReport(LitorjiaJsonService.Report r) {
        StringBuilder sb = new StringBuilder();
        if (!r.ok) {
            sb.append("⛔ ").append(getString(R.string.lit_report_bad)).append("\n");
            sb.append(r.error == null ? "" : r.error);
        } else {
            sb.append("✅ ").append(getString(R.string.lit_report_ok)).append("\n");
            int n = countNodes(r.root);
            int w = r.warnings.size();
            sb.append(getString(R.string.lit_report_check, n, w));
            for (LitorjiaJsonService.Warning warn : r.warnings) {
                sb.append("\n• ").append(warn.path == null || warn.path.isEmpty() ? "" : warn.path)
                        .append(" : ").append(warn.reason);
            }
        }
        textReport.setText(sb.toString());
        textReport.setVisibility(View.VISIBLE);
    }

    private int countNodes(ProgramNode root) {
        int n = 0;
        for (ProgramNode c : root.children) {
            n += 1 + countNodes(c);
        }
        return n;
    }

    // =========================================================
    // Fichier .json
    // =========================================================

    @Override
    @Deprecated
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && resultCode == RESULT_OK && data != null
                && data.getData() != null) {
            readUri(data.getData());
        }
    }

    private void readUri(Uri uri) {
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(
                    getContentResolver().openInputStream(uri), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            inputJson.setText(sb.toString());
        } catch (Exception e) {
            Toast.makeText(this, "Tsy nety vakiana ny fichier", Toast.LENGTH_SHORT).show();
        }
    }

    // =========================================================
    // Prompt + schéma (§8)
    // =========================================================

    private String promptText() {
        try {
            java.io.InputStream in = getResources()
                    .openRawResource(R.raw.lit_prompt_litorjia);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int l;
            while ((l = in.read(buf)) > 0) out.write(buf, 0, l);
            in.close();
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "{\"schema\":\"litorjia/1\"}";
        }
    }

    private String schemaText() {
        return "{\n"
                + "  \"schema\": \"litorjia/1\",\n"
                + "  \"title\": \"string\",\n"
                + "  \"date\": \"YYYY-MM-DD\",\n"
                + "  \"template\": \"level-2\",\n"
                + "  \"notes\": \"string\",\n"
                + "  \"nodes\": [\n"
                + "    {\n"
                + "      \"type\": \"section | title | hymn | bible | prayer | creed | choir | text | offering | custom\",\n"
                + "      \"title\": \"string\",\n"
                + "      \"hymn\": { \"book\": \"FFPM | FIF | FF\", \"number\": 0, \"stanzas\": [1, 2], \"label\": \"string\" },\n"
                + "      \"bible\": [ { \"book\": \"string\", \"chapter\": 0, \"ranges\": [ { \"from\": \"1\", \"to\": \"6\" } ] } ],\n"
                + "      \"prayerKind\": \"fiderana | fisaorana | fangatahana | keloka | hira | other\",\n"
                + "      \"creed\": { \"version\": 1 },\n"
                + "      \"body\": \"string\",\n"
                + "      \"children\": [ ]\n"
                + "    }\n"
                + "  ]\n"
                + "}";
    }

    private void copyToClipboard(String toast, String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("litorjia", text));
        Toast.makeText(this, toast, Toast.LENGTH_SHORT).show();
    }
}
