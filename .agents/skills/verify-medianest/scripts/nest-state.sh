#!/usr/bin/env bash
# nest-state.sh — read and rewrite MediaNest's PERSISTED state from the host.
#
# The UI can only show what it re-rendered. A download that never wrote a row, or a
# toggle that never reached disk, looks exactly like one that did. These helpers read
# the same bytes the app reads — the Room database and the DataStore protobuf files —
# and rewrite them when a verifier needs a precondition it cannot tap into existence.
#
#   nest-state.sh doctor                            is this instance readable at all?
#   nest-state.sh db-pull <dir>                     copy media_nest.db (+wal +shm) into <dir>
#   nest-state.sh db-query "select count(*) from videos"
#   nest-state.sh db-count downloads "status='COMPLETED'"
#   nest-state.sh db-push-sql "update downloads set status='QUEUED'"
#   nest-state.sh pref-dump download_prefs [--raw]
#   nest-state.sh pref-get collections_prefs full_titles
#   nest-state.sh pref-bool collections_prefs full_titles
#   nest-state.sh logcat-clear
#   nest-state.sh crash-check [--out <path>]
#   nest-state.sh evidence-dir [label]
#
# Env: NEST_PKG (default com.example.medianest.debug), NEST_SERIAL, NEST_ROOM_DB.
# Needs the device booted (`scripts/nest.sh up`) and a debuggable build (debug APK),
# because reads and writes both go through `run-as`.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../../../.." && pwd)"
. "$ROOT/scripts/lib/common.sh"

NEST_PKG="${NEST_PKG:-com.example.medianest.debug}"
ROOM_DB="${NEST_ROOM_DB:-media_nest.db}"
DB_REL="databases/$ROOM_DB"
DSTORE_REL="files/datastore"

# ── target + adb shims ───────────────────────────────────────────────────────
# Same resolution `scripts/ad.sh` uses, so state checks and UI drives always look
# at the same device — including a Windows host emulator reached over TCP adb.
SERIAL=""
need_target() {
  SERIAL="${NEST_SERIAL:-$(nest_device_serial)}"
  [ -n "$SERIAL" ] || die "no ready Android device — run: scripts/nest.sh up (or set NEST_SERIAL)"
  sadb get-state >/dev/null 2>&1 || die "device $SERIAL is not answering adb"
}
sadb()   { timeout "${NEST_ADB_TIMEOUT:-45}" adb -s "$SERIAL" "$@"; }
sshell() { sadb shell "$@" 2>/dev/null | tr -d '\r'; }

usage() { sed -n '2,23p' "$0" | sed 's/^# \{0,1\}//'; }

# common.sh's bad()/die() print to stdout, and several helpers below run inside
# $(...), where an error message would be captured as if it were data. Every
# failure in this script goes to stderr.
die() { bad "$*" >&2; exit 1; }

# ── temp files ─────────────────────────────────────────────────────────────
# One run directory, one trap. A per-call array would not work: several callers wrap
# the helper in $(...), and a path registered inside a subshell dies with it — which
# is exactly how snapshots got stranded in /tmp. Everything lands under
# ~/.cache/medianest/tmp/verify-<pid>/ instead, removed on exit AND on SIGPIPE
# (`nest-state.sh pref-dump … | head` kills the shell before EXIT can run).
NEST_RUN_TMP="$NEST_CACHE/tmp/verify-$$"
NEST_TMP_PATH=""
cleanup_tmp() { rm -rf "$NEST_RUN_TMP" 2>/dev/null; return 0; }
trap cleanup_tmp EXIT
trap 'cleanup_tmp; exit 0' PIPE
tmp_new() {  # tmp_new dir|file -> sets NEST_TMP_PATH
  mkdir -p "$NEST_RUN_TMP" || { bad "cannot create $NEST_RUN_TMP" >&2; return 1; }
  case "${1:-}" in
    dir)  NEST_TMP_PATH="$(mktemp -d "$NEST_RUN_TMP/db.XXXXXX")"   || { bad "mktemp -d failed" >&2; return 1; } ;;
    file) NEST_TMP_PATH="$(mktemp "$NEST_RUN_TMP/pref.XXXXXX")"    || { bad "mktemp failed" >&2; return 1; } ;;
    *)    bad "tmp_new: expected dir|file" >&2; return 1 ;;
  esac
}

