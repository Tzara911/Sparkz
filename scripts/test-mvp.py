#!/usr/bin/env python3
"""Run Issue #10 acceptance tests in a disposable source snapshot, without changing PR branches."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


SCENARIOS = {
    "regression": ([], None),
    "aligned": ([], "MatchJourneyTests,LocalConfigurationIT"),
    "pr24": ([24], "MatchJourneyTests,SharedClassesIT"),
    "pr25": ([25], "MatchJourneyTests,ProfileSkillsIT"),
    "combined": ([24, 25], "MatchJourneyTests,SharedClassesIT,ProfileSkillsIT,CrossFeatureIT"),
    "pr25-local": ([25], "LocalConfigurationIT"),
    "combined-local": ([24, 25], "LocalConfigurationIT"),
}
PINNED_REFS = {
    24: "4cd645877fc2fb9a6e8238d2d80eb1fb890304f7",
    25: "a68ab2a08c2f3583453bcf1c309289e7964d786f",
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("scenario", choices=SCENARIOS)
    parser.add_argument("--pr24-ref", default=PINNED_REFS[24])
    parser.add_argument("--pr25-ref", default=PINNED_REFS[25])
    parser.add_argument("--maven", help="Maven executable; defaults to the snapshot's bash mvnw")
    parser.add_argument("--maven-arg", action="append", default=[], help="Extra option, e.g. --maven-arg=-o")
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]

    def git(*arguments):
        return subprocess.check_output(["git", *arguments], cwd=repo)

    prs, selection = SCENARIOS[args.scenario]
    # Resolve every ref before creating a snapshot. The caller fetches deliberately; this script does not.
    refs = {pr: git("rev-parse", "--verify", f"{getattr(args, f'pr{pr}_ref')}^{{commit}}")
            .decode().strip() for pr in prs}
    root = Path(tempfile.mkdtemp(prefix=f"sparkz-mvp-{args.scenario}-"))
    source = root / "source"
    source.mkdir()
    files = set(git("ls-files", "-z").decode().split("\0")) - {""}
    # Also include the new tests while this test-only branch is still uncommitted.
    for folder in ("src/test/java/com/sparkz/sparkz/acceptance", "src/test/resources/acceptance"):
        files.update(str(p.relative_to(repo)) for p in (repo / folder).rglob("*") if p.is_file())
    hashes = {}
    for relative in sorted(files):
        original = repo / relative
        if not original.is_file() or original.is_symlink():
            continue
        if original.name == ".env" or (original.name.startswith(".env.") and original.name != ".env.example"):
            continue
        destination = source / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(original, destination)
        hashes[relative] = hashlib.sha256(original.read_bytes()).hexdigest()

    overlays = {}
    for pr, ref in refs.items():
        # All production Java files from that exact PR tree, including local security configuration.
        # Shared identical files are accepted; conflicting same-path implementations are never chosen silently.
        paths = git("ls-tree", "-r", "--name-only", ref, "src/main/java").decode().splitlines()
        overlays[str(pr)] = {}
        for relative in paths:
            data = git("show", f"{ref}:{relative}")
            destination = source / relative
            if destination.exists() and destination.read_bytes() != data:
                raise RuntimeError(f"PR #{pr} has a conflicting source file: {relative}; coordinate its owner first")
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(data)
            overlays[str(pr)][relative] = hashlib.sha256(data).hexdigest()

    manifest = {"scenario": args.scenario, "base": git("rev-parse", "HEAD").decode().strip(),
                "pr_refs": refs, "source_hashes": hashes, "overlay_hashes": overlays,
                "tests": selection, "database": "isolated H2; explicit acceptance properties"}
    (root / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    # No .env is copied, and supplied production/database overrides are not forwarded to Maven.
    env = {k: v for k, v in os.environ.items()
           if not k.startswith(("SUPABASE", "SPRING_", "DOTENV", "SPARKZ_POSTGRES_TEST_"))}
    command = [args.maven] if args.maven else ["bash", str(source / "mvnw")]
    command += ["-B", "-ntp", *args.maven_arg]
    if selection:
        command.append(f"-Dtest={selection}")
    command.append("test")
    print(f"Snapshot and reports: {root}", flush=True)
    with (root / "maven.log").open("w") as log:
        result = subprocess.run(command, cwd=source, env=env, stdout=log, stderr=subprocess.STDOUT)
    (root / "exit-code.txt").write_text(str(result.returncode) + "\n")
    print(f"Maven exit code: {result.returncode}; log: {root / 'maven.log'}", flush=True)
    return result.returncode


if __name__ == "__main__":
    raise SystemExit(main())
