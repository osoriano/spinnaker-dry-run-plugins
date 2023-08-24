#!/bin/bash
set -o errexit
set -o nounset
set -o pipefail


cd "$(dirname "$0")"
source ./common.sh


usage() {
  echo "Usage: $0 <path-to-delivery-config-yml>"
  exit 1
}


if [[ "$#" != 1 ]]; then
  usage
fi


DELIVERY_CONFIG_FILE="$1"
PROCESSED_DELIVERY_CONFIG="$(mktemp /tmp/keel-processed-delivery-config.XXXXXXXX)"
trap 'rm -f -- "${PROCESSED_DELIVERY_CONFIG}"' EXIT

cat "${DELIVERY_CONFIG_FILE}" \
  | python ./pre-process-delivery-config.py \
  > "${PROCESSED_DELIVERY_CONFIG}"


create_delivery_config "${PROCESSED_DELIVERY_CONFIG}"
