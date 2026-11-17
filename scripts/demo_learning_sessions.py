#!/usr/bin/env python3
"""Exercise #18 against the isolated local Spring profile, using only the standard library."""

import argparse
import base64
from datetime import datetime, timedelta, timezone
import http.cookiejar
import json
import os
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import HTTPCookieProcessor, Request, build_opener


class DemoUser:
    def __init__(self, base, user_id, password):
        self.base = base
        self.user_id = user_id
        self.authorization = "Basic " + base64.b64encode(f"{user_id}:{password}".encode()).decode()
        self.client = build_opener(HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf = None

    def call(self, path, method="GET", body=None):
        headers = {"Authorization": self.authorization, "Accept": "application/json"}
        if body is not None:
            if self.csrf is None:
                self.csrf = self.call("/api/match-session")
            headers[self.csrf["csrfHeader"]] = self.csrf["csrfToken"]
            headers["Content-Type"] = "application/json"
        data = None if body is None else json.dumps(body).encode()
        with self.client.open(Request(self.base + path, data=data, headers=headers, method=method), timeout=15) as response:
            return json.load(response)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    args = parser.parse_args()
    base = args.base_url.rstrip("/")
    url = urlparse(base)
    if (url.scheme != "http" or url.hostname not in {"127.0.0.1", "localhost", "::1"}
            or url.path or url.query or url.fragment or url.username or url.password):
        parser.error("Use the root URL of an isolated local-profile server on loopback.")
    password = os.environ.get("SPARKZ_LOCAL_PASSWORD", "local-demo")
    teacher = DemoUser(base, "00000000-0000-0000-0000-000000000001", password)
    learner = DemoUser(base, "00000000-0000-0000-0000-000000000002", password)
    candidates = teacher.call("/api/match-requests?direction=incoming") + teacher.call("/api/match-requests?direction=outgoing")
    match = next((m for m in candidates if {m["senderId"], m["receiverId"]} == {teacher.user_id, learner.user_id}
                  and m["skillId"] == 1 and m["status"] in {"Pending", "Accepted"}), None)
    if match is None:
        match = teacher.call("/api/match-requests", "POST", {"receiverId": learner.user_id, "skillId": 1})
    if match["status"] == "Pending":
        recipient = teacher if match["receiverId"] == teacher.user_id else learner
        match = recipient.call(f'/api/match-requests/{match["id"]}', "PATCH", {"status": "Accepted"})
    assert match["status"] == "Accepted"
    start = (datetime.now(timezone.utc) + timedelta(days=2)).replace(microsecond=0)
    proposal = {"matchRequestId": match["id"], "teacherId": teacher.user_id,
                "scheduledStart": start.isoformat(), "scheduledEnd": (start + timedelta(hours=1)).isoformat(),
                "mode": "online", "meetingUrl": "https://meet.example.test/local-demo", "message": "Local API demonstration"}
    online = teacher.call("/api/learning-sessions", "POST", proposal)
    accepted = learner.call(f'/api/learning-sessions/{online["id"]}', "PATCH", {"status": "accepted"})
    assert accepted["status"] == "accepted"
    canceled = teacher.call(f'/api/learning-sessions/{online["id"]}', "PATCH", {"status": "canceled"})
    assert canceled["status"] == "canceled"
    proposal.update(mode="in-person", location="Library room 2")
    proposal.pop("meetingUrl")
    in_person = learner.call("/api/learning-sessions", "POST", proposal)
    declined = teacher.call(f'/api/learning-sessions/{in_person["id"]}', "PATCH", {"status": "declined"})
    assert declined["status"] == "declined"
    own = teacher.call(f'/api/learning-sessions?matchRequestId={match["id"]}')
    assert own["total"] >= 2
    print("PASS: accepted match; online proposal, accept, cancel; in-person proposal, decline; private session list.")
    print(json.dumps({"online": canceled, "inPerson": declined}, indent=2))


if __name__ == "__main__":
    try:
        main()
    except HTTPError as error:
        raise SystemExit(f"Local API returned {error.code}: {error.read().decode()}") from None
    except URLError as error:
        raise SystemExit(f"Cannot reach the local API; start the local-profile server first: {error.reason}") from None
