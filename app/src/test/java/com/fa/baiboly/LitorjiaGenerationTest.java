package com.fa.baiboly;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fa.baiboly.data.litorjia.ProgramGenerator;
import com.fa.baiboly.data.litorjia.ProgramNode;
import com.fa.baiboly.data.litorjia.TemplateCatalog;

import org.junit.Test;

import java.util.List;

/**
 * Vérifie la génération par gabarit (§11) : pour CHAQUE entrée du
 * catalogue, l'arbre instancié contient au moins un noeud. C'est la
 * non-régression du bug « 0 elements » après création d'une Litorjia III.
 */
public class LitorjiaGenerationTest {

    @Test
    public void chaqueGabaritGenereAuMoinsUnNoeud() {
        List<TemplateCatalog.TemplateInfo> catalog = TemplateCatalog.catalog();
        assertFalse("catalogue vide", catalog.isEmpty());

        for (TemplateCatalog.TemplateInfo t : catalog) {
            String tree = TemplateCatalog.tree(t.id);
            assertNotNull("arbre absent pour " + t.id, tree);

            ProgramNode root = ProgramGenerator.instantiate(t.id, tree, "Fivoriana");
            assertNotNull("instanciation nulle pour " + t.id, root);
            assertTrue(t.id + " ne génère aucun noeud", count(root) > 0);
        }
    }

    @Test
    public void tousLesNoeudsOntUnParentCoherent() {
        for (TemplateCatalog.TemplateInfo t : TemplateCatalog.catalog()) {
            ProgramNode root = ProgramGenerator.instantiate(
                    t.id, TemplateCatalog.tree(t.id), "Fivoriana");
            assertNotNull(root);
            assertNull("la racine ne doit pas avoir de parent", root.parentId);
            assertParents(root, root.id);
        }
    }

    @Test
    public void chaqueNiveauContientLesSectionsAttendues() {
        assertTrue(hasTitle(level("level-1"), "Vakiteny"));
        assertTrue(hasTitle(level("level-2"), "Fanazavana"));
        assertTrue(hasType(level("level-3"), ProgramNode.TYPE_CREED));
        assertTrue(hasTitle(level("level-3"), "Rakitra"));
        assertTrue(hasTitle(level("level-4"), "Raharaham-piangonana"));
        assertTrue(hasTitle(level("level-4"), "Famaranana"));
    }

    @Test
    public void niveau1SansHiraEtNiveau2AvecHira() {
        assertFalse("le niveau 1 ne doit pas contenir de Hira",
                hasType(level("level-1"), ProgramNode.TYPE_HYMN));
        assertTrue(hasType(level("level-2"), ProgramNode.TYPE_HYMN));
    }

    @Test
    public void niveau4EstUnSurEnsembleDuNiveau3() {
        ProgramNode l4 = level("level-4");
        // Briques du niveau 3
        assertTrue(hasTitle(l4, "Fitarihana"));
        assertTrue(hasTitle(l4, "Hira"));
        assertTrue(hasTitle(l4, "Vavaka"));
        assertTrue(hasTitle(l4, "Vakiteny"));
        assertTrue(hasTitle(l4, "Fanazavana"));
        assertTrue(hasType(l4, ProgramNode.TYPE_CREED));
        assertTrue(hasTitle(l4, "Rakitra"));
        assertTrue(hasTitle(l4, "Toriteny"));
        assertTrue(hasTitle(l4, "Tsodrano"));
        // Éléments ajoutés par le niveau 4
        assertTrue(hasTitle(l4, "Raharaham-piangonana"));
        assertTrue(hasTitle(l4, "Vavaka fangatahana"));
        assertTrue(hasTitle(l4, "Asa vavolombelona"));
        assertTrue(hasTitle(l4, "Famaranana"));
    }

    @Test
    public void chaqueLitorjiaEstNonVideEtPartageLesBriques() {
        for (String id : new String[]{"litorjia-1", "litorjia-2", "litorjia-3"}) {
            ProgramNode root = ProgramGenerator.instantiate(
                    id, TemplateCatalog.tree(id), "Fivoriana");
            assertNotNull(id, root);
            assertTrue(id + " vide", count(root) > 0);
            assertTrue(id + " sans Vakiteny", hasTitle(root, "Vakiteny"));
            assertTrue(id + " sans Fanekem-pinoana",
                    hasType(root, ProgramNode.TYPE_CREED));
        }
    }

    @Test
    public void rdzNaPasDEteDansLeCatalogue() {
        for (TemplateCatalog.TemplateInfo t : TemplateCatalog.catalog()) {
            assertFalse(t.id.equals("level-5"));
            assertFalse(t.label.toLowerCase().contains("rdz"));
            assertNotNull("arbre absent pour " + t.id, TemplateCatalog.tree(t.id));
        }
    }

    @Test
    public void gabaritInconnuNeProduitRien() {
        assertNull(TemplateCatalog.tree("gabarit-inexistant"));
    }

    // =========================================================
    // Aides
    // =========================================================

    private static ProgramNode level(String id) {
        ProgramNode root = ProgramGenerator.instantiate(
                id, TemplateCatalog.tree(id), "Fivoriana");
        assertNotNull(id, root);
        return root;
    }

    private static int count(ProgramNode n) {
        int c = 1;
        for (ProgramNode child : n.children) c += count(child);
        return c;
    }

    private static void assertParents(ProgramNode n, String expectedParent) {
        for (ProgramNode c : n.children) {
            assertEquals("parent de " + c.type + " " + c.title,
                    expectedParent, c.parentId);
            assertParents(c, c.id);
        }
    }

    private static boolean hasTitle(ProgramNode n, String title) {
        if (title.equals(n.title)) return true;
        for (ProgramNode c : n.children) if (hasTitle(c, title)) return true;
        return false;
    }

    private static boolean hasType(ProgramNode n, String type) {
        if (type.equals(n.type)) return true;
        for (ProgramNode c : n.children) if (hasType(c, type)) return true;
        return false;
    }
}
