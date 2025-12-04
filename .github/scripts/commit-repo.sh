#!/bin/bash
set -e

rsync -a --delete --exclude .git --exclude .gitignore ../master/repov2/ .
git config --global user.email "github-actions[bot]@users.noreply.github.com"
git config --global user.name "github-actions[bot]"
git status
if [ -n "$(git status --porcelain)" ]; then
    git add .
    git commit -m "Update extensions repov2"
    git push
else
    echo "No changes to commit"
fi
