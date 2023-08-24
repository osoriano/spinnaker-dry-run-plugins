#!/bin/bash
set -o errexit
set -o nounset
set -o pipefail


cd "$(dirname "$0")"
source ./common.sh


usage() {
  echo "Usage: $0 <path-to-application-json>"
  exit 1
}


if [[ "$#" != 1 ]]; then
  usage
fi


APPLICATION_JSON_FILE="$1"

create_application "${APPLICATION_JSON_FILE}"
