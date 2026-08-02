# Changelog

Every version of Rain's Resourcepack Manager, newest first.

This plugin also ships as the `rrp` module of Rain's SMP Core. The two are the same sources — exactly one
file differs, the main class — and `tools/vendor-drift.sh` in that repository fails the build if a second
one ever does.

---

## 1.1.0 — 2026-08-03

Everything below already existed in the Rain's SMP Core module and had never reached the standalone jar.
That is the whole reason the drift check now exists.

**Fixed: the standalone jar compiles again.** `reportPackFacts` called `reportFact`, which only exists on
the host's `PluginModule` — vendored code that had leaked back into this tree. Standing alone there is no
host banner to report to, so the fact goes to the console instead.

**Fixed: the pack server no longer leaks two threads per reload.** Its `ExecutorService` is tracked and
shut down when the server stops.

**Changed: a message that concerns only its recipient goes through one place.** `Msg.tell` is the seam:
on its own it is chat, because a standalone plugin has no host setting to obey and quietly moving
messages somewhere the admin never asked for would be a surprise rather than a feature. Inside Rain's SMP
Core the host installs the action-bar routing behind the same call.

**Changed: window titles are capitalised like everything else** — "Catalogue", "Resource packs" — and the
title itself is built through a seam, so the standalone keeps its own `rrp »` gradient while the module
wears the host's brand.

## 1.0.0 — 2026-07-29

**Added: Rain's Resourcepack Manager** — a catalogue of packs, `/rrp` and its menus, merged and stacked
application, the built-in pack server, and pack-status reporting.
