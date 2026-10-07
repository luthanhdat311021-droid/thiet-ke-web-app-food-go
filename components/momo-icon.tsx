/** Simple text badge for the MoMo e-wallet payment option. */
export function MomoIcon({ className = 'h-6 w-9' }: { className?: string }) {
  return (
    <span className={`inline-grid place-items-center rounded-md bg-[#a50064] text-[10px] font-extrabold leading-none text-white ${className}`}>
      MoMo
    </span>
  )
}
