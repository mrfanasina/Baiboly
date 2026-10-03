package com.fa.baiboly.data.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser UNIQUE et centralisé des livres et références bibliques.
 *
 * Il est utilisé partout dans l'app :
 *  - lecture d'une référence saisie ("Heb11,1", "Jao 3:16", "Asan'ny Apostoly 7:54-8:1")
 *  - résolution d'un nom/acronyme de livre vers son numéro ("Jak" -> 59, JAMAIS Mpanjaka)
 *  - détection de références dans un texte libre (liens cliquables Mofonaina, chat IA...)
 *
 * ⚠️ Aucune dépendance Android ici : la classe est testable en JVM pure.
 */
public final class BibleRefParser {

    // =====================================================
    // TABLE DES LIVRES (numéro canonique + acronymes)
    // =====================================================
    // mg = acronyme officiel affiché (bible_books.mg)
    // Les alias sont matchés en insensible à la casse, accents/espaces/
    // points/apostrophes supprimés.
    // La correspondance EXACTE prime toujours : "jak" -> Jakoba (59) et
    // jamais "Mpanjaka" via un LIKE. Le fallback préfixe ne sert qu'aux
    // saisies incomplètes non ambiguës ("jakob" -> Jakoba).
    private static final String[][] BOOKS = {
        {"1",  "Gen",  "Genesisy"},
        {"2",  "Eks",  "Eksodosy", "Exo", "Ex", "Eksod"},
        {"3",  "Lev",  "Levitikosy"},
        {"4",  "Nom",  "Nomery", "Nomre", "Nomb"},
        {"5",  "Deo",  "Deoteronomia", "Deu", "Deut"},
        {"6",  "Jos",  "Josoa", "Josue"},
        {"7",  "Mpits","Mpitsara", "Jug"},
        {"8",  "Rot",  "Rota", "Rut", "Ruth"},
        {"9",  "1Sam", "1 Samoela", "1Samoela", "1S", "1Sa"},
        {"10", "2Sam", "2 Samoela", "2Samoela", "2S", "2Sa"},
        {"11", "1Mpa", "1 Mpanjaka", "1Mpanjaka", "1Roi", "1Rois", "1R", "1Kin"},
        {"12", "2Mpa", "2 Mpanjaka", "2Mpanjaka", "2Roi", "2Rois", "2R", "2Kin"},
        {"13", "1Tan", "1 Tantara", "1Tantara", "1Chr", "1Chroniques", "1Ch", "1Par"},
        {"14", "2Tan", "2 Tantara", "2Tantara", "2Chr", "2Chroniques", "2Ch", "2Par"},
        {"15", "Ezr",  "Ezra", "Esd"},
        {"16", "Neh",  "Nehemia", "Ne"},
        {"17", "Est",  "Estera", "Esth"},
        {"18", "Job",  "Joba"},
        {"19", "Sal",  "Salamo", "Ps", "Psa", "Psal", "Psaume", "Psaumes"},
        {"20", "Oha",  "Ohabolana", "Pro", "Prov", "Pr"},
        {"21", "Mpito","Mpitoriteny", "Ecc", "Eccl", "Ec"},
        {"22", "Ton",  "Tononkira", "Can", "Cant"},
        {"23", "Isa",  "Isaia", "Esa", "Es"},
        {"24", "Jer",  "Jeremia"},
        {"25", "Fit",  "Fitomaniana", "Lam"},
        {"26", "Eze",  "Ezekiela", "Ezek"},
        {"27", "Dan",  "Daniela", "Dn"},
        {"28", "Hos",  "Hosea", "Os"},
        {"29", "Joe",  "Joela", "Joel"},
        {"30", "Amo",  "Amosa", "Am", "Amos"},
        {"31", "Oba",  "Obadia", "Abd", "Ob"},
        {"32", "Jon",  "Jona", "Jonas"},
        {"33", "Mik",  "Mika", "Mic", "Mi"},
        {"34", "Nah",  "Nahoma", "Na"},
        {"35", "Hab",  "Habakoka"},
        {"36", "Zef",  "Zefania", "Sop", "So"},
        {"37", "Hag",  "Hagay", "Agg", "Ag"},
        {"38", "Zak",  "Zakaria", "Zac", "Zc"},
        {"39", "Mal",  "Malakia"},
        {"40", "Mat",  "Matio", "Mt", "Matt"},
        {"41", "Mar",  "Marka", "Mc", "Mk", "Marc"},
        {"42", "Lio",  "Lioka", "Luc", "Lc", "Luk", "Luke"},
        {"43", "Jao",  "Jaona", "Jea", "Jn", "Joh", "John", "Jean"},
        {"44", "Asa",  "Asan'ny Apostoly", "Asanny Apostoly", "Act", "Ac", "Actes"},
        {"45", "Rom",  "Romana", "Rm", "Romains"},
        {"46", "1Kor", "1 Korintiana", "1Korintiana", "1Corinthiens", "1Cor", "1Co", "1 Cor"},
        {"47", "2Kor", "2 Korintiana", "2Korintiana", "2Corinthiens", "2Cor", "2Co", "2 Cor"},
        {"48", "Gal",  "Galatiana", "Ga"},
        {"49", "Efe",  "Efesiana", "Eph", "Ep"},
        {"50", "Fili", "Filipiana", "Phili", "Phil", "Ph"},
        {"51", "Kol",  "Kolosiana", "Col", "Coloss"},
        {"52", "1Tes", "1 Tesalonianina", "1Tesalonianina", "1The", "1Th", "1 Thess"},
        {"53", "2Tes", "2 Tesalonianina", "2Tesalonianina", "2The", "2Th", "2 Thess"},
        {"54", "1Tim", "1 Timoty", "1Timoty", "1Ti"},
        {"55", "2Tim", "2 Timoty", "2Timoty", "2Ti"},
        {"56", "Tit",  "Titosy", "Tite"},
        {"57", "File", "Filemona", "Phile", "Phlm"},
        {"58", "Heb",  "Hebreo", "He"},
        {"59", "Jak",  "Jakoba", "Jac", "Jam", "James"},
        {"60", "1Pet", "1 Petera", "1Petera", "1Pierre", "1Pie", "1P"},
        {"61", "2Pet", "2 Petera", "2Petera", "2Pierre", "2Pie", "2P"},
        {"62", "1Jao", "1 Jaona", "1Jaona", "1Jean", "1Jea", "1Jn", "1Joh"},
        {"63", "2Jao", "2 Jaona", "2Jaona", "2Jean", "2Jea", "2Jn", "2Joh"},
        {"64", "3Jao", "3 Jaona", "3Jaona", "3Jean", "3Jea", "3Jn", "3Joh"},
        {"65", "Jod",  "Joda", "Jud", "Jude"},
        {"66", "Apo",  "Apokalypsy", "Ap", "Rev", "Reve", "Apoc"},
    };

