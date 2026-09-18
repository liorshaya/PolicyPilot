#!/bin/sh
# Pulls the two local models the ollama profile needs (Document 2, Configuration and Model Providers):
# qwen3:14b for every prompt and bge-m3 for embeddings. Runs inside the ollama image as the compose service
# ollama-pull, against the ollama service; also usable on a machine with Ollama installed.
set -eu
: "${OLLAMA_HOST:=http://ollama:11434}"
export OLLAMA_HOST

echo "waiting for Ollama at $OLLAMA_HOST"
attempt=0
until ollama list >/dev/null 2>&1; do
  attempt=$((attempt + 1))
  if [ "$attempt" -gt 60 ]; then
    echo "Ollama did not answer within two minutes"
    exit 1
  fi
  sleep 2
done

for model in qwen3:14b bge-m3; do
  echo "pulling $model"
  ollama pull "$model"
done
echo "models ready:"
ollama list