# ── Room database ───────────────────────────────────────────────────────────
# The app's data lives in /data/data/<pkg>/databases/media_nest.db in WAL mode and
# the system image ships no sqlite3, so it cannot be queried on the device: copy the
# files out (a -wal without its .db has no committed rows) and read them with the
# host's python3 sqlite3.
db_pull() {
  local dest="$1" f rel
  mkdir -p "$dest" || return 1
  # `adb exec-out` merges the device's stderr into stdout, so a missing file arrives
  # as "cat: ...: No such file or directory" and would pass a size test. Guard on the
  # device with `test -f`, and drop anything that still looks like a device error.
  # -wal/-shm are legitimately absent on a checkpointed DB; absence is not an error.
  for f in "$ROOM_DB" "$ROOM_DB-wal" "$ROOM_DB-shm"; do
    rel="databases/$f"
    sadb exec-out "run-as $NEST_PKG sh -c 'test -f $rel && cat $rel'" \
      > "$dest/$f" 2>/dev/null || true
    if [ ! -s "$dest/$f" ] || grep -qaE '^(run-as|cat):' "$dest/$f"; then
      rm -f "$dest/$f"
    fi
  done
  if [ -s "$dest/$ROOM_DB" ] \
     && [ "$(head -c 15 "$dest/$ROOM_DB")" = "SQLite format 3" ]; then
    return 0
  fi
  rm -f "$dest/$ROOM_DB" "$dest/$ROOM_DB-wal" "$dest/$ROOM_DB-shm"
  bad "no readable $DB_REL via run-as $NEST_PKG (not launched yet? not a debug build?)" >&2
  return 1
}

sqlite_exec() {  # <db-file> <sql> — prints rows tab-separated
  python3 - "$1" "$2" <<'PY'
import sqlite3, sys
con = sqlite3.connect(sys.argv[1])
try:
    for row in con.execute(sys.argv[2]):
        print("\t".join("" if v is None else str(v) for v in row))
finally:
    con.close()
PY
}

# A -shm copied while the app was running can disagree with the -wal. Retry once
# without it (SQLite rebuilds the wal-index from the -wal on open) before failing.
db_query_file() {  # <db-file> <sql>
  local out rc
  out="$(sqlite_exec "$1" "$2" 2>&1)"; rc=$?
  if [ $rc -ne 0 ]; then
    rm -f "$1-shm"
    out="$(sqlite_exec "$1" "$2" 2>&1)"; rc=$?
  fi
  if [ $rc -ne 0 ]; then
    printf '%s\n' "$out" >&2
  else
    printf '%s\n' "$out"
  fi
  return $rc
}

db_temp_query() {  # <sql>
  local sql="$1" tmp
  tmp_new dir || return 1; tmp="$NEST_TMP_PATH"
  db_pull "$tmp" >/dev/null || return 1
  db_query_file "$tmp/$ROOM_DB" "$sql"
}

# ── DataStore preferences ───────────────────────────────────────────────────
# files/datastore/<name>.preferences_pb is PreferencesProto:
#   message PreferencesProto { map<string, Value> preferences = 1; }
#   message Value { bool 1 | float 2 | int32 3 | int64 4 | string 5
#                   | StringSet 6 | double 7 | bytes 8 }
# i.e. field 1 repeated { string key = 1; Value value = 2; }. Decoded here rather
# than grepped, so `full_titles=false` cannot be confused with a leftover `true`
# byte from a previous write.
pref_path() {  # <name-or-file> -> device-relative path
  local f="$1"
  case "$f" in *.preferences_pb) ;; *) f="$f.preferences_pb" ;; esac
  printf '%s/%s\n' "$DSTORE_REL" "$f"
}

pref_fetch() {  # <device-rel-file> <local-dest> — 1 on I/O error, dest removed
  local rel="$1" dest="$2"
  # Same exec-out caveat as db_pull: without the on-device `test -f`, a store that
  # has never been written captures "cat: ...: No such file or directory" as bytes.
  sadb exec-out "run-as $NEST_PKG sh -c 'test -f $rel && cat $rel'" > "$dest" 2>/dev/null || true
  if [ -s "$dest" ] && ! grep -qaE '^(run-as|cat):' "$dest"; then
    return 0
  fi
  rm -f "$dest"
  bad "cannot read $rel (never written yet, or run-as refused)" >&2
  return 1
}

