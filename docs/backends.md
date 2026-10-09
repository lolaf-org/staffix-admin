# Backends

What each backend needs so the console can read what staffix exports. `staffix-admin-sandbox/docker` holds working
configurations of all of them: see the [sandbox](../staffix-admin-sandbox/README.md).

## Prometheus

Prometheus charts read the engines' micrometer meters received through the OpenTelemetry collector with
`translation_strategy: UnderscoreEscapingWithoutSuffixes`: a meter or a tag named with dots or dashes reads with
underscores. Another translation strategy, which adds unit suffixes, is not supported. RTT and clock offset need
staffix's RTT measurement enabled.

A rate needs two samples in the window it is averaged over, so the data sets the finest window a chart can show:
twice the engine's OTLP export `step` (staffix default 10 s), and twice the Prometheus scrape interval when
Prometheus scrapes the collector's `prometheus` exporter. The console knows the first from the engine; set the
connector's `minRateWindow` to the second (with Prometheus's own OTLP receiver there is no scrape: set it no higher
than twice the export step).

Scrape faster than the engines export, e.g. every 2 s for a 5 s step: the collector keeps only each meter's latest
export, so a scrape at the same pace misses exports and leaves too few samples for the narrowest windows, which then
chart nothing. To see second by second, an engine must export every second.

## Loki

Loki holds what staffix's OTLP messages logger sends: every message in and out, and session events. Loki keeps the
`fix.*` attributes as structured metadata, so by default the console filters them after a stream selector that scans
every stream of the time range. Promote `fix.eid`, `fix.sg` and `fix.sn` to index labels in Loki's `otlp_config` (as
`staffix-admin-sandbox/docker/conf/loki.yaml` does) and list them in the connector's `indexedLabels`, so a query reads
only the streams of its engine, group or session.

Loki indexes labels, not the message content: a text search reads the selected streams. See
[what a search costs](logs-and-trace.md#what-a-search-costs).

## Elasticsearch

The Elasticsearch connectors read what the OpenTelemetry collector's `elasticsearch` exporter writes with its OTel
mapping (the default), into the `metrics-*.otel-*` and `logs-*.otel-*` data streams (each connector's `index`). A
`fix.*` attribute is matched by its own name, whether staffix sent it as a resource or a record attribute. The
collector needs two processors on the metrics pipeline (as in
`staffix-admin-sandbox/docker/conf/otel-collector-elastic.yaml`):

- `cumulative_to_delta` on histograms: Elasticsearch stores only delta histograms, and drops the cumulative ones
  micrometer exports.
- A rename of micrometer's `<timer>.max` gauges, e.g. to `<timer>_max` with `metrics_transform`: when Elasticsearch
  maps `metrics.<timer>.max` before `metrics.<timer>`, the timer is no longer mapped as a histogram and cannot be read.

Points are computed per step: a rate from the recordings of the buckets in its window, a mean from their sum and
count, a quantile once per window from the histogram (so a window wider than the step gives fewer points), a gauge's
value as its average over the step. A mean or a quantile is approximated from the histogram's buckets, and a timer
without a histogram (an `aggregate_metric_double`) has a rate and a mean but no quantile. Set `minRateWindow` to twice
the engines' export step.

A logs text filter matches whole words of the message, case insensitive (`body.text` is a text field), rather than
any substring.
