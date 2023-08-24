#!/bin/bash
#
# A script for hosting Spinnaker plugins locally.
#
# Assumes the plugins are already compiled and packaged.
#
set -o errexit
set -o nounset
set -o pipefail

ARTIFACT_VERSION="1.0.0"
PLUGIN_NAME=spinnaker-dry-run-plugins
PORT=8000

cd "$(dirname $0)"
cd ..


#
# Switch to artifact dir
#
# Has contents:
# - Plugin Manifest (plugin-info.json)
# - Plugin Bundle (spinnaker-dry-run-plugins-1.0.0.zip)
#
cd build/distributions

ensure_file_exists() {
  local filepath="$1"
  if [[ ! -f "${filepath}" ]]; then
    echo "Expected file to exist: ${filepath}"
    exit 1
  fi
}

ensure_file_exists "plugin-info.json"
ensure_file_exists "${PLUGIN_NAME}-${ARTIFACT_VERSION}.zip"


for local_setup_type in docker minikube; do
  # Create directory
  mkdir -p "${local_setup_type}"

  # Create plugins.json with local plugin url
  python3 -c "
import json

with open('plugin-info.json') as f:
    plugin_info = json.load(f)

for release in plugin_info['releases']:
    release['url'] = 'http://host.${local_setup_type}.internal:${PORT}/${PLUGIN_NAME}-${ARTIFACT_VERSION}.zip'

with open('${local_setup_type}/plugins.json', 'w') as f:
    plugins = [ plugin_info ]
    json.dump(plugins, f)
"

done
python -m http.server 8000
