    package com.fa.baiboly.models;

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

        @Override
        public String toString() {

            if (book == null) return "";
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
    }