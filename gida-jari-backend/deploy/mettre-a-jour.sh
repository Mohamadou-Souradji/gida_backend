#!/usr/bin/env bash
# Gida Jari - mise a jour de la preproduction apres envoi du nouveau code.
# Usage : ./mettre-a-jour.sh
set -euo pipefail
cd "$(dirname "$0")"

echo ">> Sauvegarde avant mise a jour"
./sauvegarder.sh

echo ">> Reconstruction et redemarrage des conteneurs"
docker compose up -d --build

echo ">> Nettoyage des anciennes images"
docker image prune -f

echo ">> Etat des services"
docker compose ps
echo ">> Derniers logs du backend (Ctrl+C pour quitter)"
docker compose logs -f --tail=50 backend
