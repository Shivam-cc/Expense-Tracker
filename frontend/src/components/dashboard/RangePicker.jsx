import { RANGE_PRESETS } from '../../utils/date'

export default function RangePicker({ preset, startDate, endDate, onPreset, onDates }) {
  return (
    <div className="flex flex-wrap items-end gap-3">
      <div>
        <label htmlFor="range-preset" className="block text-xs font-medium text-gray-500 mb-1">Date range</label>
        <select
          id="range-preset"
          value={preset}
          onChange={(e) => onPreset(e.target.value)}
          className="px-3 py-2 border border-gray-300 bg-white rounded-lg text-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
        >
          {RANGE_PRESETS.map((p) => (
            <option key={p.id} value={p.id}>{p.label}</option>
          ))}
        </select>
      </div>
      {preset === 'custom' && (
        <>
          <div>
            <label htmlFor="range-start" className="block text-xs font-medium text-gray-500 mb-1">From</label>
            <input
              id="range-start" type="date" value={startDate} max={endDate}
              onChange={(e) => e.target.value && onDates({ start: e.target.value, end: endDate })}
              className="px-3 py-2 border border-gray-300 bg-white rounded-lg text-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
            />
          </div>
          <div>
            <label htmlFor="range-end" className="block text-xs font-medium text-gray-500 mb-1">To</label>
            <input
              id="range-end" type="date" value={endDate} min={startDate}
              onChange={(e) => e.target.value && onDates({ start: startDate, end: e.target.value })}
              className="px-3 py-2 border border-gray-300 bg-white rounded-lg text-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
            />
          </div>
        </>
      )}
    </div>
  )
}
