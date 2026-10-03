package com.fa.baiboly;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fa.baiboly.data.parser.BibleRefParser;

import org.junit.Test;

import java.util.List;

/**
 * Tests du parser centralisé {@link BibleRefParser} (JVM pur, sans Android).
 */
public class BibleRefParserTest {

    // =========================
    // Résolution de livres
    // =========================

    @Test
    public void jakDonneJakobaPasMpanjaka() {
        // "Jak" est l'acronyme exact de Jakoba (59), JAMAIS Mpanjaka (11)
        assertEquals(Integer.valueOf(59),
                BibleRefParser.resolveBookNumber("Jak"));
        assertEquals(Integer.valueOf(59),
                BibleRefParser.resolveBookNumber("jak"));
        assertEquals(Integer.valueOf(59),
                BibleRefParser.resolveBookNumber("Jakoba"));
    }

    @Test
    public void acronymesOfficiels() {
        assertEquals(Integer.valueOf(1), BibleRefParser.resolveBookNumber("Gen"));
        assertEquals(Integer.valueOf(40), BibleRefParser.resolveBookNumber("Mat"));
        assertEquals(Integer.valueOf(43), BibleRefParser.resolveBookNumber("Jao"));
        assertEquals(Integer.valueOf(58), BibleRefParser.resolveBookNumber("Heb"));
        assertEquals(Integer.valueOf(66), BibleRefParser.resolveBookNumber("Apo"));
    }

    @Test
    public void livresMultiplesAvecPrefixe() {
        assertEquals(Integer.valueOf(46), BibleRefParser.resolveBookNumber("1Kor"));
        assertEquals(Integer.valueOf(46), BibleRefParser.resolveBookNumber("1 Kor"));
        assertEquals(Integer.valueOf(46), BibleRefParser.resolveBookNumber("1 Korintiana"));
        assertEquals(Integer.valueOf(62), BibleRefParser.resolveBookNumber("I Jao"));
        assertEquals(Integer.valueOf(63), BibleRefParser.resolveBookNumber("II Jao"));
        assertEquals(Integer.valueOf(64), BibleRefParser.resolveBookNumber("III Jao"));
        assertEquals(Integer.valueOf(9), BibleRefParser.resolveBookNumber("1Sam"));
        assertEquals(Integer.valueOf(10), BibleRefParser.resolveBookNumber("2Sam"));
        assertEquals(Integer.valueOf(11), BibleRefParser.resolveBookNumber("1Mpa"));
    }

    @Test
    public void accentsEtPonctuationIgnores() {
        assertEquals(Integer.valueOf(44),
                BibleRefParser.resolveBookNumber("Asan'ny Apostoly"));
        assertEquals(Integer.valueOf(44),
                BibleRefParser.resolveBookNumber("Asanny apostoly"));
        assertEquals(Integer.valueOf(19),
                BibleRefParser.resolveBookNumber("salamo"));
    }

    @Test
    public void livreInconnuRenvoieNull() {
        assertNull(BibleRefParser.resolveBookNumber("xyz"));
        assertNull(BibleRefParser.resolveBookNumber(""));
        assertNull(BibleRefParser.resolveBookNumber(null));
    }

    @Test
    public void prefixeUniqueAutorisé() {
        // "jakob" n'est pas un alias exact mais un préfixe unique -> Jakoba
        assertEquals(Integer.valueOf(59),
                BibleRefParser.resolveBookNumber("jakob"));
        // "hebre" -> Hebreo
        assertEquals(Integer.valueOf(58),
                BibleRefParser.resolveBookNumber("hebre"));
    }

    // =========================
    // Parsing de références
    // =========================

    @Test
    public void referenceSimple() {
        BibleRefParser.ParsedRef r = BibleRefParser.parse("Jao 3");
        assertNotNull(r);
        assertEquals(43, r.bookNumber);
        assertEquals(3, r.startChapter);
        assertEquals(1, r.startVerse);
        assertEquals(3, r.endChapter);
        assertTrue(r.isFullChapter());
        assertEquals("Jao 3", BibleRefParser.format(r));
    }

