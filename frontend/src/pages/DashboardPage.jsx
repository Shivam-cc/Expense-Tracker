import { lazy, Suspense, useEffect, useMemo, useState } from 'react'
import { Plus, Download } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { expensesApi, categoriesApi } from '../services/api'
import { fetchTrendExpenses } from '../services/expenseTrend'
import { useAsyncData } from '../hooks/useAsyncData'
import { useExpenseFilters } from '../hooks/useExpenseFilters'
import { useToast } from '../components/toast/toastContext'
import Tabs from '../components/ui/Tabs'
import Skeleton from '../components/ui/Skeleton'
import SummaryCards from '../components/dashboard/SummaryCards'
import RangePicker from '../components/dashboard/RangePicker'
import ExpenseFilters from '../components/dashboard/ExpenseFilters'
import ExpenseList from '../components/dashboard/ExpenseList'
import CategoryList from '../components/dashboard/CategoryList'
import ExpenseModal from '../components/dashboard/ExpenseModal'
import CategoryModal from '../components/dashboard/CategoryModal'
import ConfirmDialog from '../components/dashboard/ConfirmDialog'
import { buildTrend } from '../utils/trend'
import { normalizeSummary } from '../utils/format'
import { RANGE_PRESETS, formatDisplayDate } from '../utils/date'
import { saveBlob } from '../utils/download'

const SpendingCharts = lazy(() => import('../components/dashboard/SpendingCharts'))

const PAGE_SIZE = 10
const TABS = [
  { id: 'expenses', label: 'Expenses' },
  { id: 'categories', label: 'Categories' },
]

