#!/usr/bin/env bash
# script.sh - Comprehensive System Report Generator
# Generates a detailed report of system information including hardware,
# OS, network, disk, memory, CPU, and running processes.

set -euo pipefail

# ─── Configuration ────────────────────────────────────────────────────────────
REPORT_DATE=$(date '+%Y-%m-%d %H:%M:%S %Z')
HOSTNAME=$(hostname)
SEPARATOR="════════════════════════════════════════════════════════════════"
THIN_SEP="────────────────────────────────────────────────────────────────"

# ─── Helper Functions ─────────────────────────────────────────────────────────

section() {
    echo ""
    echo "$SEPARATOR"
    echo "  $1"
    echo "$SEPARATOR"
}

subsection() {
    echo ""
    echo "  $THIN_SEP"
    echo "  $1"
    echo "  $THIN_SEP"
}

cmd_or_na() {
    # Run a command; print output or "N/A" if unavailable
    if command -v "$1" &>/dev/null; then
        "$@" 2>/dev/null || echo "  N/A"
    else
        echo "  N/A (command '$1' not found)"
    fi
}

indent() {
    sed 's/^/  /'
}

# ─── Report Header ────────────────────────────────────────────────────────────

echo "$SEPARATOR"
echo "  COMPREHENSIVE SYSTEM REPORT"
echo "  Generated : $REPORT_DATE"
echo "  Host      : $HOSTNAME"
echo "$SEPARATOR"

# ─── 1. Operating System ──────────────────────────────────────────────────────

section "1. OPERATING SYSTEM"

if [[ -f /etc/os-release ]]; then
    echo ""
    cat /etc/os-release | indent
elif [[ "$(uname)" == "Darwin" ]]; then
    echo ""
    sw_vers | indent
else
    uname -a | indent
fi

echo ""
echo "  Kernel   : $(uname -r)"
echo "  Arch     : $(uname -m)"
echo "  Platform : $(uname -s)"

# ─── 2. Hardware / CPU ────────────────────────────────────────────────────────

section "2. CPU INFORMATION"

if [[ "$(uname)" == "Darwin" ]]; then
    subsection "CPU Model"
    sysctl -n machdep.cpu.brand_string 2>/dev/null | indent || echo "  N/A"
    subsection "CPU Cores"
    echo "  Physical cores : $(sysctl -n hw.physicalcpu 2>/dev/null || echo N/A)"
    echo "  Logical cores  : $(sysctl -n hw.logicalcpu 2>/dev/null || echo N/A)"
    subsection "CPU Frequency"
    sysctl -n hw.cpufrequency 2>/dev/null \
        | awk '{printf "  %.2f GHz\n", $1/1e9}' || echo "  N/A"
else
    if command -v lscpu &>/dev/null; then
        subsection "lscpu"
        lscpu | indent
    fi
    if [[ -f /proc/cpuinfo ]]; then
        subsection "/proc/cpuinfo (summary)"
        grep -E "^(model name|cpu MHz|cache size|siblings|cpu cores)" \
            /proc/cpuinfo | sort -u | indent
    fi
fi

# ─── 3. Memory ────────────────────────────────────────────────────────────────

section "3. MEMORY INFORMATION"

if [[ "$(uname)" == "Darwin" ]]; then
    total_mem=$(sysctl -n hw.memsize 2>/dev/null)
    echo "  Total RAM : $(awk "BEGIN{printf \"%.2f GB\n\", $total_mem/1073741824}")"
    subsection "VM Statistics"
    vm_stat | indent
else
    if [[ -f /proc/meminfo ]]; then
        subsection "/proc/meminfo"
        cat /proc/meminfo | indent
    fi
    if command -v free &>/dev/null; then
        subsection "free -h"
        free -h | indent
    fi
fi

# ─── 4. Disk Usage ────────────────────────────────────────────────────────────

section "4. DISK USAGE"

subsection "Filesystem Usage (df -h)"
df -h 2>/dev/null | indent