pref_decode() {  # <local-file> [key]  — exit 0 ok, 3 key unset, 4 decode failed
  python3 - "$@" <<'PY'
import struct, sys

def varint(b, i):
    shift = val = 0
    while True:
        if i >= len(b):
            raise ValueError("truncated varint")
        c = b[i]; i += 1
        val |= (c & 0x7F) << shift
        if not c & 0x80:
            return val, i
        shift += 7
        if shift > 63:
            raise ValueError("varint too long")

def signed(v):
    return v - (1 << 64) if v & (1 << 63) else v

def fields(b):
    i, out = 0, []
    while i < len(b):
        key, i = varint(b, i)
        fn, wt = key >> 3, key & 7
        if wt == 0:
            v, i = varint(b, i)
        elif wt == 1:
            if i + 8 > len(b): raise ValueError("truncated fixed64")
            v = b[i:i + 8]; i += 8
        elif wt == 2:
            n, i = varint(b, i)
            if i + n > len(b): raise ValueError("truncated bytes")
            v = b[i:i + n]; i += n
        elif wt == 5:
            if i + 4 > len(b): raise ValueError("truncated fixed32")
            v = b[i:i + 4]; i += 4
        else:
            raise ValueError("unsupported wire type %d" % wt)
        out.append((fn, wt, v))
    return out

def value(vb):
    for fn, wt, v in fields(vb):
        if fn == 1 and wt == 0: return ("bool", bool(v))
        if fn == 2 and wt == 5: return ("float", struct.unpack("<f", v)[0])
        if fn == 3 and wt == 0: return ("int", signed(v))
        if fn == 4 and wt == 0: return ("long", signed(v))
        if fn == 5 and wt == 2: return ("string", v.decode("utf-8", "replace"))
        if fn == 6 and wt == 2:
            return ("set", [s.decode("utf-8", "replace")
                            for sf, swt, s in fields(v) if sf == 1 and swt == 2])
        if fn == 7 and wt == 1: return ("double", struct.unpack("<d", v)[0])
        if fn == 8 and wt == 2: return ("bytes", v.hex())
    raise ValueError("empty Value")

def fmt(kind, v):
    if kind == "bool":   return "true" if v else "false"
    if kind == "set":    return "{" + ", ".join(sorted(v)) + "}"
    if kind in ("float", "double"): return repr(v)
    return str(v)

path = sys.argv[1]
want = sys.argv[2] if len(sys.argv) > 2 else None
try:
    data = open(path, "rb").read()
except OSError as e:
    print("cannot read %s: %s" % (path, e), file=sys.stderr); sys.exit(4)

prefs = {}
try:
    for fn, wt, raw in fields(data):
        if fn != 1 or wt != 2:
            continue
        key = val = None
        for ef, ewt, ev in fields(raw):
            if ef == 1 and ewt == 2:
                key = ev.decode("utf-8", "replace")
            elif ef == 2 and ewt == 2:
                val = value(ev)
        if key is not None and val is not None:
            prefs[key] = val
except Exception as e:
    print("decode failed: %s" % e, file=sys.stderr); sys.exit(4)

if want is None:
    for k in sorted(prefs):
        print("%s=%s" % (k, fmt(*prefs[k])))
    sys.exit(0)
if want not in prefs:
    print("unset: %s (present: %s)" % (want, ", ".join(sorted(prefs)) or "<none>"),
          file=sys.stderr)
    sys.exit(3)
print(fmt(*prefs[want]))
PY
}

NEST_PREF_FILE=""
pref_load() {  # <name-or-file> -> sets NEST_PREF_FILE (registered with the EXIT trap)
  local tmp
  # Allocated here, not inside a $(...): a subshell's array entry dies with it and
  # the file would never be registered for cleanup.
  tmp_new file || return 1; tmp="$NEST_TMP_PATH"
  pref_fetch "$(pref_path "$1")" "$tmp" || return 1
  NEST_PREF_FILE="$tmp"
}

