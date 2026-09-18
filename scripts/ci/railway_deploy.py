#!/usr/bin/env python3
"""Deploys the backend image CI built and scanned to Railway (Documents 5 and 7: the image deployed is the digest
stage 6 produced). Railway runs that exact image; it never builds its own.

  railway_deploy.py check                      validate the project token and show what the service runs now
  railway_deploy.py deploy <image> <digest>    point the service at <image>@<digest>, apply the deploy settings of
                                               backend/railway.json, deploy, wait for SUCCESS, check the health URL

Environment: RAILWAY_TOKEN (a Railway project token for the production environment), RAILWAY_SERVICE_ID, HEALTH_URL.
Standard library only, so the CI job needs no install step.
"""
import json
import os
import pathlib
import sys
import time
import urllib.error
import urllib.request

API = "https://backboard.railway.com/graphql/v2"
SETTINGS_FILE = pathlib.Path(__file__).resolve().parents[2] / "backend" / "railway.json"
APPLIED_SETTINGS = ("numReplicas", "sleepApplication", "healthcheckPath", "healthcheckTimeout",
                    "restartPolicyType", "restartPolicyMaxRetries")
SUCCESS, BROKEN = "SUCCESS", {"FAILED", "CRASHED", "REMOVED", "SKIPPED"}
DEPLOY_TIMEOUT_SECONDS = 900
POLL_SECONDS = 10


def env(name):
    value = os.environ.get(name, "").strip()
    if not value:
        sys.exit(f"{name} is not set")
    return value


def gql(query, variables=None):
    request = urllib.request.Request(
        API,
        data=json.dumps({"query": query, "variables": variables or {}}).encode(),
        headers={"Content-Type": "application/json", "Project-Access-Token": env("RAILWAY_TOKEN")},
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            payload = json.load(response)
    except urllib.error.HTTPError as error:
        sys.exit(f"Railway API answered HTTP {error.code}: {error.read(300)!r}")
    if payload.get("errors"):
        sys.exit("Railway API error: " + "; ".join(e.get("message", "?") for e in payload["errors"]))
    return payload["data"]


def token_scope():
    return gql("query { projectToken { projectId environmentId project { name } environment { name } } }")["projectToken"]


def service_instance(environment_id):
    return gql(
        """query($sid: String!, $eid: String!) {
             serviceInstance(serviceId: $sid, environmentId: $eid) {
               serviceName source { image repo } latestDeployment { id status } } }""",
        {"sid": env("RAILWAY_SERVICE_ID"), "eid": environment_id},
    )["serviceInstance"]


def summary(lines):
    text = "\n".join(lines) + "\n"
    print(text)
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        with open(path, "a", encoding="utf-8") as handle:
            handle.write(text)


def check():
    scope = token_scope()
    instance = service_instance(scope["environmentId"])
    latest = instance.get("latestDeployment") or {}
    summary([
        "### Railway target",
        "",
        f"- token: project `{scope['project']['name']}`, environment `{scope['environment']['name']}`",
        f"- service `{instance['serviceName']}` runs `{instance['source'].get('image') or instance['source'].get('repo')}`",
        f"- latest deployment: `{latest.get('id')}` {latest.get('status')}",
    ])
    return scope


def wait_for(deployment_id):
    deadline = time.monotonic() + DEPLOY_TIMEOUT_SECONDS
    last = None
    while time.monotonic() < deadline:
        deployment = gql("query($id: String!) { deployment(id: $id) { status meta } }", {"id": deployment_id})["deployment"]
        if deployment["status"] != last:
            last = deployment["status"]
            print(f"deployment {deployment_id}: {last}", flush=True)
        if last == SUCCESS or last in BROKEN:
            return deployment
        time.sleep(POLL_SECONDS)
    sys.exit(f"deployment {deployment_id} did not finish within {DEPLOY_TIMEOUT_SECONDS} s (last status {last})")


def healthy(url, attempts=10):
    for attempt in range(1, attempts + 1):
        try:
            with urllib.request.urlopen(url, timeout=15) as response:
                body = response.read().decode()
                if response.status == 200 and '"status":"UP"' in body:
                    return body
        except urllib.error.URLError as error:
            print(f"health attempt {attempt}: {error}", flush=True)
        time.sleep(6)
    return None


def deploy(image, digest):
    if not digest.startswith("sha256:"):
        sys.exit(f"refusing to deploy without an image digest (got {digest!r}); stage 6 must have pushed the image")
    reference = f"{image}@{digest}"
    scope = check()
    settings = json.loads(SETTINGS_FILE.read_text(encoding="utf-8"))["deploy"]
    update = {key: settings[key] for key in APPLIED_SETTINGS if key in settings}
    update["source"] = {"image": reference}
    variables = {"sid": env("RAILWAY_SERVICE_ID"), "eid": scope["environmentId"]}
    gql("""mutation($sid: String!, $eid: String, $input: ServiceInstanceUpdateInput!) {
             serviceInstanceUpdate(serviceId: $sid, environmentId: $eid, input: $input) }""",
        {**variables, "input": update})
    deployment_id = gql("""mutation($sid: String!, $eid: String!) {
                             serviceInstanceDeployV2(serviceId: $sid, environmentId: $eid) }""", variables)["serviceInstanceDeployV2"]
    deployment = wait_for(deployment_id)
    deployed = (deployment.get("meta") or {}).get("image")
    health = healthy(env("HEALTH_URL")) if deployment["status"] == SUCCESS else None
    summary([
        "### Railway deploy",
        "",
        f"- image: `{reference}`",
        f"- deployment: `{deployment_id}` {deployment['status']}",
        f"- Railway reports it runs: `{deployed}`",
        f"- health: {health.strip() if health else 'not UP'}",
    ])
    if deployment["status"] != SUCCESS:
        sys.exit(f"deployment {deployment_id} ended {deployment['status']}; Railway keeps the previous deployment running")
    if deployed != reference:
        sys.exit(f"Railway runs {deployed!r}, expected {reference!r}")
    if not health:
        sys.exit(f"{env('HEALTH_URL')} did not answer UP after the deploy")


def main(argv):
    if argv[1:2] == ["check"]:
        check()
    elif argv[1:2] == ["deploy"] and len(argv) == 4:
        deploy(argv[2], argv[3])
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main(sys.argv)
