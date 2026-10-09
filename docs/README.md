# staffix-admin documentation

Start with the [quick start](../README.md#quick-start): the demo runs the console against simulated engines in one
command. Then come back here.

| Guide | What it answers |
|-------|-----------------|
| [Installation](installation.md) | How do I install the console, create its users, run it in Docker, and get my engines to show up? |
| [Configuration](configuration.md) | Which settings does the console take, and what are their defaults? |
| [Connectors](connectors.md) | How does the console find each session's metrics and logs, and where do I keep the backends' credentials? |
| [Backends](backends.md) | What do Prometheus, Loki, Elasticsearch, ClickHouse and the OpenTelemetry collector need so the console can read what staffix exports? |
| [Sessions](sessions.md) | What do the colours mean, and how do I log a session on, reset its numbers, edit its settings, send messages or see its availability? |
| [Metrics](metrics.md) | Which meters can I chart, and what sets the finest rate a chart can show? |
| [Logs and trace](logs-and-trace.md) | How do I search FIX logs, decode a message, follow an order across engines, and what does a search cost the backend? |
| [Security](security.md) | How do operators and engines log in, what does each role allow, what is audited, and how do I turn on TLS? |
| [Plugins](plugins.md) | How do I add a backend, a secret scheme, an audit sink or a configuration store? |
| [Development](development.md) | How is the code organised, how do I build it, and how do I work on the UI with hot reload? |