# ── commands ────────────────────────────────────────────────────────────────
cmd_doctor() {
  local rc=0 focus pid ds
  hdr "target"
  SERIAL="${NEST_SERIAL:-$(nest_device_serial)}"
  if [ -z "$SERIAL" ]; then
    bad "no device attached — run: scripts/nest.sh up"
    say "      (under WSL, ADB_SERVER_SOCKET must point at the host adb server)"
    return 1
  fi
  say "  serial        $SERIAL"
  if [ -n "${ADB_SERVER_SOCKET:-}" ]; then say "  adb transport $ADB_SERVER_SOCKET (bridged)"
  else say "  adb transport local"; fi
  say "  package       $NEST_PKG"
  say "  booted        $(sshell getprop sys.boot_completed)"
  say "  sdk           $(sshell getprop ro.build.version.sdk)"

  hdr "app"
  if sadb shell pm path "$NEST_PKG" >/dev/null 2>&1; then
    ok "installed ($(sshell pm path "$NEST_PKG" | head -1 | sed 's|package:||'))"
  else
    bad "not installed — scripts/nest.sh up"; rc=1
  fi
  pid="$(sshell pidof "$NEST_PKG")"
  if [ -n "$pid" ]; then ok "process running (pid $pid)"; else warn "not running (run: scripts/ad.sh launch)"; fi
  focus="$(sshell dumpsys window | grep -E 'mCurrentFocus|mFocusedApp')"
  case "$focus" in
    *"$NEST_PKG"*) ok "foreground (see focus line below)" ;;
    "")            warn "window focus unreadable" ;;
    *)             warn "not foreground" ;;
  esac
  [ -n "$focus" ] && printf '%s\n' "$focus" | sed 's/^/      /'

  hdr "run-as (needed for every read below)"
  if sadb exec-out run-as "$NEST_PKG" id 2>/dev/null | tr -d '\r' | grep -q uid=; then
    ok "run-as $NEST_PKG works (debuggable build)"
  else
    bad "run-as $NEST_PKG refused — not a debug build, or package not installed"
    say "      test with the debug APK (applicationIdSuffix .debug), never release"
    rc=1
  fi

  hdr "state files"
  # Test the file and trust the exit status: `ls | [ -n ... ]` passes on a
  # Directory listing header even when the file itself is absent.
  if sadb shell "run-as $NEST_PKG test -f $DB_REL" >/dev/null 2>&1; then
    ok "$DB_REL"
    sshell run-as "$NEST_PKG" ls -l "$DB_REL" 2>/dev/null | sed 's/^/      /'
  else
    warn "$DB_REL missing (app never opened its DB?)"; rc=1
  fi
  if sadb shell "run-as $NEST_PKG test -d $DSTORE_REL" >/dev/null 2>&1; then
    ok "$DSTORE_REL"
    ds="$(sshell run-as "$NEST_PKG" ls "$DSTORE_REL" 2>/dev/null)"
    [ -n "$ds" ] && printf '%s\n' "$ds" | sed 's/^/      /'
  else
    warn "$DSTORE_REL missing (no preference written yet)"
  fi
  return $rc
}

cmd_db_pull() {
  local dest="${1:-}"
  [ -n "$dest" ] || die "usage: nest-state.sh db-pull <dest-dir>"
  need_target
  hdr "db-pull"
  db_pull "$dest" || return 1
  ls -l "$dest" | sed 's/^/  /'
  ok "snapshot of $DB_REL in $dest"
}

cmd_db_query() {
  local sql="${1:-}"
  [ -n "$sql" ] || die "usage: nest-state.sh db-query \"<SQL>\""
  need_target
  db_temp_query "$sql"
}

cmd_db_count() {
  local table="${1:-}" where="${2:-1=1}" out
  [ -n "$table" ] || die "usage: nest-state.sh db-count <table> [where-clause]"
  need_target
  out="$(db_temp_query "select count(*) from $table where $where" 2>/dev/null)" \
    || die "count failed: $table where $where"
  printf '%s\n' "$out" | head -1
}

