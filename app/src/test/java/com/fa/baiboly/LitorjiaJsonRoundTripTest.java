package com.fa.baiboly;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.fa.baiboly.data.litorjia.LitorjiaJsonService;
import com.fa.baiboly.data.litorjia.Program;
import com.fa.baiboly.data.litorjia.ProgramGenerator;
import com.fa.baiboly.data.litorjia.ProgramNode;
import com.fa.baiboly.data.litorjia.TemplateCatalog;

import org.junit.Test;

/**
 * Aller-retour export → import → export (§4.6, §11) et tolérance de
 * l'import (donnée non fiable) sans base Android.
 */
public class LitorjiaJsonRoundTripTest {

    private static final LitorjiaJsonService.TemplateLookup ANY_TEMPLATE = id -> true;

    @Test
    public void exportPuisImportRedonneLeMemeArbre() {
        Program p = program("litorjia-1", "Alahady 8 Novambra", "2026-11-08");
        String first = LitorjiaJsonService.export(p);

        LitorjiaJsonService.Report report = LitorjiaJsonService.parse(first, ANY_TEMPLATE);
        assertTrue(report.error, report.ok);
        assertNotNull(report.root);

        String second = LitorjiaJsonService.exportOf(report.root, report);
        assertEquals("l'export doit être symétrique de l'import", first, second);
    }

    @Test
    public void tousLesGabaritsFontUnAllerRetourSansPerte() {
        for (TemplateCatalog.TemplateInfo t : TemplateCatalog.catalog()) {
            Program p = program(t.id, "Fivoriana " + t.id, "2026-11-08");
            String first = LitorjiaJsonService.export(p);
            LitorjiaJsonService.Report r = LitorjiaJsonService.parse(first, ANY_TEMPLATE);
            assertTrue(t.id + " : " + r.error, r.ok);
            assertEquals(t.id, first, LitorjiaJsonService.exportOf(r.root, r));
        }
    }

    @Test
    public void typeInconnuDevientCustomAvecAvertissement() {
        String json = "{\"schema\":\"litorjia/1\",\"title\":\"T\",\"nodes\":["
                + "{\"type\":\"inconnu\",\"title\":\"Zavatra\"}]}";
        LitorjiaJsonService.Report r = LitorjiaJsonService.parse(json, ANY_TEMPLATE);
        assertTrue(r.ok);
        assertEquals(ProgramNode.TYPE_CUSTOM, r.root.children.get(0).type);
        assertFalse(r.warnings.isEmpty());
    }

    @Test
    public void livreInconnuDonneUnNoeudACompleter() {
        String json = "{\"schema\":\"litorjia/1\",\"title\":\"T\",\"nodes\":["
                + "{\"type\":\"bible\",\"bible\":[{\"book\":\"Livre imaginaire\","
                + "\"chapter\":2,\"ranges\":[{\"from\":\"1\",\"to\":\"3\"}]}]}]}";
        LitorjiaJsonService.Report r = LitorjiaJsonService.parse(json, ANY_TEMPLATE);
        assertTrue(r.ok);
        ProgramNode bible = r.root.children.get(0);
        assertTrue("un livre inconnu doit être marqué à compléter", bible.isIncomplete());
        assertFalse(r.warnings.isEmpty());
    }

    @Test
    public void chantSansNumeroEstConserveEtIncomplet() {
        String json = "{\"schema\":\"litorjia/1\",\"title\":\"T\",\"nodes\":["
                + "{\"type\":\"hymn\",\"hymn\":{\"book\":\"FFPM\"}}]}";
        LitorjiaJsonService.Report r = LitorjiaJsonService.parse(json, ANY_TEMPLATE);
        assertTrue(r.ok);
        ProgramNode hymn = r.root.children.get(0);
        assertEquals("FFPM", hymn.hymnBook());
        assertTrue(hymn.isIncomplete());
    }

    @Test
    public void schemaInconnuRefuse() {
        LitorjiaJsonService.Report r = LitorjiaJsonService.parse(
                "{\"schema\":\"litorjia/99\",\"nodes\":[]}", ANY_TEMPLATE);
        assertFalse(r.ok);
        assertNotNull(r.error);
    }

    @Test
    public void jsonEntoureDeTexteEstAccepte() {
        String wrapped = "Voici le résultat :\n```json\n"
                + "{\"schema\":\"litorjia/1\",\"title\":\"T\",\"nodes\":[]}\n```";
        LitorjiaJsonService.Report r = LitorjiaJsonService.parse(wrapped, ANY_TEMPLATE);
        assertTrue(r.error, r.ok);
    }

    private static Program program(String templateId, String title, String date) {
        ProgramNode root = ProgramGenerator.instantiate(
                templateId, TemplateCatalog.tree(templateId), title);
        assertNotNull(root);
        Program p = new Program();
        p.id = "p-test";
        p.title = title;
        p.serviceDate = date;
        p.templateId = templateId;
        p.root = root;
        return p;
    }
}
