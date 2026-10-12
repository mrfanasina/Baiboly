package com.fa.baiboly.data.playback;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.Nullable;

/**
 * Petites aides SAF (Storage Access Framework) : nom lisible du dossier
 * choisi par l'utilisateur, permissions persistantes.
 */
public final class SafUtils {

    private SafUtils() {
    }

    /**
     * Prend les permissions persistantes sur le dossier choisi (à appeler
     * après ACTION_OPEN_DOCUMENT_TREE).
     */
    public static void takePersistablePermission(ContentResolver resolver,
                                                  Uri treeUri) {
        try {
            resolver.takePersistableUriPermission(treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception ignored) {
            // La permission est parfois déjà prise : on continue
        }
    }

    /**
     * Nom lisible du dossier choisi ("Downloads", "Feo FFPM"...), en
     * interrogeant le provider ; fallback sur le dernier segment du tree Uri.
     */
    @Nullable
    public static String folderDisplayName(ContentResolver resolver, Uri treeUri) {
        try {
            Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri));
            try (android.database.Cursor c = resolver.query(docUri, new String[]{
                    android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst() && c.getString(0) != null) {
                    return c.getString(0);
                }
            }
        } catch (Exception ignored) {
            // Fallback ci-dessous
        }
        String last = treeUri.getLastPathSegment();
        if (last != null && last.contains(":")) {
            String sub = last.substring(last.lastIndexOf(':') + 1);
            return sub.isEmpty() ? last : sub;
        }
        return last;
    }
}
