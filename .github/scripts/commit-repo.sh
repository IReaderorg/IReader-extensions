#!/bin/bash
set -e

# Sync built APKs from master/repo/ to repov2 branch
# Note: The workflow checks out repov2 branch into 'repo' folder
# and master branch into 'master' folder
rsync -a --delete --exclude .git --exclude .gitignore ../master/repo/ .
git config --global user.email "github-actions[bot]@users.noreply.github.com"
git config --global user.name "github-actions[bot]"
git status
if [ -n "$(git status --porcelain)" ]; then
    git add .
    git commit -m "Update extensions repo"
    git push
else
    echo "No changes to commit"
fi
