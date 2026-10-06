'use client'

const inputClass = 'mt-2 h-12 w-full rounded-xl border border-[#eaded8] bg-white px-4 font-normal outline-none focus:border-[#ff5b35]'

export function Field({ label, value, onChange, type = 'text', ...rest }: {
  label: string; value: string; onChange: (v: string) => void; type?: string
} & Omit<React.InputHTMLAttributes<HTMLInputElement>, 'value' | 'onChange' | 'type'>) {
  return (
    <label className="text-sm font-semibold">
      {label}
      <input type={type} value={value} onChange={e => onChange(e.target.value)} {...rest} className={inputClass} />
    </label>
  )
}

export function TextArea({ label, value, onChange, ...rest }: {
  label: string; value: string; onChange: (v: string) => void
} & Omit<React.TextareaHTMLAttributes<HTMLTextAreaElement>, 'value' | 'onChange'>) {
  return (
    <label className="text-sm font-semibold">
      {label}
      <textarea value={value} onChange={e => onChange(e.target.value)} rows={3} {...rest}
        className="mt-2 w-full rounded-xl border border-[#eaded8] bg-white px-4 py-3 font-normal outline-none focus:border-[#ff5b35]" />
    </label>
  )
}
