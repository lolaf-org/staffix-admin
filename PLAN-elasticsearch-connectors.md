# Plan: Elasticsearch connectors

Read staffix's FIX logs and metrics from Elasticsearch, as the console reads them from Loki and Prometheus. The data is
what the sandbox's `kibana` profile writes: the OpenTelemetry collector's `elasticsearch` exporter with its OTel-native
mapping, into the `logs-generic.otel-default` and `metrics-generic.otel-default` data streams, histograms converted to
delta (`cumulative_to_delta`).

The console builds each connector's form in the Connectors tab from its settings class (`ConnectorKind`, with
`@Description` and `@Secret`), so the new kinds need no UI code: once their plugins are in `plugins/`, they show up
with their own form and connection test.

## 1. `staffix-admin-logs/staffix-admin-logs-elasticsearch`, kind `elasticsearch-logs`

Built like `staffix-admin-logs-loki`: same pom (everything `provided`), plain `RestClient` calls (no Elasticsearch
client, nothing to shade), `ElasticsearchLogsAutoConfiguration` registering an `ElasticsearchLogsSourceFactory`.
Kinds are unique among metrics and logs factories, hence the `-logs` / `-metrics` suffixes.

`ElasticsearchLogsSettings`:

| setting | default | |
|---|---|---|
| `url` | required | e.g. `http://localhost:9200` |
| `timeout` | `10s` | connecting included |
| `index` | `logs-*.otel-*` | index pattern or data stream |
| `apiKey` | none | `@Secret`, sent as `Authorization: ApiKey ...` |
| `username`, `password` | none | basic auth; `password` is `@Secret` |

`query`: one `POST /{index}/_search`:

- `bool.filter`: `term` on `fix.eid`, and on `fix.sg` / `fix.sn` when set; `terms` on `fix.log.type` (`in`, `out`,
  `event`) and on `fix.msg.type`; `range` on `@timestamp` (`from` inclusive, `to` exclusive).
- The OTel mapping's `attributes` and `resource.attributes` are passthrough objects, so the plain `fix.*` names match
  wherever staffix puts them (today `fix.eid`/`sg`/`sn` are resource attributes, `fix.log.type`/`msg.type` record
  attributes).
- `sort` on `@timestamp` descending, `size` = `limit`, `_source` limited to the fields read.
- Entry: `@timestamp` (`date_nanos`, kept to the nanosecond), `fix.sg`, `fix.sn`, `fix.log.type`, and `body.text` as
  the line.
- Text: `body.text` is `match_only_text`, so the filter is a `match_phrase`: whole words, case insensitive, looser than
  the SPI's case-sensitive "contains". Said in the README.

`testConnection`: `GET /`, which also checks the credentials; then `GET /_resolve/index/{index}` to report a pattern
matching nothing. Errors as `LogsSourceException`, with Elasticsearch's `error.reason`.

## 2. `staffix-admin-metrics/staffix-admin-metrics-elasticsearch`, kind `elasticsearch-metrics`

Same structure. `ElasticsearchMetricsSettings`: `url`, `timeout`, `apiKey`, `username`, `password` as above, plus:

| setting | default | |
|---|---|---|
| `index` | `metrics-*.otel-*` | |
| `timerBaseUnit` | `milliseconds` | as Prometheus's, for engines that do not report theirs |
| `minRateWindow` | `10s` | two OTLP export steps; returned by `getMinRateWindow` |

A meter is the field `metrics.<meter>` (e.g. `metrics.quote.round.trip`): timers as `histogram` (delta counts per
bucket), gauges as `double`. `query`: one `POST /{index}/_search`, `size: 0`:

- `bool.filter`: engine, group, session, and a `term` per `tagFilters` entry (tag names as staffix names them), `range`
  on `@timestamp`, `exists` on the meter's field.
- `date_histogram` on `@timestamp`, `fixed_interval` = `step`, `extended_bounds` from/to; under it, one nested `terms`
  per `splitBy` tag (or none), then the statistic:

