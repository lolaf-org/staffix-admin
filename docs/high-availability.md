# High availability

staffix can run an engine as a pair of nodes: an elected leader runs the FIX sessions and a follower keeps a copy of
their stores, ready to take over. The console shows the pair as one, and needs no setup of its own: every engine with
high availability appears here through the HTTP admin API it already uses. See staffix's high availability guide for
how to configure a pair.

## Pairs

Two nodes form a pair when they report the same pair name: `ReplicationHaSettings.pairName`, else the name the leader
election gives (primos's `applicationName`). A node without a name is shown alone, with a hint to set one. Use a
different name for each pair: two pairs with the same name show as one group of four.

A node that stops answering stays in its pair, shown as not answering. The console remembers its pair in memory only,
so after a console restart such a node is not placed until it answers again.

## High availability page

The High availability tab has one card per pair, also reached from the pair's name on the Sessions page. Each node
shows its role, node id, epoch, whether its leadership can be proven, when it took its role and whether it can lead.

- A **leader** lists a line per follower connection: in sync, catching up by snapshot, not in sync or not connected,
  with what is still unsent. A follower that has not applied a write for more than 3 seconds reads as stalled, even
  when its last acknowledgement said in sync. A follower dropped as too slow is listed with the time it may reconnect.
- A **follower** shows its connection to the leader: in sync, catching up or not connected.
- Each node lists its `ha` probes and, on a follower, the alerts of its stores, as a follower has no session page to
  show them on.

With no leader, the card says so: the sessions are down until a node is elected.

### Sessions

The table lists the pair's sessions with their replication mode, their replication state and the sends that went out
without the follower's acknowledgement (red when the last one did). A session's state is one of:

| State | Meaning |
|---|---|
| off | Configured not to replicate (mode `NONE`), by choice |
| in sync | The follower holds what the leader wrote |
| handed over | Handed over cleanly, ready for the follower to take over |
| catching up | The follower is not in sync yet |
| refused (reason) | The follower will not replicate it, for example a mode mismatch or a storage shared with the leader |
| not started | The follower has not started replicating it |
| follower down | The follower does not answer |
| unavailable | The node shown does not answer |

`N writes pending` is added when the follower's store is more than 1,000 writes behind what it acknowledged, as with
an async store: a takeover has to wait for them. `N writes lost on <node>` is added when a node's store failed to
write some messages: a lost message costs a gap fill if it is asked for again. Clearing the `messages-lost` alert
acknowledges the count.

`settings not on the follower yet` or `settings differ on the follower` is added when the follower reports a difference
with the leader's session settings. The follower's card lists them, sessions only it has included, as a failover runs
that node's own settings.

### Changing sessions

Add, change and remove sessions on the leader: the console disables these on a follower and says why. A store that is
replicated passes the change to the follower, without the console waiting for it; a memory store is only compared, so
its differences stay listed.

### Actions

Writers only, each confirmed and [audited](security.md):

- **Switchover**, on the leader: its sessions log out and log on from the other node, without the sequence number jump
  of a failover. It needs a follower in sync and a leader election able to hand leadership over. The engine refuses
  it, and the console reports why, when the follower's store is too far behind or no follower is in sync.
- **Maintenance** and **End maintenance**, on any node whose election supports it: the node stops or resumes standing
  for election. On a leader, maintenance switches over first and fails if that does.
- **Re-sync all** on the leader, or **Re-sync** on one session of the table: copies the session again by snapshot. The
  session is not protected meanwhile, and the copy matches the leader, including what the leader's store lost.

### Replication charts

See [Metrics](metrics.md#high-availability).

## Alerts

The engine's `ha` probes and each session's `ha-replication` probe are listed with the other alerts; an `ha` alert or
an alert from a follower's store opens the High availability page. The engine refuses to clear its `ha` probes, and a
follower's store alert has no Clear, as the engine cannot clear a session it does not run. The `ha` probes reach the
console only when the engine sets `alertsPublisherInstanceId`.

## Demo

The [demo](../README.md#demo) has a pair, `geneva-gateway` (`geneva-a` and `geneva-b`), whose two minute script plays
a stalled follower, a snapshot, a clean handover and a failover. Its actions work, one session is refused and one is
not replicated.
