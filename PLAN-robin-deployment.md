# Plan: Deploy Robin Stack to Proxmox LXC + Hetzner HAProxy

## Context

All DMARC porting tasks (TASK-00 through TASK-17) are complete. The robin stack now needs to be
deployed as a production environment:

- **robin** — Java 21 MTA + DMARC backend (ports 25/465/587 SMTP, 8080/8090 API)
- **robin-gateway** — Spring Cloud Gateway (auth, routing, rate-limiting, port 8080)
- **robin-ui** — Angular 21 SPA served via nginx (port 80)
- **PostgreSQL 15** — DMARC reports DB (`robin_db`) + gateway auth DB (`robin_gw_db`)
- **Redis 7** — Rate-limiting cache for robin-gateway

Infrastructure:
- **Proxmox LXC (home)**: all application services in one Debian 12 container
- **Hetzner Cloud VPS**: HAProxy + Let's Encrypt — masks home IP from public internet

Ansible playbook follows existing patterns from:
- `infrastructure/personal/ansible/playbooks/pihole-unbound/` (LXC provisioning role)
- `infrastructure/personal/ansible/playbooks/stalwart/` (systemd service pattern)

---

## Architecture

```
Internet (HTTPS :443 / SMTP :25/:465/:587)
         │
         ▼
┌────────────────────────────────────────────┐
│  Hetzner Cloud VPS                         │
│  HAProxy 2.6 + Certbot (Let's Encrypt)     │
│                                            │
│  :443 → TLS term → home:80  (web)          │
│  :25/:465/:587 → TCP proxy → home:25/465/587│
└──────────────────┬─────────────────────────┘
                   │ TCP to home IP
                   ▼
┌────────────────────────────────────────────────────┐
│  Proxmox LXC — Debian 12                           │
│  hostname: robin-lxc                               │
│                                                    │
│  nginx :80                                         │
│    /           → /var/www/robin-ui (SPA)           │
│    /v1/ /api/  → 127.0.0.1:8080 (gateway)         │
│    /client/    → 127.0.0.1:8090 (robin API)        │
│    /health/ /logs /store → 127.0.0.1:8080 / :8090 │
│                                                    │
│  robin-gateway :8080 (Spring Boot)                 │
│    → JWT auth, routing, rate-limit (Redis)         │
│    → proxies to robin :8080 / :8090                │
│                                                    │
│  robin :8080/:8090/:25/:465/:587 (Java MTA)        │
│                                                    │
│  PostgreSQL :5432  Redis :6379                     │
└────────────────────────────────────────────────────┘
```

---

## Ansible Structure

**New playbook root**: `infrastructure/personal/ansible/playbooks/robin/`

```
playbooks/robin/
├── main-lxc.yml              # Play 1: provision LXC → Play 2: deploy services
├── main-haproxy.yml          # HAProxy cloud deployment
├── ansible.cfg               # host_key_checking=False, vault_password_file
├── inventory/
│   └── hosts.yml             # Groups: robin_lxc, haproxy_cloud
├── group_vars/
│   ├── robin_lxc.yml         # LXC IP, specs, service ports, build paths
│   ├── robin_lxc_vault.yml   # DB passwords, JWT secret, Redis auth (encrypted)
│   ├── haproxy_cloud.yml     # Hetzner IP, domain name, home_ip
│   └── haproxy_cloud_vault.yml  # Certbot email (encrypted)
└── roles/
    ├── proxmox_lxc -> ../../pihole-unbound/roles/proxmox_lxc   # SYMLINK (reuse)
    ├── debian_setup -> ../../pihole-unbound/roles/debian_setup  # SYMLINK (reuse)
    ├── postgresql/
    ├── redis/
    ├── robin/
    ├── robin-gateway/
    ├── robin-ui/
    └── haproxy-cloud/
```

---

## Playbook Design

### `main-lxc.yml` — three sequential plays

```yaml
# Play 1: Provision the LXC (runs on localhost via Proxmox API)
- name: Provision Proxmox LXC for Robin
  hosts: localhost
  roles:
    - role: proxmox_lxc     # reused role — creates+starts container
      tags: [proxmox, lxc]

# Play 2: Harden the new container
- name: Harden Debian LXC
  hosts: robin_lxc
  roles:
    - role: debian_setup    # reused role — UFW, fail2ban, SSH hardening
      tags: [debian, harden]

# Play 3: Deploy all services
- name: Deploy Robin Services
  hosts: robin_lxc
  roles:
    - { role: postgresql,    tags: [postgresql, db] }
    - { role: redis,         tags: [redis, cache] }
    - { role: robin,         tags: [robin, mta] }
    - { role: robin-gateway, tags: [gateway] }
    - { role: robin-ui,      tags: [ui, nginx] }
```

### `main-haproxy.yml` — one play

```yaml
- name: Deploy HAProxy to Hetzner Cloud VPS
  hosts: haproxy_cloud
  roles:
    - { role: haproxy-cloud, tags: [haproxy, tls, cloud] }
```

---

## Role Details