    @Test
    public void referenceAvecVerset() {
        BibleRefParser.ParsedRef r = BibleRefParser.parse("Jao 3:16");
        assertNotNull(r);
        assertEquals(3, r.startChapter);
        assertEquals(16, r.startVerse);
        assertEquals(16, r.endVerse);
        assertEquals("Jao 3:16", BibleRefParser.format(r));
    }

    @Test
    public void formesColleesEtVirgules() {
        BibleRefParser.ParsedRef r = BibleRefParser.parse("Heb11,1");
        assertNotNull(r);
        assertEquals(58, r.bookNumber);
        assertEquals(11, r.startChapter);
        assertEquals(1, r.startVerse);
        assertEquals("Heb 11:1", BibleRefParser.format(r));
    }

    @Test
    public void plageDeVersets() {
        BibleRefParser.ParsedRef r = BibleRefParser.parse("1Kor13,4-7");
        assertNotNull(r);
        assertEquals(46, r.bookNumber);
        assertEquals(13, r.startChapter);
        assertEquals(4, r.startVerse);
        assertEquals(7, r.endVerse);
        assertEquals("1Kor 13:4-7", BibleRefParser.format(r));
    }

    @Test
    public void plageCrossChapitres() {
        BibleRefParser.ParsedRef r = BibleRefParser.parse("Asan'ny Apostoly 7:54-8:1");
        assertNotNull(r);
        assertEquals(44, r.bookNumber);
        assertEquals(7, r.startChapter);
        assertEquals(54, r.startVerse);
        assertEquals(8, r.endChapter);
        assertEquals(1, r.endVerse);
    }

    @Test
    public void plageDeChapitres() {
        BibleRefParser.ParsedRef r = BibleRefParser.parse("Jao 3-4");
        assertNotNull(r);
        assertEquals(3, r.startChapter);
        assertEquals(4, r.endChapter);
        assertTrue(r.isFullChapter());
        assertEquals("Jao 3-4", BibleRefParser.format(r));
    }

    @Test
    public void separateurPoint() {
        BibleRefParser.ParsedRef r = BibleRefParser.parse("Jao 3.16");
        assertNotNull(r);
        assertEquals(16, r.startVerse);
    }

    @Test
    public void referenceInvalideRenvoieNull() {
        assertNull(BibleRefParser.parse("chiffre 3"));
        assertNull(BibleRefParser.parse(null));
        assertNull(BibleRefParser.parse(""));
        assertNull(BibleRefParser.parse("pahataperana 3"));
    }

    // =========================
    // Détection dans texte libre
    // =========================

    @Test
    public void detectionDansTexte() {
        List<BibleRefParser.DetectedRef> refs = BibleRefParser.detectReferences(
                "Araka ny Jao 3:16 ao amin'ny filazantsara, ary Heb11,1 koa.");
        assertEquals(2, refs.size());
        assertEquals("Jao 3:16", refs.get(0).reference);
        assertEquals("Heb 11:1", refs.get(1).reference);
    }

    @Test
    public void detectionNeTraversePasLesParagraphes() {
        // "pahataperana.\n3.16" ne doit pas matcher à travers le saut de ligne
        List<BibleRefParser.DetectedRef> refs = BibleRefParser.detectReferences(
                "fitaovana marina.\n3.16 ary marina izany");
        assertTrue(refs.isEmpty());
    }

    @Test
    public void detectionIgnoreLesFauxPositifs() {
        // Mots quelconques suivis de chiffres : aucun livre reconnu
        List<BibleRefParser.DetectedRef> refs = BibleRefParser.detectReferences(
                "chapitre 3 verset 12 et nombre 42");
        assertTrue(refs.isEmpty());
    }
}
