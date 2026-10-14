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
    public void siteFjkmFormats() {
        // Formats utilisés par le site fjkm.mg (Mofonaina) avec espaces :
        // "1 Tesaloniana 5,16 - 25", "Sal 96,7-10", "Hira 514: 1, 4" etc.
        BibleRefParser.ParsedRef r = BibleRefParser.parse("1 Tesaloniana 5:16 - 25");
        assertNotNull(r);
        assertEquals(52, r.bookNumber);
        assertEquals(5, r.startChapter);
        assertEquals(16, r.startVerse);
        assertEquals(25, r.endVerse);
        assertEquals("1Tes 5:16-25", BibleRefParser.format(r));

        BibleRefParser.ParsedRef sal = BibleRefParser.parse("Sal 96,7-10");
        assertNotNull(sal);
        assertEquals(19, sal.bookNumber);
        assertEquals(96, sal.startChapter);
        assertEquals(7, sal.startVerse);
        assertEquals(10, sal.endVerse);
        assertEquals("Sal 96:7-10", BibleRefParser.format(sal));
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

    @Test
    public void listeDiscreteAvecLettre() {
        // "and 39b,42,44" dans le chat IA -> référence "Lio 2:39b,42,44"
        BibleRefParser.ParsedRef r = BibleRefParser.parse("Lio 2:39b,42,44");
        assertNotNull(r);
        assertEquals(42, r.bookNumber);
        assertEquals(2, r.startChapter);
        assertNotNull(r.discreteVerses);
        assertEquals(3, r.discreteVerses.size());
        assertEquals(39, r.discreteVerses.get(0)[0]);
        assertEquals(42, r.discreteVerses.get(1)[0]);
        assertEquals(44, r.discreteVerses.get(2)[0]);
        // lettre d'affichage sur le 1er item uniquement : "b"
        assertEquals("b", r.discreteLetters.get(0));
        assertNull(r.discreteLetters.get(1));
        assertNull(r.discreteLetters.get(2));
        // Affichage : la lettre est conservée dans le titre de page
        assertEquals("Lio 2:39b,42,44", BibleRefParser.format(r));
    }

    @Test
    public void listeDiscreteAvecLettreA() {
        BibleRefParser.ParsedRef r = BibleRefParser.parse("Lio 2:39a,42,44");
        assertNotNull(r);
        assertEquals(3, r.discreteVerses.size());
        assertEquals("a", r.discreteLetters.get(0));
        assertEquals("Lio 2:39a,42,44", BibleRefParser.format(r));
    }

    @Test
    public void formeClassiquePrioritaireSurListe() {
        // "Heb11,1" = ch. 11, v. 1 (pas de ':') -> PAS une liste discrete
        BibleRefParser.ParsedRef r = BibleRefParser.parse("Heb11,1");
        assertNotNull(r);
        assertNull(r.discreteVerses);
        assertEquals(11, r.startChapter);
        assertEquals("Heb 11:1", BibleRefParser.format(r));

        // "Jao 3:16-17" = plage (un seul item sans lettre) -> PAS une liste
        BibleRefParser.ParsedRef p = BibleRefParser.parse("Jao 3:16-17");
        assertNotNull(p);
        assertNull(p.discreteVerses);
        assertEquals("Jao 3:16-17", BibleRefParser.format(p));

        // "1 Tesaloniana 5:16 - 25" : plage avec espaces -> PAS une liste
        BibleRefParser.ParsedRef s = BibleRefParser.parse("1 Tesaloniana 5:16 - 25");
        assertNotNull(s);
        assertNull(s.discreteVerses);
        assertEquals(16, s.startVerse);
        assertEquals(25, s.endVerse);
    }

    @Test
    public void andMarkerListeAvecLettreEtVirgules() {
        java.util.regex.Matcher m = BibleRefParser.AND_MARKER_PATTERN
                .matcher("... dia (and 39b,42,44).") ;
        assertTrue(m.find());
        assertEquals("39b,42,44", m.group(1));

        // Espaces autour des virgules tolérés
        java.util.regex.Matcher m2 = BibleRefParser.AND_MARKER_PATTERN
                .matcher("and. 39a, 42, 44");
        assertTrue(m2.find());
        assertEquals("39a, 42, 44", m2.group(1));

        // La référence "Lio 2:" + suffixe se parse en liste discrète
        BibleRefParser.ParsedRef r = BibleRefParser.parse("Lio 2:39b,42,44");
        assertNotNull(r);
        assertEquals(3, r.discreteVerses.size());

        // Le mot "and" sans numéro n'est pas une référence
        java.util.regex.Matcher m3 = BibleRefParser.AND_MARKER_PATTERN
                .matcher("andininy manontolo");
        assertFalse(m3.find());
    }

    @Test
    public void versetSeulAvecLettre() {
        // "Jao 3:16b" : lettre d'article -> liste à 1 verset, complet affiché
        BibleRefParser.ParsedRef r = BibleRefParser.parse("Jao 3:16b");
        assertNotNull(r);
        assertNotNull(r.discreteVerses);
        assertEquals(1, r.discreteVerses.size());
        assertEquals(16, r.discreteVerses.get(0)[0]);
        assertEquals("b", r.discreteLetters.get(0));
        assertEquals("Jao 3:16b", BibleRefParser.format(r));
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
