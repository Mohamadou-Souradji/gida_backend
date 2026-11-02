#!/usr/bin/env bash
# Gida Jari - restauration d'une sauvegarde de la base.
# Usage : ./restaurer.sh /opt/gida-jari/sauvegardes/base_2026-10-16_0200.sql.gz
set -euo pipefail
cd "$(dirname "$0")"
FICHIER=${1:?Indique le fichier base_xxx.sql.gz a restaurer}
read -r -p "La base actuelle va etre ECRASEE par $FICHIER. Continuer ? (oui/non) " OK
[ "$OK" = "oui" ] || { echo "Annule."; exit 1; }
docker compose stop backend
gunzip -c "$FICHIER" | docker compose exec -T db psql -U gida_jari -d gida_jari
docker compose start backend
echo "Restauration terminee."
