#!/bin/bash
set -o errexit
set -o nounset
set -o pipefail

# Should be one of local, k8s
SPINNAKER_ENV=local