# Writing the DB is three separate hazards, all handled here:
#  1. an open connection owns the file — the app must be force-stopped first;
#  2. a stale -wal/-shm replays old frames on next open — both must be deleted;
#  3. `adb push` cannot write into /data/data — stage in /data/local/tmp, then pipe
#     the bytes through `run-as … cat >` (as the app uid, so no SELinux surprise),
#     and fold the WAL in beforehand so the pushed file is self-contained.
cmd_db_push_sql() {
  local sql="${1:-}" tmp rc=1 pid
  [ -n "$sql" ] || die "usage: nest-state.sh db-push-sql \"<SQL>\""
  need_target
  hdr "db-push-sql"
  pid="$(sshell pidof "$NEST_PKG")"
  if [ -n "$pid" ]; then
    warn "app running (pid $pid) — force-stopping before the write"
    sshell am force-stop "$NEST_PKG" >/dev/null 2>&1
    sleep 2
    [ -z "$(sshell pidof "$NEST_PKG")" ] || die "could not force-stop $NEST_PKG"
  fi
  ok "app is stopped (a live connection would overwrite this write)"

  tmp_new dir || return 1; tmp="$NEST_TMP_PATH"
  if db_pull "$tmp" >/dev/null; then
    if python3 - "$tmp/$ROOM_DB" "$sql" 2>"$tmp/sql.err" <<'PY'
import sqlite3, sys
con = sqlite3.connect(sys.argv[1])
try:
    con.execute("PRAGMA foreign_keys = ON")
    con.executescript(sys.argv[2])
    con.commit()
    con.execute("pragma wal_checkpoint(truncate)")
finally:
    con.close()
PY
    then
      rm -f "$tmp/$ROOM_DB-wal" "$tmp/$ROOM_DB-shm"
      # `run-as cp` from /data/local/tmp trips SELinux/0600 on some builds; piping the
      # bytes into the app's own shell writes them as the app uid.
      if sadb push "$tmp/$ROOM_DB" /data/local/tmp/nest-seed.db >/dev/null 2>&1 \
         && sadb shell "cat /data/local/tmp/nest-seed.db | run-as $NEST_PKG sh -c 'cat > $DB_REL && chmod 600 $DB_REL'" >/dev/null 2>&1 \
         && sshell run-as "$NEST_PKG" rm -f "$DB_REL-wal" "$DB_REL-shm" >/dev/null 2>&1; then
        rc=0
      else
        bad "push into $DB_REL failed" >&2
      fi
      # Never leave the staged seed behind, on success or failure.
      sshell rm -f /data/local/tmp/nest-seed.db >/dev/null 2>&1 || true
    else
      bad "SQL rejected by sqlite3 — nothing was written" >&2
      tail -1 "$tmp/sql.err" 2>/dev/null | sed 's/^/      /' >&2
    fi
  else
    bad "no readable $DB_REL — nothing was written" >&2
  fi
  rm -rf "$tmp"   # released now; the EXIT trap is only a backstop
  [ $rc -eq 0 ] && ok "wrote SQL into $DB_REL (WAL checkpointed, stale wal/shm removed)"
  return $rc
}

cmd_pref_dump() {
  local name="" raw=0 a
  for a in "$@"; do
    case "$a" in --raw) raw=1 ;; -*) die "unknown flag: $a" ;; *) name="$a" ;; esac
  done
  [ -n "$name" ] || die "usage: nest-state.sh pref-dump <datastore-file> [--raw]"
  need_target
  hdr "pref-dump $(pref_path "$name")"
  pref_load "$name" || return 1
  if pref_decode "$NEST_PREF_FILE"; then
    :
  else
    warn "protobuf decode failed — raw bytes follow"
    raw=1
  fi
  if [ "$raw" -eq 1 ]; then
    say "  raw $(od -An -v -tx1 "$NEST_PREF_FILE" | tr -s ' ' | tr '\n' ' ')"
  fi
}

cmd_pref_get() {
  local name="${1:-}" key="${2:-}" out rc
  [ -n "$name" ] && [ -n "$key" ] || die "usage: nest-state.sh pref-get <datastore-file> <key>"
  need_target
  pref_load "$name" || return 1
  out="$(pref_decode "$NEST_PREF_FILE" "$key" 2>&1)"; rc=$?
  if [ $rc -ne 0 ]; then
    printf '%s\n' "$out" >&2
    bad "$(pref_path "$name"): $key not readable (exit $rc)" >&2
    return $rc          # 3 = unset key, 4 = undecodable store
  fi
  printf '%s\n' "$out"
}

cmd_pref_bool() {
  local name="${1:-}" key="${2:-}" val rc
  [ -n "$name" ] && [ -n "$key" ] || die "usage: nest-state.sh pref-bool <datastore-file> <key>"
  val="$(cmd_pref_get "$name" "$key")"; rc=$?
  [ $rc -eq 0 ] || return $rc
  case "$val" in
    true|false) printf '%s\n' "$val" ;;
    *) bad "$(pref_path "$name"): $key is not a boolean (got: $val)" >&2; return 1 ;;
  esac
}

cmd_logcat_clear() {
  need_target
  sadb logcat -c >/dev/null 2>&1 || die "logcat -c failed"
  ok "logcat cleared ($SERIAL)"
}