if command -v lsblk &>/dev/null; then
    subsection "Block Devices (lsblk)"
    lsblk | indent
fi

if command -v fdisk &>/dev/null && [[ $EUID -eq 0 ]]; then
    subsection "Partition Table (fdisk -l)"
    fdisk -l 2>/dev/null | indent
fi

# ─── 5. Network ───────────────────────────────────────────────────────────────

section "5. NETWORK INFORMATION"

subsection "Hostname & DNS"
echo "  Hostname : $(hostname)"
echo "  FQDN     : $(hostname -f 2>/dev/null || echo N/A)"

if command -v ip &>/dev/null; then
    subsection "IP Addresses (ip addr)"
    ip addr 2>/dev/null | indent
elif command -v ifconfig &>/dev/null; then
    subsection "Network Interfaces (ifconfig)"
    ifconfig 2>/dev/null | indent
fi

if command -v ip &>/dev/null; then
    subsection "Routing Table (ip route)"
    ip route 2>/dev/null | indent
elif command -v netstat &>/dev/null; then
    subsection "Routing Table (netstat -rn)"
    netstat -rn 2>/dev/null | indent
fi

subsection "DNS Resolvers"
if [[ -f /etc/resolv.conf ]]; then
    cat /etc/resolv.conf | grep -v '^#' | grep -v '^$' | indent
else
    echo "  /etc/resolv.conf not found"
fi

if command -v ss &>/dev/null; then
    subsection "Listening Ports (ss -tlnp)"
    ss -tlnp 2>/dev/null | indent
elif command -v netstat &>/dev/null; then
    subsection "Listening Ports (netstat -tlnp)"
    netstat -tlnp 2>/dev/null | indent
fi

# ─── 6. Running Processes ─────────────────────────────────────────────────────

section "6. RUNNING PROCESSES"

subsection "Top 20 Processes by CPU"
ps aux --sort=-%cpu 2>/dev/null | head -21 | indent \
    || ps aux 2>/dev/null | head -21 | indent

subsection "Top 20 Processes by Memory"
ps aux --sort=-%mem 2>/dev/null | head -21 | indent \
    || ps aux 2>/dev/null | head -21 | indent

subsection "Process Count"
echo "  Total processes : $(ps aux 2>/dev/null | tail -n +2 | wc -l | tr -d ' ')"

# ─── 7. System Load & Uptime ──────────────────────────────────────────────────

section "7. SYSTEM LOAD & UPTIME"

echo ""
uptime | indent

if [[ -f /proc/loadavg ]]; then
    echo ""
    echo "  Load averages (1m / 5m / 15m):"
    awk '{printf "  %s / %s / %s\n", $1, $2, $3}' /proc/loadavg
fi

# ─── 8. Users & Login Activity ────────────────────────────────────────────────

section "8. USERS & LOGIN ACTIVITY"

subsection "Currently Logged-in Users"
who 2>/dev/null | indent || echo "  N/A"

subsection "Last Logins"
last 2>/dev/null | head -20 | indent || echo "  N/A"

subsection "Local User Accounts"
if [[ -f /etc/passwd ]]; then
    awk -F: '$3 >= 1000 && $3 < 65534 {printf "  %-20s uid=%-6s shell=%s\n", $1, $3, $7}' \
        /etc/passwd
else
    echo "  /etc/passwd not accessible"
fi

# ─── 9. Installed Packages ────────────────────────────────────────────────────

section "9. INSTALLED PACKAGES"

if command -v dpkg &>/dev/null; then
    subsection "dpkg (Debian/Ubuntu)"
    echo "  Total installed packages: $(dpkg -l 2>/dev/null | grep -c '^ii')"
    echo "  (Run 'dpkg -l' for full list)"
elif command -v rpm &>/dev/null; then
    subsection "rpm (RHEL/CentOS/Fedora)"
    echo "  Total installed packages: $(rpm -qa 2>/dev/null | wc -l | tr -d ' ')"
    echo "  (Run 'rpm -qa' for full list)"
