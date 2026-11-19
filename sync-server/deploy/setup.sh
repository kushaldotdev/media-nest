#!/bin/bash
set -e

# Install uv
curl -LsSf https://astral.sh/uv/install.sh | sh
source "$HOME/.cargo/env"

# Create user
useradd -r -s /bin/false medianest || true

# Create dirs
mkdir -p /opt/medianest-sync/data
chown -R medianest:medianest /opt/medianest-sync

# Copy app files (assuming current dir is sync-server/)
cp -r app /opt/medianest-sync/
cp pyproject.toml /opt/medianest-sync/
cp uv.lock /opt/medianest-sync/

# Install deps with uv
cd /opt/medianest-sync
uv sync --frozen --no-dev

# Service
cp deploy/systemd/medianest-sync.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now medianest-sync

echo "Server running on port 8000. Data at /opt/medianest-sync/data/"
