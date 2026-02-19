# DMARC Task Board

Coordination file for parallel sessions. The plan is in `PLAN-mailwhere-to-robin.md`.

## How to use this file

1. **Before starting:** `git pull` to get the latest state of this file.
2. **To claim a task:** edit only your task's row — set `Status` to `in-progress` and
   fill in `Session` with your identifier (e.g. `claude-A`, `cursor-1`, `agent-xyz`).
3. **Commit immediately** after claiming, before writing any code:
   ```bash
   git add TASKS.md
   git commit -m "dmarc(TASK-XX): claim"
   git push
   ```
4. **Pull again and verify** your session name is in the row. If another session beat
   you to it, pick a different task.
5. **When done:** set `Status` to `done`, commit alongside your code and session log.
6. Edit **only your own task's row** — other rows belong to other sessions.

> Git will auto-merge rows edited by different sessions since each row is a separate line.
> A merge conflict on this file means two sessions claimed the same task — the one that
> pushed first wins; the other must revert and pick a different task.

---

## Available tasks (refresh with `git pull` before checking)

A task is available when:
- Its `Status` is `pending`, AND
- All tasks listed in `Depends On` show `done`

---

## Task Registry

<!-- STATUS VALUES: pending | in-progress | done | blocked -->
<!-- Keep rows sorted by ID. Edit only your row. -->

| ID       | Status      | Session | Track                | Depends On              | Blocks                          | Description                              |
|----------|-------------|---------|----------------------|-------------------------|---------------------------------|------------------------------------------|
| TASK-00  | done        | —       | Foundation           | —                       | ALL                             | Contracts & frozen interfaces            |
| TASK-01  | done        | gemini  | Backend Core         | TASK-00                 | TASK-03,04,05,06,07,08          | Java data models + service interfaces    |
| TASK-02  | done        | codex   | Backend Core         | TASK-00                 | TASK-08                         | DB schema SQL (idempotent, total_count)  |
| TASK-03  | done        | gemini  | Backend Core         | TASK-01                 | TASK-07,11                      | DefaultDmarcDnsResolver (JNDI)           |
| TASK-04  | done        | codex   | Backend Core         | TASK-01                 | TASK-07,11                      | DefaultDmarcXmlParser (DOM + 4 XMLs)     |
| TASK-05  | done        | codex   | Backend Core         | TASK-01                 | TASK-11                         | DefaultDmarcRecordValidator              |
| TASK-06  | done        | codex   | Backend Core         | TASK-01                 | TASK-11                         | DefaultDmarcEmailExtractor (MIME/ZIP/GZ) |
| TASK-07  | done        | gemini  | Backend Core         | TASK-01,03,04           | TASK-11                         | DefaultDmarcIpClassifier (5-tier logic)  |
| TASK-08  | done        | gemini  | Backend Core         | TASK-01,02              | TASK-11                         | SqlDmarcReportStore (list no JOIN)       |
| TASK-09  | done        | codex   | Frontend Core        | TASK-00                 | TASK-10,12                      | TypeScript models (interfaces + index)   |
| TASK-10  | done        | gemini  | Frontend Core        | TASK-00,09              | TASK-12                         | Angular ApiService DMARC methods         |
| TASK-11  | done        | gemini  | Backend Integration  | TASK-03,04,05,06,07,08  | TASK-17                         | DmarcHandlerImpl + auth + wiring         |
| TASK-12  | done        | gemini  | Frontend Integration | TASK-09,10              | TASK-13,14,15,16                | Angular feature module skeleton (stubs)  |
| TASK-13  | done        | codex   | Frontend Features    | TASK-12                 | TASK-17                         | Dashboard + Chart.js visualizations      |
| TASK-14  | done        | gemini  | Frontend Features    | TASK-12                 | TASK-17                         | Report list + detail views + badges      |
| TASK-15  | done        | gemini  | Frontend Features    | TASK-12                 | TASK-17                         | Validator form + Ingest form             |
| TASK-16  | done        | codex   | Frontend Features    | TASK-12                 | TASK-17                         | Sidebar nav + app routing                |
| TASK-17  | done        | gemini  | QA                   | TASK-11,13,14,15,16     | —                               | Tests + E2E + auth + JaCoCo ≥80%        |