| statistic | aggregation | value |
|---|---|---|
| `RATE` | `value_count` on the histogram | recordings in the rate window / window seconds; a window wider than the step sums the buckets it covers (`moving_fn` with `MovingFunctions.sum`, or summed in Java) |
| `QUANTILE` | `percentiles` on the histogram, `percents: [q * 100]` | / timer units per second |
| `MEAN` | `avg` on the histogram | / timer units per second; approximate, as Elasticsearch keeps bucket centres, not the exact sum |
| `VALUE` | `avg` on the gauge | converted by `gaugeUnit` when set |

- Rate window: `max(rateWindow ?: step, minRateWindow)`, reported on each `MetricSeries`.
- Timer unit: `query.getTimerBaseUnit()` when the engine reports it, else the setting.
- Series: one per combination of the split tags' values, labelled by tag; empty buckets and non-finite values skipped;
  series without points dropped.

`testConnection` as for logs, with `MetricsSourceException`.

## 3. Wiring

- `staffix-admin-logs/pom.xml`, `staffix-admin-metrics/pom.xml`: the new modules.
- Root `pom.xml` `dependencyManagement`: both artifacts.
- `staffix-admin-dist`: dependencies, and both in `distribution.xml`'s `plugins/` includes.
- Root `README.md`: modules table, connector kinds (`elasticsearch-metrics`, `elasticsearch-logs`) with a YAML
  example, the Plugins section's list of bundled connectors, and an **Elasticsearch** paragraph: the collector setup it
  expects (`elasticsearch` exporter, OTel mapping, `cumulative_to_delta` on histograms), the text-search and mean
  approximations.

## 4. Sandbox (done with this plan)

- `staffix-admin-sandbox/connectors-es.yaml`: `sandbox-elasticsearch-metrics` (`url: http://localhost:9200`,
  `timerBaseUnit: microseconds`, `minRateWindow: 10s`) and `sandbox-elasticsearch-logs`, both the defaults. The
  sandbox's Elasticsearch runs without security: no credentials.
- `docker/compose.yaml`: the `console` profile/service is now `console-prometheus-loki` (connectors of
  `connectors-prometheus-loki.yaml`, volume `console-data`); the new `console-es` runs the same console with `connectors-es.yaml`
  (volume `console-es-data`). Both use host networking on 8080: one at a time.
- Sandbox README: the renamed profile and `console-es`.

## 5. Tests and verification

- Unit tests as the Loki and Prometheus ones, against a JDK `HttpServer` stub: the request (filters, aggregations,
  index, auth headers), the response read (entries and their order, series and labels, unit conversions, rate over a
  window wider than the step), error statuses and an unreachable server; factory (`url` required) and
  auto-configuration tests.
- Live: `./mvnw install`, `docker compose --profile kibana --profile console-es up -d`, engines with
  `-DotlpUrl=http://localhost:4320`; in the console as `writer`, test both connectors, then check `alpha-bank`'s
  Logs tab (filters by session, type, MsgType, text) and Metrics tab (message rates, `quote.round.trip` quantile and
  mean, `quote.last.mid` gauge, a split by `fix.msg.type`).

## Status: implemented

Differences from the plan, found against the live sandbox:

- The collector also renames micrometer's `<timer>.max` gauges to `<timer>_max` (`metrics_transform`). Elasticsearch
  otherwise maps `metrics.<timer>.max` first in many runs, and then no longer applies the `histogram` template to
  `metrics.<timer>`, which becomes plain `counts`/`values` arrays nothing can aggregate. Reproduced with a single
  bulk request: the exporter sends the template on every document.
- Charts stop at the newest bucket holding data: the engines export every few seconds, so the windows reaching past
  the last export were half empty and showed a false dip at the right edge.
- Logs `@timestamp` is a `date` (milliseconds) in the OTel logs mapping, not `date_nanos`; the source still asks for
  the nanosecond format, so a nanosecond mapping keeps its precision.
- A timer without a histogram (`session.rtt`, an `aggregate_metric_double`) has a rate and a mean; a quantile is
  refused by Elasticsearch and reported as the backend failing.
