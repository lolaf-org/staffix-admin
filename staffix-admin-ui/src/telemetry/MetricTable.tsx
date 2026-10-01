import { Table } from 'antd'
import { formatDateTime } from './format'
import { mergeByTimestamp, type ChartSeries } from './MetricChart'

/**
 * The same values as the charts, newest first, for reading exact figures.
 */
export function MetricTable({ facets, format }: { facets: { title: string; series: ChartSeries[] }[]; format: (v: number) => string }) {
  const columns = facets.flatMap((facet) =>
    facet.series.map((s) => ({ key: `${facet.title}-${s.key}`, title: facets.length > 1 ? `${s.name}, ${facet.title}` : s.name, series: s })),
  )
  const rows = mergeByTimestamp(
    columns.map((c) => ({ ...c.series, key: c.key })),
  ).reverse()

  return (
    <Table
      size="small"
      rowKey="t"
      dataSource={rows}
      pagination={{ pageSize: 20, showSizeChanger: false, size: 'small' }}
      scroll={{ x: true }}
      columns={[
        { title: 'Time', dataIndex: 't', render: (t: number) => formatDateTime(t), fixed: 'left' },
        ...columns.map((c) => ({
          title: c.title,
          dataIndex: c.key,
          align: 'right' as const,
          render: (value: number | undefined) => (value === undefined ? '' : format(value)),
        })),
      ]}
    />
  )
}