elif command -v brew &>/dev/null; then
    subsection "Homebrew (macOS)"
    echo "  Total installed formulae: $(brew list --formula 2>/dev/null | wc -l | tr -d ' ')"
    echo "  Total installed casks   : $(brew list --cask 2>/dev/null | wc -l | tr -d ' ')"
elif command -v pacman &>/dev/null; then
    subsection "pacman (Arch)"
    echo "  Total installed packages: $(pacman -Q 2>/dev/null | wc -l | tr -d ' ')"
else
    echo "  No recognised package manager found."
fi

# ─── 10. Environment & Shell ──────────────────────────────────────────────────

section "10. ENVIRONMENT & SHELL"

subsection "Shell Information"
echo "  Current shell : ${SHELL:-N/A}"
echo "  Script shell  : $(ps -p $$ -o comm= 2>/dev/null || echo N/A)"

subsection "Key Environment Variables"
for var in HOME USER LANG LC_ALL PATH TERM EDITOR VISUAL TZ; do
    printf "  %-12s = %s\n" "$var" "${!var:-<unset>}"
done

# ─── 11. Security ─────────────────────────────────────────────────────────────

section "11. SECURITY INFORMATION"

subsection "SELinux Status"
if command -v getenforce &>/dev/null; then
    echo "  SELinux: $(getenforce 2>/dev/null)"
else
    echo "  SELinux: not available"
fi

subsection "AppArmor Status"
if command -v apparmor_status &>/dev/null && [[ $EUID -eq 0 ]]; then
    apparmor_status 2>/dev/null | head -10 | indent
elif command -v aa-status &>/dev/null && [[ $EUID -eq 0 ]]; then
    aa-status 2>/dev/null | head -10 | indent
else
    echo "  AppArmor: not available or insufficient privileges"
fi

subsection "Firewall"
if command -v ufw &>/dev/null; then
    echo "  UFW status: $(ufw status 2>/dev/null | head -1 || echo N/A)"
elif command -v firewall-cmd &>/dev/null; then
    echo "  firewalld: $(firewall-cmd --state 2>/dev/null || echo N/A)"
elif command -v iptables &>/dev/null && [[ $EUID -eq 0 ]]; then
    echo "  iptables rules:"
    iptables -L --line-numbers 2>/dev/null | head -20 | indent
else
    echo "  No recognised firewall tool found."
fi

subsection "Failed Login Attempts"
if [[ -f /var/log/auth.log ]]; then
    echo "  Recent failures from /var/log/auth.log:"
    grep -i "failed\|failure" /var/log/auth.log 2>/dev/null | tail -10 | indent \
        || echo "  None found or insufficient privileges"
elif [[ -f /var/log/secure ]]; then
    echo "  Recent failures from /var/log/secure:"
    grep -i "failed\|failure" /var/log/secure 2>/dev/null | tail -10 | indent \
        || echo "  None found or insufficient privileges"
else
    echo "  Auth log not accessible"
fi

# ─── 12. System Services ──────────────────────────────────────────────────────

section "12. SYSTEM SERVICES"

if command -v systemctl &>/dev/null; then
    subsection "Failed systemd Units"
    systemctl --failed 2>/dev/null | indent || echo "  N/A"
    subsection "Running systemd Services (top 20)"
    systemctl list-units --type=service --state=running 2>/dev/null \
        | head -22 | indent || echo "  N/A"
elif command -v service &>/dev/null; then
    subsection "Service Status (service --status-all)"
    service --status-all 2>/dev/null | indent || echo "  N/A"
elif [[ "$(uname)" == "Darwin" ]]; then
    subsection "launchctl (macOS) — loaded services"
    launchctl list 2>/dev/null | head -30 | indent || echo "  N/A"
else
    echo "  No recognised service manager found."
fi

# ─── Report Footer ────────────────────────────────────────────────────────────

echo ""
echo "$SEPARATOR"
echo "  END OF REPORT"
echo "  Generated : $REPORT_DATE"
echo "  Host      : $HOSTNAME"
echo "$SEPARATOR"
echo ""