    // =====================================================
    // NORMALISATION
    // =====================================================

    /**
     * Clé normalisée d'un nom de livre : minuscules, accents retirés,
     * apostrophes/espaces/points/tirets supprimés. La carte des alias est
     * construite avec cette même clé.
     */
    private static String bookKey(String raw) {
        if (raw == null) return "";
        String s = java.text.Normalizer.normalize(raw.toLowerCase(Locale.ROOT),
                java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        s = s.replace("’", "'")
                .replace("'", "")
                .replace("-", "")
                .replace(" ", "")
                .replace(".", "")
                .replace(",", "");
        return s;
    }

    // Construit une fois, au chargement de la classe, la carte alias -> numéro.
    private static final Map<String, Integer> ALIAS_MAP = new LinkedHashMap<>();

    static {
        for (String[] book : BOOKS) {
            int numero = Integer.parseInt(book[0]);
            for (int i = 1; i < book.length; i++) {
                String key = bookKey(book[i]);
                if (!key.isEmpty() && !ALIAS_MAP.containsKey(key)) {
                    ALIAS_MAP.put(key, numero);
                }
            }
        }
    }

    // =====================================================
    // RÉSOLUTION D'UN NOM DE LIVRE
    // =====================================================

    /**
     * Numéro canonique (1..66) ou null si inconnu.
     * Priorité : correspondance EXACTE (acronyme ou nom complet), puis
     * fallback préfixe si unique (ex "jakob" -> Jakoba). Plusieurs livres
     * candidats différents = ambigu -> null.
     */
    public static Integer resolveBookNumber(String raw) {
        if (raw == null) return null;

        // 0) Préfixe romain en tête ("I Jao", "II Kor", "III Jao") -> 1/2/3.
        //    Même normalisation que parse() : resolveBookNumber seul doit
        //    aussi savoir résoudre ces formes. La regex exige I+espace+lettre,
        //    donc "Isa", "Job" ne sont jamais pris pour un préfixe romain.
        java.util.regex.Matcher rom = LEADING_ROMAN.matcher(raw);
        if (rom.find() && rom.start() == 0) {
            String rest = raw.substring(rom.end()).trim();
            if (!rest.isEmpty()) {
                Integer viaRoman = resolveBookNumber(
                        rom.group(1).length() + " " + rest);
                if (viaRoman != null) return viaRoman;
            }
        }

        String key = bookKey(raw);
        if (key.isEmpty()) return null;

        // 1) Alias exact (acronyme ou nom long, ex: "jak", "jakoba", "1kor")
        Integer exact = ALIAS_MAP.get(key);
        if (exact != null) return exact;

        // 2) Fallback tolérant : préfixe unique (ex: "hebre" -> Hebreo)
        //    NB: "jak" est un alias exact donc ne passe jamais ici.
        Integer prefixMatch = null;
        for (Map.Entry<String, Integer> e : ALIAS_MAP.entrySet()) {
            if (e.getKey().startsWith(key)) {
                if (prefixMatch != null && !prefixMatch.equals(e.getValue())) {
                    return null; // ambigu (plusieurs livres) -> on abandonne
                }
                prefixMatch = e.getValue();
            }
        }
        return prefixMatch;
    }

    // =====================================================
    // PARSING D'UNE RÉFÉRENCE COMPLÈTE
    // =====================================================

    /** Résultat du parsing d'une référence. */
    public static class ParsedRef {
        public String bookName;   // nom brut du livre reconnu (ex "Heb", "Asan'ny Apostoly")
        public int bookNumber;    // 1..66
        public int startChapter;
        public int startVerse;
        public int endChapter;
        public int endVerse;
        /** true si au moins un nombre (chapitre/verset) a été trouvé. */
        public boolean hasNumbers;

        public boolean isFullChapter() {
            return startVerse <= 1 && endVerse >= 999;
        }
    }

    // Préfixe arabe en tête de référence : "1 Kor 13:4", "2Sam 1:1"...
    // (un seul chiffre 1-3, non suivi d'un autre chiffre)
    private static final Pattern LEADING_ARABIC =
            Pattern.compile("^\\s*([1-3])(?!\\d)\\s*");

    // Préfixe romain : "I Jao", "II Korintiana", "III Jao"
    // (obligé d'être suivi d'un espace + lettre pour ne pas manger "Isa", "Job"...)
    private static final Pattern LEADING_ROMAN =
            Pattern.compile("^\\s*(III|II|I)\\s+(?=[A-Za-zÀ-ÿ])");

    // Queue numérique d'une référence, à partir du premier chiffre :
    // "11.1" | "11" | "3.16-17" | "7.54-8.1" | "3-4" (plage de chapitres)...
    private static final Pattern TAIL_PATTERN = Pattern.compile(
            "(\\d+)"                              // g1 : chapitre début
                    + "(?:\\s*[:.]\\s*(\\d+))?"   // g2 : verset début (séparé par : ou .)
                    + "(?:\\s*-\\s*(\\d+)"        // g3 : nombre après tiret
                    + "(?:\\s*[:.]\\s*(\\d+))?"   // g4 : verset fin (si ':' présent)
                    + ")?"
    );

    /**
     * Extrait le nom de livre + les nombres d'une référence libre.
     * Accepte les séparateurs ":", ".", ",", les nombres collés au livre
     * ("Heb11,1", "1Kor13,4-7"), les tirets – — et les plages de chapitres
     * ("Jao 3-4").
     *
     * @return null si aucun livre connu n'est reconnu
     */
    public static ParsedRef parse(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.isEmpty()) return null;

        // Normalisation : tirets unicode -> '-', virgule -> point (séparateur
        // chapitre:verset), puis détachement des nombres collés au livre.
        s = s.replace('\u2013', '-').replace('\u2014', '-');
        s = s.replace(',', '.');
        s = s.replaceAll("([A-Za-zÀ-ÿ'])(\\d)", "$1 $2");

        // Préfixe de livre multiple ("1 Kor", "II Sam") consommé à part pour
        // ne pas le confondre avec le numéro de chapitre.
        Integer prefix = null;
        Matcher arab = LEADING_ARABIC.matcher(s);
        if (arab.find()) {
            prefix = arab.group(1).charAt(0) - '0';
            s = s.substring(arab.end());
        } else {
            Matcher rom = LEADING_ROMAN.matcher(s);
            if (rom.find()) {
                prefix = rom.group(1).length();
                s = s.substring(rom.end());
            }
        }

        // Sépare le texte du livre de la queue numérique.
        int firstDigit = -1;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) { firstDigit = i; break; }
        }
        String bookText = (firstDigit >= 0) ? s.substring(0, firstDigit) : s;
        String tail = (firstDigit >= 0) ? s.substring(firstDigit) : "";

        Integer num = resolveBookText(prefix, bookText);
        if (num == null) return null;

        return buildReading(num, bookText, tail);
    }

    /**
     * Résout le livre parmi les sous-chaînes de tokens (tolère un ou
     * plusieurs mots parasites avant/après, ex "Vakiteny Jao" ou
     * "Jao chapitre"), avec le préfixe numérique réinjecté.
     */
    private static Integer resolveBookText(Integer prefix, String bookText) {
        String trimmed = bookText.trim();
        if (trimmed.isEmpty()) return null;
        String[] tokens = trimmed.split("\\s+");

        for (int startIdx = 0; startIdx < tokens.length; startIdx++) {
            for (int endIdx = tokens.length - 1; endIdx >= startIdx; endIdx--) {
                StringBuilder sb = new StringBuilder();
                for (int i = startIdx; i <= endIdx; i++) {
                    if (i > startIdx) sb.append(' ');
                    sb.append(tokens[i]);
                }
                String candidate = sb.toString();
                if (prefix != null) {
                    Integer n = resolveBookNumber(prefix + candidate);
                    if (n != null) return n;
                    n = resolveBookNumber(prefix + " " + candidate);
                    if (n != null) return n;
                }
                Integer n = resolveBookNumber(candidate);
                if (n != null) return n;
            }
        }
        return null;
    }

    /**
     * Interprète la queue numérique de façon structurelle :
     *  - "3"          -> chapitre 3 entier
     *  - "3-4"        -> chapitres 3 à 4 entiers
     *  - "3.16"       -> chapitre 3, verset 16
     *  - "3.16-17"    -> chapitre 3, versets 16 à 17
     *  - "7.54-8.1"   -> chapitre 7 v54 à chapitre 8 v1
     */
    private static ParsedRef buildReading(int bookNumber, String bookName, String tail) {
        ParsedRef ref = new ParsedRef();
        ref.bookNumber = bookNumber;
        ref.bookName = bookName == null ? "" : bookName.trim();

        ref.startChapter = 1;
        ref.startVerse = 1;
        ref.endChapter = ref.startChapter;
        ref.endVerse = 999;
        ref.hasNumbers = false;

        if (tail == null || tail.isEmpty()) return ref;

        Matcher m = TAIL_PATTERN.matcher(tail);
        if (!m.find()) return ref;

        ref.hasNumbers = true;
        ref.startChapter = Integer.parseInt(m.group(1));
        ref.endChapter = ref.startChapter;
        ref.endVerse = 999;

        if (m.group(2) != null) {
            // Forme "chapitre.verset[-...]"
            ref.startVerse = Integer.parseInt(m.group(2));
            ref.endVerse = ref.startVerse;

            if (m.group(3) != null) {
                int n3 = Integer.parseInt(m.group(3));
                if (m.group(4) != null) {
                    ref.endChapter = n3;
                    ref.endVerse = Integer.parseInt(m.group(4));
                } else {
                    ref.endVerse = n3;
                }
            }
        } else if (m.group(3) != null) {
            // Forme "chapitre-chapitre" (chapitres entiers)
            ref.endChapter = Integer.parseInt(m.group(3));
        }
        return ref;
    }

    /**
     * Référence normalisée affichable : "Heb 11:1", "Jao 3:16-17",
     * "1Kor 13:4-7", "Jao 3-4", "Sal 66"...
     */
    public static String format(ParsedRef ref) {
        if (ref == null) return "";
        StringBuilder sb = new StringBuilder(shortNameOf(ref.bookNumber))
                .append(' ')
                .append(ref.startChapter);

        if (ref.endVerse >= 999) {
            // Chapitres entiers ("Sal 66" ou "Jao 3-4")
            if (ref.endChapter != ref.startChapter) {
                sb.append('-').append(ref.endChapter);
            }
        } else {
            sb.append(':').append(ref.startVerse);
            if (ref.endChapter != ref.startChapter) {
                sb.append('-').append(ref.endChapter).append(':')
                        .append(ref.endVerse);
            } else if (ref.endVerse != ref.startVerse) {
                sb.append('-').append(ref.endVerse);
            }
        }
        return sb.toString();
    }

    // =====================================================
    // DÉTECTION DANS UN TEXTE LIBRE (liens cliquables)
    // =====================================================

    /**
     * Référence détectée dans un texte : plage [start,end) + référence
     * normalisée à ouvrir ("Heb 11:1", "Jao 3:16-17"...).
     */
    public static class DetectedRef {
        public final int start;
        public final int end;
        public final String reference;

        public DetectedRef(int start, int end, String reference) {
            this.start = start;
            this.end = end;
            this.reference = reference;
        }
    }

    // Livre : lettres (avec accents/apostrophes) éventuellement précédées d'un
    // préfixe 1-3, puis espace OU rien, puis les nombres.
    // Espaces/tabulations uniquement entre les mots : jamais de saut de ligne,
    // pour ne pas "traverser" un paragraphe ("pahataperana.\n3." ne matche pas).
    // Séparateur chapitre:verset : ":", "." OU "," (forme malgache usuelle).
    // Ex matchés : "Jao 3", "Heb11,1", "1 Kor 13:4-7", "1Kor13,4-7",
    //              "Jao 3.16", "Jao 3,16", "Asan'ny Apostoly 7:54-8:1", "Jod 1:2"
    private static final Pattern REF_IN_TEXT = Pattern.compile(
            "(?:\\b[1-3][ \\t]?)?\\b[A-Za-zÀ-ÿ][A-Za-zÀ-ÿ'’]*"
                    + "(?:[ \\t][A-Za-zÀ-ÿ][A-Za-zÀ-ÿ'’]*)*"
                    + "[ \\t]?\\d+(?:[ \\t]?[.:,][ \\t]?\\d+)?"
                    + "(?:[ \\t]?-[ \\t]?\\d+(?:[ \\t]?[.:,][ \\t]?\\d+)?)?"
    );

    /**
     * Détecte TOUTES les références bibliques dans un texte libre.
     * Chaque match est validé par la table des livres (pas de faux positifs
     * comme "chiffre 3" ou "pahataperana.3" : si le livre n'est pas reconnu,
     * le match est ignoré). La référence renvoyée est déjà normalisée.
     */
    public static List<DetectedRef> detectReferences(String text) {
        List<DetectedRef> results = new ArrayList<>();
        if (text == null || text.isEmpty()) return results;

        Matcher m = REF_IN_TEXT.matcher(text);
        while (m.find()) {
            String candidate = m.group().trim();
            ParsedRef parsed = parse(candidate);
            if (parsed == null) continue;
            results.add(new DetectedRef(m.start(), m.end(), format(parsed)));
        }
        return results;
    }

    /** Acronyme officiel affiché ("Jao", "1Kor"...) pour un numéro. */
    public static String shortNameOf(int bookNumber) {
        for (String[] book : BOOKS) {
            if (Integer.parseInt(book[0]) == bookNumber) return book[1];
        }
        return String.valueOf(bookNumber);
    }

    /** Numéro du livre d'après son acronyme officiel (le même qu'on affiche). */
    public static Integer numberFromShortName(String shortName) {
        return resolveBookNumber(shortName);
    }
}
