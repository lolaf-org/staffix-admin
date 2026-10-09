# Metrics

The Metrics page charts an engine, one of its groups or one session; a session's Metrics tab charts that session.
Metrics come from the backend of each session's monitoring plugin (see [Connectors](connectors.md)), never from the
engine.

## Meters

The page offers the meters the scope's running sessions publish, as their engine describes them (staffix's
`GET meters`), each with what it measures in its tooltip:

- staffix's own: message rates in and out, message read, write, decoding and encoding latencies, round-trip time and
  clock offset (both need staffix's RTT measurement enabled);
- the timers and gauges the session's application created through `FixSessionsMonitoringContext`, split by their own
  tags. An application's meter shows once the application asked for it in that session.

A timer reads as a quantile (when it publishes its percentile histogram), a mean or a rate; a gauge as its value. The
logon status gauge is not charted: the session's [availability](sessions.md#availability) shows it.

## Period and rate window

The period is one of the last 15 minutes up to 7 days, live (the last 15 minutes, refreshed every 2 seconds), or a
custom range of up to 31 days. A chart holds about 240 points, down to a point a second over a few minutes.

A rate is averaged over a window, the step by default. A rate needs two samples in its window, so the window cannot
go under twice the engine's export step, nor under the connector's `minRateWindow`; the chart says the window it used.
See [Backends](backends.md) for what sets those floors.

## Around a logged message

A decoded message (from the Logs tab or the Trace page) has a Metrics view: the session's charts a minute either side
of the message, with the message marked.
