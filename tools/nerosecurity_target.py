#!/usr/bin/env python3
"""Shared target resolution for the NeroSecurity asset tools (port of NeroTech's nerotech_target).

NeroSecurity is multiloader-only: the shared tree lives in ``common/src/main/{java,resources}`` and there
is no single-loader root tree, so the DEFAULT target is ``multiloader`` (common). The
``--multiloader``/``--common``/``--root`` flags and the ``NEROSECURITY_TARGET`` env var are kept for CLI
parity with the sibling tools. Repo-root paths (``art/``, ``tools/``) never move; only the ``src/main``
base does.

Usage in a tool:

    from nerosecurity_target import REPO_ROOT, src_base, target_label, resolve

    SRC_BASE = src_base()            # common (default), or the repo root with --root
    path = resolve("src/main/...")   # -> SRC_BASE/src/main/...
    art  = resolve("art/...")        # -> REPO_ROOT/art/... (always)
"""
import os
import sys

# tools/ -> repo root
REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Where the multiloader's shared module lives (holds its own src/main/{java,resources}).
MULTILOADER_COMMON = os.path.join(REPO_ROOT, "common")


def _target(argv):
    t = os.environ.get("NEROSECURITY_TARGET", "multiloader").strip().lower()
    if any(a in ("--multiloader", "--common") for a in argv):
        t = "multiloader"
    if "--root" in argv:  # explicit override wins (parity only; NeroSecurity has no root tree)
        t = "root"
    return "multiloader" if t in ("multiloader", "common") else "root"


def is_multiloader(argv=None):
    return _target(sys.argv if argv is None else argv) == "multiloader"


def src_base(argv=None):
    """Directory that contains ``src/main/{java,resources}`` for the chosen target."""
    return MULTILOADER_COMMON if is_multiloader(argv) else REPO_ROOT


def target_label(argv=None):
    return "common" if is_multiloader(argv) else "root"


def resolve(rel, argv=None):
    """Absolute path for a repo-relative path, routing ``src/...`` to the chosen target base and
    everything else (``art/...``, ``tools/...``, ``common/...``) to the repo root."""
    rel = rel.replace("\\", "/")
    base = src_base(argv) if rel.startswith("src/") else REPO_ROOT
    return os.path.join(base, rel)
