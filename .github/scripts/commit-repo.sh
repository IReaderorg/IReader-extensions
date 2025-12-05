#!/bin/bash
set -e

# Sync built APKs from master/repo/ to repov2 branch
# Note: The workflow checks out repov2 branch into 'repo' folder
# and master branch into 'master' folder
# Exclude js/ folder to preserve JS sources deployed by build_js.yml workflow
rsync -a --delete --exclude .git --exclude .gitignore --exclude js/ ../master/repo/ .

# Ensure .gitignore in repo branch doesn't exclude APKs
# Create/update .gitignore to allow all repo files
cat > .gitignore << 'EOF'
# Repo branch - allow all built files
# Only ignore OS/editor files
.DS_Store
Thumbs.db
*.swp
*~
EOF

git config --global user.email "github-actions[bot]@users.noreply.github.com"
git config --global user.name "github-actions[bot]"

# Force add all files including APKs (in case .gitignore was excluding them)
git add -f .

git status
if [ -n "$(git status --porcelain)" ]; then
    git commit -m "Update extensions repo"
    git push
else
    echo "No changes to commit"
fi
