#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# ── Read version info from package.json ──────────────────────────────
VERSION=$(node -p "require('./package.json').version")
VERSION_CODE=$(node -p "require('./package.json').versionCode")
TAG="v${VERSION}.${VERSION_CODE}"
REPO_URL=$(node -p "require('./package.json').repository.url")

# ── Collect output files ─────────────────────────────────────────────
OUTPUT_DIR="app/build/outputs"
ASSETS=()
while IFS= read -r -d '' file; do
  ASSETS+=("$file")
done < <(find "${OUTPUT_DIR}" -type f \( -name "*.apk" -o -name "*.aab" \) -print0)

if [[ ${#ASSETS[@]} -eq 0 ]]; then
  echo "WARNING: No .apk or .aab files found in ${OUTPUT_DIR}"
  exit 1
fi

echo "==> Publishing ${TAG}"

# ── Ensure working tree is clean ─────────────────────────────────────
if [[ -n "$(git status --porcelain)" ]]; then
  echo "ERROR: Working tree is dirty. Commit or stash changes first."
  exit 1
fi

# ── Create release branch & tag ──────────────────────────────────────
BRANCH="release/${TAG}"
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)

if git rev-parse "${TAG}" >/dev/null 2>&1; then
  echo "WARNING: Tag ${TAG} already exists."
  read -rp "Force overwrite? (y/N): " FORCE
  if [[ "${FORCE}" =~ ^[Yy]$ ]]; then
    echo "==> Deleting existing tag ${TAG} (local + remote)"
    git tag -d "${TAG}" || true
    git push origin ":refs/tags/${TAG}" || true
    if gh release view "${TAG}" &>/dev/null 2>&1; then
      gh release delete "${TAG}" --yes || true
    fi
  else
    echo "Aborted."
    exit 1
  fi
fi

echo "==> Creating branch ${BRANCH}"
git checkout -b "${BRANCH}"

echo "==> Tagging ${TAG}"
git tag -a "${TAG}" -m "Release ${TAG}"

echo "==> Pushing branch and tag to origin"
git push origin "${BRANCH}"
git push origin "${TAG}"

# ── Create / update GitHub release ───────────────────────────────────
if command -v gh &>/dev/null; then
  echo "==> Creating GitHub release ${TAG}"

  UPLOAD_ARGS=()
  for asset in "${ASSETS[@]}"; do
    UPLOAD_ARGS+=("${asset}")
  done

  gh release create "${TAG}" \
    --title "${TAG}" \
    --notes "Release ${TAG}" \
    --latest \
    "${UPLOAD_ARGS[@]}"

  echo "==> Release ${TAG} published with ${#ASSETS[@]} asset(s)"
else
  echo "WARNING: GitHub CLI (gh) not found. Skipping release upload."
  echo "Install it from https://cli.github.com/ then run:"
  echo "  gh release create ${TAG} --title \"${TAG}\" ${ASSETS[*]}"
fi

# ── Return to original branch ────────────────────────────────────────
echo "==> Switching back to ${CURRENT_BRANCH}"
git checkout "${CURRENT_BRANCH}"

echo "==> Done! Published ${TAG}"
