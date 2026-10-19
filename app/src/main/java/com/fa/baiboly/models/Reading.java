    package com.fa.baiboly.models;

    import java.util.List;

    public class Reading {

        private Book book;

        // case simple (même chapitre ou multi-chapitres)
        private int startChapter;
        private int startVerse;

        private int endChapter;
        private int endVerse;

        public Reading(Book book,
                       int startChapter,
                       int startVerse,
                       int endChapter,
                       int endVerse) {

            this.book = book;
            this.startChapter = startChapter;
            this.startVerse = startVerse;
            this.endChapter = endChapter;
            this.endVerse = endVerse;
        }

        /**
         * Forme "Lio 2:39,42,44" : versets d'un même chapitre listés
         * discrètement (null sinon). La lettre d'affichage ("b" de 39b,
         * 2e partie du verset) est conservée mais n'a AUCUN effet sur le
         * contenu : le verset complet est toujours chargé/affiché.
         */
        private List<int[]> discreteVerses;

        /** Lettres d'affichage ("b", "a"...), index parallèle de discreteVerses. */
        private List<String> discreteLetters;

        /** Constructeur étendu : liste de versets discrets ("Lio 2:39b,42,44"). */
        public Reading(Book book,
                       int startChapter,
                       List<int[]> discreteVerses,
                       List<String> discreteLetters) {

            this.book = book;
            this.startChapter = startChapter;
            this.startVerse = discreteVerses.get(0)[0];
            this.endChapter = startChapter;
            this.endVerse = discreteVerses.get(discreteVerses.size() - 1)[0];

            this.discreteVerses = discreteVerses;
            this.discreteLetters = discreteLetters;
        }

        @Override
        public String toString() {

            if (book == null) return "";

            // Affichage liste discrète : "Lio 2:39b,42,44" (lettres conservées)
            if (discreteVerses != null && !discreteVerses.isEmpty()) {

                StringBuilder sb = new StringBuilder(book.getShortName())
                        .append(' ')
                        .append(startChapter)
                        .append(':');

                for (int i = 0; i < discreteVerses.size(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append(discreteVerses.get(i)[0]);
                    if (discreteLetters != null && discreteLetters.get(i) != null) {
                        sb.append(discreteLetters.get(i));
                    }
                }
                return sb.toString();
            }

            if (endVerse == 999 && endChapter == startChapter) {
                return book.getShortName() + " " + startChapter;
            } else if (endVerse == 999) {
                return book.getShortName() + " " + startChapter + "-" + endChapter;
            }
            if (startChapter == endChapter) {
                return book.getShortName() + " " + startChapter + ":" + startVerse +
                        "-" + endVerse;
            } else {
                return book.getShortName() + " " + startChapter + ":" + startVerse +
                        " - " + endChapter + ":" + endVerse;
            }
        }

        // getters
        public Book getBook() {
            return book;
        }

        public int getStartChapter() {
            return startChapter;
        }

        public int getStartVerse() {
            return startVerse;
        }

        public int getEndChapter() {
            return endChapter;
        }

        public int getEndVerse() {
            return endVerse;
        }

        /** Liste {verseNumber} des versets discrets, ou null si plaine simple. */
        public List<int[]> getDiscreteVerses() {
            return discreteVerses;
        }

        /** Lettres d'affichage des versets discrets, ou null. */
        public List<String> getDiscreteLetters() {
            return discreteLetters;
        }
    }