export default function DashboardPage() {
  const { user } = useAuth()
  const toast = useToast()
  const currency = user?.currency || 'INR'
  const f = useExpenseFilters()
  const { startDate, endDate, q, categoryId, sortBy, direction, page } = f

  const [tab, setTab] = useState('expenses')
  const [expenseModal, setExpenseModal] = useState(null) // null | {} (new) | expense (edit)
  const [categoryModal, setCategoryModal] = useState(null)
  const [confirm, setConfirm] = useState(null)
  const [exporting, setExporting] = useState(false)

  const categories = useAsyncData(() => categoriesApi.list(), 'categories')
  const expenses = useAsyncData(() => {
    const params = { page, size: PAGE_SIZE, sortBy, direction, startDate, endDate }
    if (q) params.q = q
    if (categoryId !== '') params.categoryId = categoryId
    return expensesApi.list(params)
  }, [page, sortBy, direction, startDate, endDate, q, categoryId].join('|'))
  const summary = useAsyncData(
    () => expensesApi.summary(startDate, endDate).then(normalizeSummary),
    `${startDate}|${endDate}`,
  )
  const trendData = useAsyncData(() => fetchTrendExpenses(startDate, endDate), `${startDate}|${endDate}`)

  const catList = categories.data
  const catMap = useMemo(() => Object.fromEntries((catList || []).map((c) => [c.id, c])), [catList])
  const catByName = useMemo(() => Object.fromEntries((catList || []).map((c) => [c.name, c])), [catList])

  const categoryItems = useMemo(
    () => (summary.data?.byCategory || []).map((c) => ({
      ...c,
      color: c.color || catByName[c.name]?.color || null,
      icon: c.icon || catByName[c.name]?.icon || null,
    })),
    [summary.data, catByName],
  )

  const trend = useMemo(
    () => (trendData.data ? buildTrend(trendData.data.expenses, startDate, endDate) : null),
    [trendData.data, startDate, endDate],
  )

  const expenseItems = expenses.data?.content ?? null
  const totalElements = expenses.data?.totalElements ?? 0
  const totalPages = expenses.data?.totalPages ?? Math.ceil(totalElements / PAGE_SIZE)

  const { setPage } = f
  useEffect(() => {
    if (expenses.data && expenses.data.content.length === 0 && page > 0 && totalPages > 0) {
      setPage(totalPages - 1)
    }
  }, [expenses.data, page, totalPages, setPage])

  const rangeLabel = f.preset === 'custom'
    ? `${formatDisplayDate(startDate)} – ${formatDisplayDate(endDate)}`
    : RANGE_PRESETS.find((p) => p.id === f.preset).label

  const reloadSummaries = () => { summary.reload(); trendData.reload() }

  const handleExpenseSaved = () => {
    setExpenseModal(null)
    expenses.reload()
    reloadSummaries()
  }

  const handleCategorySaved = () => {
    setCategoryModal(null)
    categories.reload()
    expenses.reload()
    reloadSummaries()
  }

  const handleDeleteExpense = (exp) => {
    setConfirm({
      message: `Delete "${exp.description}"? This cannot be undone.`,
      onConfirm: async () => {
        try {
          await expensesApi.delete(exp.id)
        } catch (err) {
          toast.error(err.message || 'Could not delete the expense.')
          throw err
        }
        toast.success('Expense deleted')
        if (page > 0 && expenseItems?.length === 1) setPage(page - 1)
        else expenses.reload()
        reloadSummaries()
      },
    })
  }

  const handleDeleteCategory = (cat) => {
    setConfirm({
      message: `Delete category "${cat.name}"? Expenses in this category will be uncategorized.`,
      onConfirm: async () => {
        try {
          await categoriesApi.delete(cat.id)
        } catch (err) {
          toast.error(err.message || 'Could not delete the category.')
          throw err
        }
        toast.success('Category deleted')
        categories.reload()
        expenses.reload()
        reloadSummaries()
      },
    })
  }

  const handleExport = async () => {
    setExporting(true)
    try {
      const params = { startDate, endDate }
      if (q) params.q = q
      if (categoryId !== '') params.categoryId = categoryId
      const { blob, filename } = await expensesApi.export(params)
      saveBlob(blob, filename)
      toast.success('Export downloaded')
    } catch (err) {
      toast.error(err.message || 'Could not export expenses.')
    } finally {
      setExporting(false)
    }
  }

  const addButtonCls =
    'flex items-center gap-1.5 bg-primary-600 hover:bg-primary-700 text-white text-sm font-medium px-3.5 py-2 rounded-lg transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:ring-offset-2'

  return (
    <>
      <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-4 mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
          <p className="text-sm text-gray-500">Hi, {user?.name}. Here is your spending for {rangeLabel.toLowerCase()}.</p>
        </div>
        <RangePicker
          preset={f.preset}
          startDate={startDate}
          endDate={endDate}
          onPreset={f.setPreset}
          onDates={({ start, end }) => f.setFilters({ range: 'custom', start, end })}
        />
      </div>

      <SummaryCards
        summary={summary.data}
        summaryLoading={summary.loading}
        summaryFailed={Boolean(summary.error)}
        onRetrySummary={summary.reload}
        expenseCount={expenses.data ? totalElements : null}
        expenseCountLoading={expenses.loading && !expenses.data}
        categoryCount={catList ? catList.length : null}
        rangeLabel={rangeLabel}
        currency={currency}
      />

      <Suspense fallback={<div className="grid lg:grid-cols-2 gap-4 mb-6"><Skeleton className="h-72" /><Skeleton className="h-72" /></div>}>
        <SpendingCharts
          categoryItems={categoryItems}
          summaryLoading={summary.loading && !summary.data}
          summaryFailed={Boolean(summary.error)}
          onRetrySummary={summary.reload}
          trend={trend}
          trendLoading={trendData.loading && !trend}
          trendError={trendData.error}
          onRetryTrend={trendData.reload}
          truncated={Boolean(trendData.data?.truncated)}
          currency={currency}
        />
      </Suspense>

      <Tabs tabs={TABS} value={tab} onChange={setTab} label="Dashboard sections">
        {tab === 'expenses' ? (
          <div className="bg-white rounded-xl shadow-sm border border-gray-100">
            <div className="flex items-center justify-between gap-3 px-5 py-4 border-b border-gray-100">
              <h2 className="font-semibold text-gray-900">Expenses</h2>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={handleExport}
                  disabled={exporting}
                  className="flex items-center gap-1.5 border border-gray-300 text-gray-700 hover:bg-gray-50 disabled:opacity-60 text-sm font-medium px-3.5 py-2 rounded-lg transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
                >
                  <Download className="w-4 h-4" aria-hidden="true" />
                  <span className="hidden sm:inline">{exporting ? 'Exporting…' : 'Export CSV'}</span>
                  <span className="sr-only sm:hidden">Export CSV</span>
                </button>
                <button type="button" onClick={() => setExpenseModal({})} className={addButtonCls}>
                  <Plus className="w-4 h-4" aria-hidden="true" /> Add Expense
                </button>
              </div>
            </div>
            <ExpenseFilters
              searchInput={f.searchInput}
              onSearchChange={f.onSearchChange}
              categoryId={categoryId}
              onCategory={(value) => f.setFilters({ categoryId: value })}
              sortValue={f.sortValue}
              onSort={f.setSort}
              categories={catList || []}
              isModified={f.isModified}
              onReset={f.resetFilters}
            />
            <ExpenseList
              expenses={expenseItems}
              loading={expenses.loading}
              error={expenses.error}
              onRetry={expenses.reload}
              catMap={catMap}
              currency={currency}
              page={page}
              totalPages={totalPages}
              totalElements={totalElements}
              onPage={setPage}
              filtered={f.hasListFilters || f.preset !== 'month'}
              onAdd={() => setExpenseModal({})}
              onClear={f.resetFilters}
              onEdit={setExpenseModal}
              onDelete={handleDeleteExpense}
            />
          </div>
        ) : (
          <div className="bg-white rounded-xl shadow-sm border border-gray-100">
            <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
              <h2 className="font-semibold text-gray-900">Categories</h2>
              <button type="button" onClick={() => setCategoryModal({})} className={addButtonCls}>
                <Plus className="w-4 h-4" aria-hidden="true" /> Add Category
              </button>
            </div>
            <CategoryList
              categories={catList}
              error={categories.error}
              onRetry={categories.reload}
              onAdd={() => setCategoryModal({})}
              onEdit={setCategoryModal}
              onDelete={handleDeleteCategory}
            />
          </div>
        )}
      </Tabs>

      {expenseModal !== null && (
        <ExpenseModal
          expense={expenseModal}
          categories={catList || []}
          currency={currency}
          onSave={handleExpenseSaved}
          onClose={() => setExpenseModal(null)}
        />
      )}
      {categoryModal !== null && (
        <CategoryModal
          category={categoryModal}
          onSave={handleCategorySaved}
          onClose={() => setCategoryModal(null)}
        />
      )}
      {confirm && (
        <ConfirmDialog
          message={confirm.message}
          onConfirm={confirm.onConfirm}
          onClose={() => setConfirm(null)}
        />
      )}
    </>
  )
}
