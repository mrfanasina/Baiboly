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
        {"52", "1Tes", "1 Tesalonianina", "1Tesalonianina", "1Tesaloniana", "1 Tesaloniana", "Tesaloniana", "1The", "1Th", "1 Thess"},
        {"53", "2Tes", "2 Tesalonianina", "2Tesalonianina", "2Tesaloniana", "2 Tesaloniana", "2The", "2Th", "2 Thess"},
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

        /**
         * Forme "Lio 2:39,42,44" : versets d'un même chapitre listés
         * discrètement, dans le bruit de la notation "and 39b,42" (le
         * 'b' indique une 2e partie du verset, ignorée pour la lecture
         * mais conservée purement pour l'affichage).
         */
        public List<int[]> discreteVerses;

        /** Lettres d'affichage associées à discreteVerses ("b", "a"... ou null). */
        public List<String> discreteLetters;

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

        // Forme "Lio 2:39b,42,44" (liste de versets discrets) : testée sur
        // la chaîne brute, car la normalisation ',' -> '.' qui suit
        // détruirait la distinction "liste" / "chapitre.verset"
        // ("Heb11,1" = ch. 11, v. 1 doit rester prioritaire).
        ParsedRef discrete = tryParseDiscreteList(s);
        if (discrete != null) return discrete;

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
    /**
     * Livres qui n'ont QU'UN SEUL chapitre (donc pas de numéro de
     * chapitre utilisable) : Oba (Obadia), File (Filemona), 2Jao, 3Jao
     * et Jod. Pour eux, "Jod 17-23" ne peut pas être une plage de
     * chapitres : ce sont les versets du chapitre 1 (= "Jod 1:17-23").
     */
    private static boolean isSingleChapterBook(int bookNumber) {
        return bookNumber == 31 // Oba — Obadia
                || bookNumber == 57 // File — Filemona
                || bookNumber == 63 // 2Jao
                || bookNumber == 64 // 3Jao
                || bookNumber == 65; // Jod
    }

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

        // Livres à UN SEUL chapitre ("File", "Jod"...) : sans séparateur
        // ":", les nombres qui suivent le livre sont des VERSETS du
        // chapitre 1. "Jod 17-23" = "Jod 1:17-23" ; "File 4" = "File 1:4".
        // La forme explicite "Jod 1:17-23" (avec ':') reste inchangée, et
        // "Jod 1" tout seul garde le sens "le livre entier" (chap. 1).
        if (isSingleChapterBook(bookNumber) && m.group(2) == null) {
            int v1 = ref.startChapter;
            int v2 = ref.endChapter;
            if (v1 > 1 || v2 != v1) {
                ref.startChapter = 1;
                ref.endChapter = 1;
                ref.startVerse = v1;
                ref.endVerse = v2;
            }
        }
        return ref;
    }    // =====================================================
    // MARQUEUR "and" / "andininy" (lecture du jour)
    // =====================================================

    // Marqueur "and" / "andininy" — numéro OBLIGATOIRE. Captures étendues :
    //   - suffixe de partie : "and 39b", "and 39a" (lettre d'article,
    //     purement décorative — le verset COMPLET est affiché)
    //   - listes séparées par des virgules : "and 39b,42,44" (espaces
    //     autour des virgules tolérés)
    //   - plages : "and 12-13", "and:12"
    // NB : pas de \b APRÈS "and" — pas de frontière de mot entre "d" et un
    // chiffre ("and12" ne matcherait jamais sinon). [ \t]* et non \s* :
    // jamais de saut de ligne, pour ne pas "traverser" un paragraphe.
    // Groupe 1 = suffixe de versets ("39b,42,44").
    // Pattern UNIQUE partagé par ChatAdapter et MofonainaActivity.
    public static final Pattern AND_MARKER_PATTERN = Pattern.compile(
            "\\b(?:andininy|and)[ \\t]*[.:]?[ \\t]*"
                    + "(\\d+(?:[ \\t]?-[ \\t]?\\d+)?[a-zA-Z]?"
                    + "(?:[ \\t]*,[ \\t]*\\d+(?:[ \\t]?-[ \\t]?\\d+)?[a-zA-Z]?)*)"
    );

    // =====================================================
    // LISTE DE VERSETS DISCRETS : "Lio 2:39b,42,44"
    // =====================================================

    /** Un item de liste : numéro + lettre optionnelle (+ plage via rangeEnd). */
    private static final Pattern DISCRETE_ITEM =
            Pattern.compile("(\\d+)([a-zA-Z])?"
                    + "(?:\\s*-\\s*(\\d+)(?:[a-zA-Z])?)?");

    /**
     * Détecte la forme "Lio 2:39b,42,44" sur la chaîne BRUTE (les
     * virgules ne sont PAS encore normalisées).
     */
    public static ParsedRef tryParseDiscreteList(String s) {

        int colon = s.indexOf(':');
        if (colon < 0) return null;

        Matcher chap = CHAPTER_BEFORE_COLON.matcher(s.substring(0, colon).trim());
        if (!chap.matches()) return null;

        String listPart = s.substring(colon + 1).trim();
        if (!isListBody(listPart)) return null;

        Integer bookNumber = resolveBookText(null, chap.group(1));
        if (bookNumber == null) return null;

        ParsedRef ref = new ParsedRef();
        ref.bookNumber = bookNumber;
        ref.bookName = chap.group(1).trim();
        ref.hasNumbers = true;
        ref.discreteVerses = new ArrayList<>();
        ref.discreteLetters = new ArrayList<>();

        for (String raw : listPart.split(",")) {
            Matcher item = DISCRETE_ITEM.matcher(raw.trim());
            if (!item.matches()) return null;

            int n1 = Integer.parseInt(item.group(1));
            String letter = item.group(2);
            int n2 = (item.group(3) != null)
                    ? Integer.parseInt(item.group(3)) : n1;
            if (n2 < n1) { int t = n1; n1 = n2; n2 = t; }

            for (int n = n1; n <= n2; n++) {
                // La lettre d'article n'habille que le premier verset de
                // l'item (les versets expansés d'une plage restent nus).
                ref.discreteVerses.add(new int[]{n});
                ref.discreteLetters.add(n == n1 ? letter : null);
            }
        }

        if (ref.discreteVerses.isEmpty()) return null;

        ref.startChapter = Integer.parseInt(chap.group(2));
        ref.endChapter = ref.startChapter;
        // Bornes dérivées de la liste (repli affichage classique).
        ref.startVerse = ref.discreteVerses.get(0)[0];
        ref.endVerse = ref.discreteVerses.get(ref.discreteVerses.size() - 1)[0];
        return ref;
    }

    /** Chapter + ':' + liste (items n, nb, n-nb...) matché en entier. */
    private static boolean isListBody(String listPart) {
        if (listPart == null || listPart.isEmpty()) return false;

        boolean hasComma = listPart.indexOf(',') >= 0;

        // Un item seul n'est accepté que si une lettre d'article est
        // présente ("39b") : les formes classiques "3:16-17" (plage) et
        // "3:16" (verset simple) gardent sinon préséance via TAIL_PATTERN.
        if (!hasComma && !listPart.matches(".*\\d[a-zA-Z].*")) return false;

        for (String item : listPart.split(",")) {
            if (!DISCRETE_ITEM.matcher(item.trim()).matches()) return false;
        }
        return true;
    }

    /** Livre (éventuel préfixe 1-3) + chapitre entier juste avant le ':'. */
    private static final Pattern CHAPTER_BEFORE_COLON =
            Pattern.compile("^(.*?)(\\d+)\\s*$");

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
        // Forme liste explicite : remplace la fin standard
        // ("Lio 2:39b,42,44" — lettres conservées pour l'affichage).
        if (ref.discreteVerses != null && !ref.discreteVerses.isEmpty()) {
            sb = new StringBuilder(shortNameOf(ref.bookNumber))
                    .append(' ')
                    .append(ref.startChapter)
                    .append(':');
            for (int i = 0; i < ref.discreteVerses.size(); i++) {
                int[] pair = ref.discreteVerses.get(i);
                String letter = ref.discreteLetters.get(i);
                if (i > 0) sb.append(',');
                sb.append(pair[0]);
                if (letter != null) sb.append(letter);
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

    /**
     * Un mot qui peut appartenir à un nom de livre. Les marqueurs de verset
     * (« and », « andininy ») sont exclus : sans cela « Obadia and.20 » serait
     * lu comme le chapitre 20 d'Obadia.
     */
    private static final String BOOK_WORD =
            "(?i:(?!and\\b|andininy\\b)[\\p{L}][\\p{L}'’]*)";

    // Livre : un à cinq mots (accents/apostrophes), éventuellement précédés
    // d'un préfixe 1-3, puis espace OU rien, puis les nombres.
    // Espaces/tabulations uniquement entre les mots : jamais de saut de ligne,
    // pour ne pas "traverser" un paragraphe ("pahataperana.\n3." ne matche pas).
    // Séparateur chapitre:verset : ":", "." OU "," (forme malgache usuelle).
    // Ex matchés : "Jao 3", "Heb11,1", "1 Kor 13:4-7", "1Kor13,4-7",
    //              "Jao 3.16", "Jao 3,16", "Asan'ny Apostoly 7:54-8:1", "Jod 1:2"
    private static final Pattern REF_IN_TEXT = Pattern.compile(
            "(?:\\b[1-3][ \\t]?|\\b)" + BOOK_WORD
                    + "(?:[ \\t]" + BOOK_WORD + "){0,4}"
                    + "[ \\t]?\\d+(?:[ \\t]?[.:,][ \\t]?\\d+)?"
                    + "(?:[ \\t]?-[ \\t]?\\d+(?:[ \\t]?[.:,][ \\t]?\\d+)?)?"
    );

    /** Liste de versets d'un marqueur : "8", "3-7", "39b,42,44", "1 - 23". */
    private static final String VERSE_LIST =
            "\\d+(?:[ \\t]*-[ \\t]*\\d+)?[a-zA-Z]?"
                    + "(?:[ \\t]*,[ \\t]*\\d+(?:[ \\t]*-[ \\t]*\\d+)?[a-zA-Z]?)*";

    /** « v-10 », « v. 10 », « v:10 » : verset seul, toujours séparé. */
    private static final Pattern V_MARKER_PATTERN = Pattern.compile(
            "(?i)\\bv\\s*[.:\\-]\\s*(" + VERSE_LIST + ")");

    /** Référence complète déjà recalée sur son nom de livre. */
    private static final class FoundRef {
        final int start;
        final int end;
        final int bookNumber;
        final int chapter;
        final String reference;

        FoundRef(int start, int end, int bookNumber, int chapter, String reference) {
            this.start = start;
            this.end = end;
            this.bookNumber = bookNumber;
            this.chapter = chapter;
            this.reference = reference;
        }
    }

    /** Marqueur de verset (« and.18 », « andininy 16 », « v-10 »). */
    private static final class Marker {
        final int start;
        final int end;
        final int bookNumber; // 0 = pas de livre devant le marqueur
        final String verses;

        Marker(int start, int end, int bookNumber, String verses) {
            this.start = start;
            this.end = end;
            this.bookNumber = bookNumber;
            this.verses = verses;
        }
    }

    /**
     * Détecte TOUTES les références bibliques dans un texte libre.
     * Chaque match est validé par la table des livres (pas de faux positifs
     * comme "chiffre 3" ou "pahataperana.3" : si le livre n'est pas reconnu,
     * le match est ignoré). La référence renvoyée est déjà normalisée.
     */
    public static List<DetectedRef> detectReferences(String text) {
        return detectReferences(text, null, 0);
    }

    /**
     * Variante avec contexte de lecture : quand le texte contient seulement
     * « and 8 », « andininy 16 » ou « v-10 », le livre et le chapitre du jour
     * servent à construire la référence. Sans contexte, ces marqueurs ne sont
     * résolus que s'ils suivent une référence complète dans le même texte.
     */
    public static List<DetectedRef> detectReferences(String text,
                                                     String contextBook,
                                                     int contextChapter) {
        List<DetectedRef> results = new ArrayList<>();
        if (text == null || text.isEmpty()) return results;

        // 1) Références complètes, recalées sur le nom du livre pour ne pas
        //    souligner « Tao amin'ny I Mpanjaka 4 : 3 » en entier.
        List<FoundRef> full = new ArrayList<>();
        Matcher m = REF_IN_TEXT.matcher(text);
        int from = 0;
        while (from < text.length()) {
            m.region(from, text.length());
            if (!m.find()) {
                break;
            }

            FoundRef ref = trimToBook(text, m.start(), m.end());
            if (ref == null) {
                // Un mot parasite a peut-être mangé le début (« et 1 Jaona 2 ») :
                // on retente au mot suivant (jamais au milieu d'un mot, sinon
                // « re 3 » serait lu comme un acronyme d'Apocalypse).
                from = nextWordStart(text, m.start() + 1);
                continue;
            }

            full.add(ref);
            from = ref.end;
        }

        // 2) Marqueurs de versets (livre devant, ou contexte).
        List<Marker> markers = findVerseMarkers(text, full);

        // 3) Fusion dans l'ordre du texte : les marqueurs héritent du dernier
        //    livre/chapitre rencontré.
        Integer ctxBook = resolveBookNumber(contextBook);
        int ctxChapter = contextChapter;

        int fi = 0;
        int mi = 0;
        while (fi < full.size() || mi < markers.size()) {
            boolean takeFull = mi >= markers.size()
                    || (fi < full.size()
                    && full.get(fi).start <= markers.get(mi).start);

            if (takeFull) {
                FoundRef ref = full.get(fi++);
                results.add(new DetectedRef(ref.start, ref.end, ref.reference));
                ctxBook = ref.bookNumber;
                ctxChapter = ref.chapter;
                continue;
            }

            Marker marker = markers.get(mi++);

            int book = marker.bookNumber;
            if (book == 0) {
                if (ctxBook == null || ctxBook == 0 || ctxChapter <= 0) {
                    continue;
                }
                book = ctxBook;
            }

            int chapter;
            if (isSingleChapterBook(book)) {
                chapter = 1;
            } else if (ctxBook != null && ctxBook == book && ctxChapter > 0) {
                chapter = ctxChapter;
            } else {
                continue;
            }

            ParsedRef parsed = parse(shortNameOf(book) + " " + chapter
                    + ":" + marker.verses);
            if (parsed == null) {
                continue;
            }

            results.add(new DetectedRef(marker.start, marker.end, format(parsed)));
        }

        return results;
    }

    /**
     * Recale une référence candidate sur son nom de livre : « Tao amin'ny
     * I Mpanjaka 4 : 3 » devient « I Mpanjaka 4 : 3 ». On garde le suffixe
     * le plus court qui se parse encore (le nom du livre le plus proche des
     * nombres).
     */
    private static FoundRef trimToBook(String text, int start, int end) {
        String candidate = text.substring(start, end);
        ParsedRef whole = parse(candidate);
        FoundRef best = null;

        for (int i = 0; i < candidate.length(); i++) {
            if (i > 0 && candidate.charAt(i - 1) != ' '
                    && candidate.charAt(i - 1) != '\t') {
                continue;
            }

            ParsedRef parsed = parse(candidate.substring(i));
            if (parsed == null || !parsed.hasNumbers) {
                continue;
            }

            // Ne jamais perdre le préfixe du livre : « 3 Jao 1 » ne doit pas
            // devenir « Jao 1 » (livre différent).
            if (whole != null && parsed.bookNumber != whole.bookNumber) {
                continue;
            }

            best = new FoundRef(start + i, end, parsed.bookNumber,
                    parsed.startChapter, format(parsed));
        }
        return best;
    }

    /** Marqueurs « and » / « andininy » / « v- » d'un texte, hors références. */
    private static List<Marker> findVerseMarkers(String text,
                                                 List<FoundRef> full) {
        List<Marker> markers = new ArrayList<>();

        Matcher andMatcher = AND_MARKER_PATTERN.matcher(text);
        while (andMatcher.find()) {
            Marker marker = buildMarker(text, andMatcher.start(),
                    andMatcher.end(), andMatcher.group(1));
            if (marker != null
                    && !overlapsRef(full, marker.start, marker.end)) {
                markers.add(marker);
            }
        }

        Matcher vMatcher = V_MARKER_PATTERN.matcher(text);
        while (vMatcher.find()) {
            Marker marker = buildMarker(text, vMatcher.start(),
                    vMatcher.end(), vMatcher.group(1));
            if (marker != null
                    && !overlapsRef(full, marker.start, marker.end)) {
                markers.add(marker);
            }
        }

        markers.sort((a, b) -> Integer.compare(a.start, b.start));
        return markers;
    }

    private static Marker buildMarker(String text, int start, int end,
                                      String verses) {
        int[] bookStart = new int[]{-1};
        Integer book = resolveBookBefore(text, start, bookStart);

        if (book != null && bookStart[0] >= 0) {
            return new Marker(bookStart[0], end, book, verses);
        }
        return new Marker(start, end, 0, verses);
    }

    /**
     * Cherche un nom de livre collé juste avant un marqueur (« Obadia and.20 »,
     * « I Tantara and.6 »). Renvoie aussi le début de ce nom pour ne pas
     * souligner les mots qui le précèdent.
     */
    private static Integer resolveBookBefore(String text, int markerStart,
                                             int[] startOut) {
        int from = Math.max(0, markerStart - 48);
        String before = text.substring(from, markerStart);

        Matcher word = Pattern.compile("[\\p{L}][\\p{L}'’]*")
                .matcher(before);
        List<int[]> tokens = new ArrayList<>();
        while (word.find()) {
            tokens.add(new int[]{word.start(), word.end()});
        }

        if (tokens.isEmpty()) {
            return null;
        }

        // Le dernier mot doit toucher le marqueur (séparé seulement par des
        // espaces) : « ... ; (and.18) » n'a pas de livre juste devant.
        for (int j = tokens.get(tokens.size() - 1)[1]; j < before.length(); j++) {
            char ch = before.charAt(j);
            if (ch != ' ' && ch != '\t') {
                return null;
            }
        }

        int max = Math.min(4, tokens.size());
        for (int len = 1; len <= max; len++) {
            int first = tokens.size() - len;
            int ts = tokens.get(first)[0];
            int te = tokens.get(tokens.size() - 1)[1];

            // Seulement des mots consécutifs : un chiffre ou une ponctuation
            // entre eux interdit de prendre tout le passage pour un livre
            // (« Marka 14 : 36 ; ... Ny and.8 »).
            if (!onlySpaces(before, tokens, first)) {
                break;
            }

            String candidate = before.substring(ts, te);
            Integer book = resolveBookText(null, candidate);
            if (book != null) {
                startOut[0] = from + ts;
                return book;
            }
        }
        return null;
    }

    private static boolean onlySpaces(String text, List<int[]> tokens,
                                       int first) {
        for (int i = first + 1; i < tokens.size(); i++) {
            int gapStart = tokens.get(i - 1)[1];
            int gapEnd = tokens.get(i)[0];
            for (int j = gapStart; j < gapEnd; j++) {
                char ch = text.charAt(j);
                if (ch != ' ' && ch != '\t') {
                    return false;
                }
            }
        }
        return true;
    }

    /** Début du mot suivant : ignore les caractères d'un mot en cours. */
    private static int nextWordStart(String text, int index) {
        int i = index;
        while (i < text.length() && !Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private static boolean overlapsRef(List<FoundRef> refs, int start, int end) {
        for (FoundRef ref : refs) {
            if (start < ref.end && end > ref.start) {
                return true;
            }
        }
        return false;
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
