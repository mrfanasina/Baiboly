import sqlite3
import sys

def afficher_structure(db_path):
    try:
        conn = sqlite3.connect(db_path)
        cursor = conn.cursor()

        # Récupérer toutes les tables
        cursor.execute("""
            SELECT name
            FROM sqlite_master
            WHERE type = 'table'
            AND name NOT LIKE 'sqlite_%'
            ORDER BY name;
        """)

        tables = cursor.fetchall()

        if not tables:
            print("Aucune table trouvée.")
            return

        print(f"=== Structure de : {db_path} ===\n")

        for (table,) in tables:
            print(f"TABLE : {table}")
            print("-" * 50)

            cursor.execute(f'PRAGMA table_info("{table}")')
            columns = cursor.fetchall()

            for column in columns:
                # cid, name, type, notnull, default_value, pk
                cid, name, col_type, notnull, default, pk = column

                contraintes = []

                if pk:
                    contraintes.append("PRIMARY KEY")
                if notnull:
                    contraintes.append("NOT NULL")
                if default is not None:
                    contraintes.append(f"DEFAULT {default}")

                contrainte = " ".join(contraintes)

                print(
                    f"  {name:<20} "
                    f"{col_type:<15} "
                    f"{contrainte}"
                )

            # Clés étrangères
            cursor.execute(f'PRAGMA foreign_key_list("{table}")')
            foreign_keys = cursor.fetchall()

            if foreign_keys:
                print("\n  Clés étrangères :")
                for fk in foreign_keys:
                    # id, seq, table, from, to, on_update, on_delete, match
                    _, _, ref_table, from_col, to_col, on_update, on_delete, _ = fk

                    print(
                        f"    {from_col} -> {ref_table}.{to_col}"
                        f" (ON UPDATE {on_update}, ON DELETE {on_delete})"
                    )

            print()

        conn.close()

    except sqlite3.Error as e:
        print(f"Erreur SQLite : {e}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Utilisation : python structure.py ma_base.db")
        sys.exit(1)

    afficher_structure(sys.argv[1])