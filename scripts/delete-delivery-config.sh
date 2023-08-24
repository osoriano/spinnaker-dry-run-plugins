#!/bin/bash
set -o errexit
set -o nounset
set -o pipefail


cd "$(dirname "$0")"
source ./common.sh


usage() {
  echo "Usage: $0 <application>"
  exit 1
}


if [[ "$#" != 1 ]]; then
  usage
fi


APPLICATION="$1"

req \
  -v \
  --request DELETE \
  --header "Accept: application/json" \
  "${API_URL}/managed/application/${APPLICATION}/config"