### postgresql role
- Install via `apt: name=postgresql-15`
- Create databases: `robin_db`, `robin_gw_db`
- Create user `robin` with password from vault; GRANT ALL on both DBs
- Template `pg_hba.conf`: local socket = trust, `127.0.0.1/32` = md5

### redis role
- Install via `apt: name=redis-server`
- Configure `/etc/redis/redis.conf`: `bind 127.0.0.1`, `requirepass {{ redis_password }}`,
  `maxmemory 256mb`, `maxmemory-policy allkeys-lru`

### robin role
- Create OS user `robin` (`/usr/local/robin` home, no login shell)
- `copy` pre-built `robin.jar` from `{{ local_robin_jar }}` to `/usr/local/robin/lib/robin.jar`
- Template `server.json5` and `dmarc.json5` configs
- Template `robin.service` → `/etc/systemd/system/robin.service`
- UFW: allow 25/465/587 from any; 8080/8090 from 127.0.0.1 only

### robin-gateway role
- **build task** (delegate_to: localhost): `mvn clean package -DskipTests`
- Create OS user `robin-gateway`, dir `/opt/robin-gateway`
- Template `application.yml` with DB, Redis, JWT, and robin API config
- Template `robin-gateway.service`

### robin-ui role
- **build task** (delegate_to: localhost): `ng build --configuration=production`
- Install nginx, sync `dist/robin-ui/` → `/var/www/robin-ui/`
- Template `nginx.conf` with reverse proxy to gateway and robin API

### haproxy-cloud role
- Install haproxy + certbot on Hetzner VPS
- Certbot for Let's Encrypt TLS cert
- systemd timer for cert renewal
- Template `haproxy.cfg` with HTTPS frontend + TCP SMTP backends → home IP

---

## group_vars Placeholders

`group_vars/robin_lxc.yml` (plain):
```yaml
container_id: 200
container_hostname: robin-lxc
container_ip: "10.240.x.x"        # fill in
container_gateway: "10.240.x.1"   # fill in
container_memory: 4096
container_cores: 4
container_disk_size: 40
container_storage: local-lvm

local_robin_jar: "~/development/workspace/open-source/transilvlad-robin/target/robin.jar"
local_gateway_path: "~/development/workspace/open-source/robin-ui/robin-gateway"
local_ui_path: "~/development/workspace/open-source/robin-ui"
```

`group_vars/haproxy_cloud.yml` (plain):
```yaml
domain: "robin.example.com"       # fill in
home_ip: "x.x.x.x"               # fill in (home public IP)
```

---

## Task Registry (TASK-18–27)

| ID      | Track            | Depends On               | Blocks         | Description                                         |
|---------|------------------|--------------------------|----------------|-----------------------------------------------------|
| TASK-18 | Deploy Foundation| —                        | ALL 19-27      | Ansible inventory, group_vars, playbook skeleton     |
| TASK-19 | Deploy LXC       | TASK-18                  | TASK-20,21,22,23,24 | proxmox_lxc symlink + run: create+start LXC    |
| TASK-20 | Deploy Services  | TASK-19                  | TASK-22,23,26  | postgresql role: install + two DBs + user           |
| TASK-21 | Deploy Services  | TASK-19                  | TASK-23,26     | redis role: install + auth + bind 127.0.0.1         |
| TASK-22 | Deploy Services  | TASK-19, TASK-20         | TASK-26        | robin role: JAR copy + systemd + UFW rules           |
| TASK-23 | Deploy Services  | TASK-19, TASK-20, TASK-21| TASK-26        | robin-gateway role: mvn build + systemd + config    |
| TASK-24 | Deploy Services  | TASK-19                  | TASK-26        | robin-ui role: ng build + nginx deploy              |
| TASK-25 | Deploy Cloud     | TASK-18                  | TASK-27        | haproxy-cloud role: HAProxy + Certbot TLS           |
| TASK-26 | Deploy Playbook  | TASK-19–24               | TASK-27        | main-lxc.yml: full LXC stack wired and running      |
| TASK-27 | Deploy Playbook  | TASK-25, TASK-26         | —              | main-haproxy.yml: cloud proxy + end-to-end smoke    |

---

## Verification

1. **LXC exists**: `ansible-playbook main-lxc.yml --tags proxmox` → container visible in Proxmox UI
2. **Services up**: SSH to LXC → `systemctl status robin robin-gateway nginx postgresql redis` — all active
3. **Web (internal)**: `curl http://{{ container_ip }}/` → Angular HTML (nginx)
4. **API (internal)**: `curl http://{{ container_ip }}/api/v1/health` → `{"status":"UP"}`
5. **TLS cert**: `echo | openssl s_client -connect {{ domain }}:443 2>/dev/null | openssl x509 -noout -dates`
6. **Web (external)**: `curl https://{{ domain }}/` → Angular HTML through HAProxy
7. **SMTP (external)**: `telnet {{ domain }} 25` connects and robin greets
8. **DMARC API**: `curl -u admin:pass https://{{ domain }}/client/dmarc/reports` → JSON list
