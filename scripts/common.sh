#!/bin/bash
set -o errexit
set -o nounset
set -o pipefail

source configuration.sh

LOCAL_API_URL="http://localhost:8084"
K8S_API_URL="https://osoriano.com/implementme"

get_api_url() {
  if [[ "${SPINNAKER_ENV}" == k8s ]]; then
    echo "${K8s_API_URL}"
  else
    echo "${LOCAL_API_URL}"
  fi
}


API_URL="$(get_api_url)"


CURL="curl \
  --silent \
  --show-error \
  --location \
  --fail \
  --connect-timeout 900 \
  --max-time 900"

req() {
  if [[ "${SPINNAKER_ENV}" == k8s ]]; then
    ${CURL} \
      --header "X-SPINNAKER-USER: testuser" \
      --header "Host: implementme" \
      "$@" \
      | jq
  else
    ${CURL} \
      --header "X-SPINNAKER-USER: testuser" \
      "$@" \
      | jq
  fi
}

create_application() {
  local application_json_file="$1"

  echo "Creating application"
  local create_application_resp="$(
    req \
      -v \
      --request POST \
      --header "Accept: application/json" \
      --header "Content-Type: application/json" \
      --data "@${application_json_file}" \
      "${API_URL}/tasks"
  )"


  local task="$(echo "${create_application_resp}" | jq -r .ref)"
  echo "Waiting for task: ${task}"

  while true; do
    echo "Fetching task status"

    local task_resp="$(
      req \
        -v \
        --header "Accept: application/json" \
        "${API_URL}${task}"
    )"

    local task_status="$(echo "${task_resp}" | jq -r .status)"

    if [[ "${task_status}" == SUCCEEDED ]]; then
      break
    elif [[ "${task_status}" == RUNNING ]] || [[ "${task_status}" == NOT_STARTED ]]; then
      echo "Got task status: ${task_status}"
    else
      echo "Unknown task status: ${task_status}"
      exit 1
    fi

    sleep 2

  done

  echo "Task succeeded"


  local application_name="$(echo "${task_resp}" | jq -r .application)"
  echo "Fetching application: ${application_name}"
  req \
    -v \
    --header "Accept: application/json" \
   "${API_URL}/applications/${application_name}?expand=false"
}

create_delivery_config() {
  local delivery_config_file="$1"
  req \
    -v \
    --request POST \
    --header "Accept: application/json" \
    --header "Content-Type: application/x-yaml" \
    --data-binary "@${delivery_config_file}" \
    "${API_URL}/managed/delivery-configs"
}
