#!/usr/bin/env bash
# Gida Jari - sauvegarde de la base PostgreSQL et des fichiers televerses.
# Garde les 14 dernieres sauvegardes dans /opt/gida-jari/sauvegardes.
# Cron conseille (tous les jours a 2 h) :
#   0 2 * * * /opt/gida-jari/gida-jari-backend/deploy/sauvegarder.sh >> /opt/gida-jari/sauvegardes/cron.log 2>&1
set -euo pipefail
cd "$(dirname "$0")"
DEST=/opt/gida-jari/sauvegardes
DATE=$(date +%Y-%m-%d_%H%M)
mkdir -p "$DEST"

docker compose exec -T db pg_dump -U gida_jari -d gida_jari --clean --if-exists | gzip > "$DEST/base_$DATE.sql.gz"
docker compose exec -T backend tar czf - -C /app uploads > "$DEST/fichiers_$DATE.tar.gz"

# Rotation : on ne garde que les 14 plus recentes de chaque type
ls -1t "$DEST"/base_*.sql.gz | tail -n +15 | xargs -r rm -f
ls -1t "$DEST"/fichiers_*.tar.gz | tail -n +15 | xargs -r rm -f
echo "Sauvegarde OK : $DEST/base_$DATE.sql.gz"
