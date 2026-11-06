package com.fa.baiboly.data.litorjia;

/**
 * Programme de culte (fandaharana fivoriana) : un arbre de
 * {@link ProgramNode} rattaché à une date et à un gabarit.
 *
 * <p>Le module « Litorjia » est indépendant de la base chants/Bible :
 * il ne stocke que des références, résolues à l'affichage.</p>
 */
public class Program {

    public String id;
    public String title;
    /** ISO 8601 (yyyy-MM-dd), nullable. */
    public String serviceDate;
    /** Identifiant du gabarit utilisé à la génération (level-2, litorjia-1…). */
    public String templateId;
    public String notes;
    public long createdAt;
    public long updatedAt;

    /** Noeud racine de l'arbre (rarement affiché directement). */
    public ProgramNode root;

    /** Parcours en profondeur (racine comprise puis enfants dans l'ordre). */
    public void flatten(java.util.List<ProgramNode> out) {
        out.clear();
        if (root != null) collect(root, out);
    }

    private void collect(ProgramNode node, java.util.List<ProgramNode> out) {
        out.add(node);
        for (int i = 0; i < node.children.size(); i++) {
            collect(node.children.get(i), out);
        }
    }

    /** Nombre d'éléments à compléter visibles (chants sans numéro, lectures vides). */
    public int countIncomplete(boolean includeHidden) {
        if (root == null) return 0;
        return countIncomplete(root, includeHidden);
    }

    private int countIncomplete(ProgramNode node, boolean includeHidden) {
        int n = (!node.hidden || includeHidden) && node.isIncomplete() ? 1 : 0;
        for (int i = 0; i < node.children.size(); i++) {
            n += countIncomplete(node.children.get(i), includeHidden);
        }
        return n;
    }
}