---

## Dependency graph (visual)

```
TASK-00 (done)
│
├── TASK-01 ──┬── TASK-03 ──┐
│             ├── TASK-04 ──┤
│             ├── TASK-05 ──┤
│             ├── TASK-06 ──┤
│             └── TASK-07 ──┤ (needs 01+03+04)
│                           │
├── TASK-02 ── TASK-08 ─────┤
│                           │
│                           └── TASK-11 ── TASK-17
│
├── TASK-09 ──┐
│             ├── TASK-10 ── TASK-12 ──┬── TASK-13 ──┐
│                                      ├── TASK-14 ──┤
│                                      ├── TASK-15 ──┤
│                                      └── TASK-16 ──┘
│                                                     │
│                                                     └── TASK-17
```

---

## Wave summary (maximum parallelism)

| Wave | Available tasks                                      | Requires                        |
|------|------------------------------------------------------|---------------------------------|
| 1    | TASK-01, TASK-02, TASK-09                            | Only TASK-00 (done)             |
| 2    | TASK-03, TASK-04, TASK-05, TASK-06, TASK-10          | TASK-01 done                    |
| 2b   | TASK-12                                              | TASK-09 + TASK-10 done          |
| 3    | TASK-07, TASK-08                                     | TASK-01+03+04 / TASK-01+02 done |
| 3b   | TASK-13, TASK-14, TASK-15, TASK-16                   | TASK-12 done                    |
| 4    | TASK-11                                              | TASK-03–08 all done             |
| 5    | TASK-17                                              | TASK-11 + TASK-13–16 all done   |

Up to **6 sessions can work simultaneously** during waves 2–3b.

---

## Deployment Task Registry (TASK-18–27)

| ID       | Status      | Session | Track                | Depends On              | Blocks                          | Description                              |
|----------|-------------|---------|----------------------|-------------------------|---------------------------------|------------------------------------------|
| TASK-18 | pending     | —       | Deploy Foundation    | —                       | TASK-19,20,21,22,23,24,25,26,27 | Ansible skeleton + inventory + group_vars |
| TASK-19 | pending     | —       | Deploy LXC           | TASK-18                 | TASK-20,21,22,23,24             | proxmox_lxc role: create+start Debian 12 LXC |
| TASK-20 | pending     | —       | Deploy Services      | TASK-19                 | TASK-22,23,26                   | postgresql role: install + 2 DBs + user  |
| TASK-21 | pending     | —       | Deploy Services      | TASK-19                 | TASK-23,26                      | redis role: install + auth + 127.0.0.1   |
| TASK-22 | pending     | —       | Deploy Services      | TASK-19,20              | TASK-26                         | robin role: JAR + systemd + UFW          |
| TASK-23 | pending     | —       | Deploy Services      | TASK-19,20,21           | TASK-26                         | robin-gateway role: mvn build + systemd  |
| TASK-24 | pending     | —       | Deploy Services      | TASK-19                 | TASK-26                         | robin-ui role: ng build + nginx deploy   |
| TASK-25 | pending     | —       | Deploy Cloud         | TASK-18                 | TASK-27                         | haproxy-cloud role: HAProxy + Certbot    |
| TASK-26 | pending     | —       | Deploy Playbook      | TASK-19,20,21,22,23,24  | TASK-27                         | main-lxc.yml: full LXC stack wired       |
| TASK-27 | pending     | —       | Deploy Playbook      | TASK-25,26              | —                               | main-haproxy.yml: cloud proxy + smoke    |

### Deployment Waves (TASK-18–27)

| Wave | Tasks                                    | Requires                        |
|------|------------------------------------------|---------------------------------|
| D1   | TASK-18                                  | — (bootstrap)                   |
| D2   | TASK-19, TASK-25                         | TASK-18 done                    |
| D3   | TASK-20, TASK-21, TASK-24                | TASK-19 done                    |
| D4   | TASK-22, TASK-23                         | TASK-19 + TASK-20 + TASK-21     |
| D5   | TASK-26                                  | TASK-19–24 all done             |
| D6   | TASK-27                                  | TASK-25 + TASK-26 done          |