cmd_crash_check() {
  local out="${NEST_CACHE}/last-crashes.txt" raw hits anr rc=0
  while [ $# -gt 0 ]; do
    case "$1" in
      --out)
        [ -n "${2:-}" ] || die "usage: nest-state.sh crash-check [--out <path>]"
        out="$2"; shift 2 ;;
      *) die "unknown argument for crash-check: $1 (usage: crash-check [--out <path>])" ;;
    esac
  done
  need_target
  hdr "crash-check"
  # One capture feeds every pattern. grep -A keeps 25 lines of stack, because a
  # bare "FATAL EXCEPTION" line names no cause and cannot be reported.
  raw="$(sadb logcat -d 2>/dev/null | tr -d '\r')"
  hits="$(printf '%s\n' "$raw" \
    | grep -aE -A 25 "FATAL EXCEPTION|AndroidRuntime.*${NEST_PKG}|ANR in ${NEST_PKG}" \
    || true)"
  # A native crash prints only "Fatal signal 11 … thread 1234 (<proc>)", and the
  # kernel truncates <proc> to 15 characters — so match the block, not the line.
  fatal="$(printf '%s\n' "$raw" | awk -v pkg="$NEST_PKG" -v trunc="${NEST_PKG:0:15}" '
    function flush() { if (blk != "" && hit) printf "%s\n", blk; blk = ""; hit = 0; left = 0 }
    /Fatal signal/ {
      flush(); blk = $0; left = 25
      hit = (index($0, pkg) > 0 || index($0, trunc) > 0)
      next
    }
    left > 0 {
      blk = blk "\n" $0; left--
      if (index($0, pkg) > 0 || index($0, trunc) > 0) hit = 1
    }
    END { flush() }')"
  [ -n "$fatal" ] && hits="${hits}${hits:+$'\n'}${fatal}"
  anr="$(printf '%s\n' "$raw" | grep -aE "ANR in ${NEST_PKG}" || true)"
  dumpsys_anr="$(sshell dumpsys activity | grep -E "ANR in ${NEST_PKG}" || true)"
  [ -n "$dumpsys_anr" ] && anr="${anr}${anr:+$'\n'}${dumpsys_anr}"

  if [ -n "$hits" ] || [ -n "$anr" ]; then
    mkdir -p "$(dirname "$out")" 2>/dev/null || true
    {
      printf '# crash-check %s — %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$SERIAL"
      printf '\n## logcat (25 lines of context per hit)\n%s\n' "$hits"
      printf '\n## dumpsys activity\n%s\n' "$anr"
    } > "$out" 2>/dev/null || warn "cannot write $out"
    if [ -n "$hits" ]; then
      bad "FATAL EXCEPTION / fatal signal in logcat"
      printf '%s\n' "$hits" | head -40 | sed 's/^/      /'
    fi
    if [ -n "$anr" ]; then
      bad "ANR for $NEST_PKG"
      printf '%s\n' "$anr" | head -10 | sed 's/^/      /'
    fi
    say "      evidence: $out"
    rc=1
  else
    ok "no FATAL EXCEPTION, no fatal signal, no ANR for $NEST_PKG"
    say "  (logcat lines: $(printf '%s\n' "$raw" | wc -l | tr -d ' '))"
  fi
  return $rc
}

cmd_evidence_dir() {
  local label="${1:-}" stamp dir
  stamp="$(date -u +%Y%m%d-%H%M%SZ)"
  dir="$ROOT/artifacts/verify-$stamp"
  [ -n "$label" ] && dir="$dir-$label"
  mkdir -p "$dir"/{screens,snapshots,db,logs} || die "cannot create $dir"
  printf '%s\n' "$dir"
  hdr "evidence" >&2
  say "  $dir" >&2
  say "  screens/ snapshots/ db/ logs/" >&2
}

case "${1:-help}" in
  -h|--help|help|"") usage ;;
  doctor)       shift; cmd_doctor "$@" ;;
  db-pull)      shift; cmd_db_pull "$@" ;;
  db-query)     shift; cmd_db_query "$@" ;;
  db-count)     shift; cmd_db_count "$@" ;;
  db-push-sql)  shift; cmd_db_push_sql "$@" ;;
  pref-dump)    shift; cmd_pref_dump "$@" ;;
  pref-get)     shift; cmd_pref_get "$@" ;;
  pref-bool)    shift; cmd_pref_bool "$@" ;;
  logcat-clear) shift; cmd_logcat_clear "$@" ;;
  crash-check)  shift; cmd_crash_check "$@" ;;
  evidence-dir) shift; cmd_evidence_dir "$@" ;;
  *)            die "unknown command: $1 (try: nest-state.sh --help)" ;;
esac
