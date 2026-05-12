import type { ReactNode } from 'react'

export function Laptop({ children }: { children: ReactNode }) {
  return (
    <div
      className="anim-laptop-rise relative w-full [animation-delay:0.05s]"
      style={{
        maxWidth: 'min(820px, calc((100svh - 240px) * 1.6))',
        filter: 'drop-shadow(0 30px 50px rgba(31,39,27,0.22))',
      }}
    >
      {/* Lid */}
      <div
        className="relative rounded-[20px] p-[9px]"
        style={{
          background: 'linear-gradient(180deg, #d8d6d2 0%, #c9c6c2 100%)',
          boxShadow:
            'inset 0 1px 0 rgba(255,255,255,0.6), 0 1px 2px rgba(31,39,27,0.15)',
        }}
      >
        {/* Bezel */}
        <div
          className="relative rounded-[12px] p-[12px]"
          style={{ background: 'linear-gradient(180deg, #1a1a1a 0%, #0c0c0c 100%)' }}
        >
          {/* Camera dot */}
          <div className="absolute top-[5px] left-1/2 -translate-x-1/2 w-1.5 h-1.5 rounded-full bg-zinc-700" />

          {/* Screen */}
          <div
            className="relative aspect-[16/10] rounded-[5px] overflow-hidden flex items-center justify-center px-6"
            style={{
              background:
                'radial-gradient(120% 100% at 50% 0%, #f5f1ea 0%, #e8e3da 60%, #ddd6cc 100%)',
            }}
          >
            {children}
          </div>
        </div>
      </div>

      {/* Base / hinge */}
      <div className="relative mx-auto" style={{ width: '108%' }}>
        <div
          className="h-[12px] -mt-[2px] mx-auto rounded-b-[10px]"
          style={{
            background: 'linear-gradient(180deg, #c2bfba 0%, #a8a5a0 100%)',
            boxShadow:
              'inset 0 1px 0 rgba(255,255,255,0.4), 0 8px 12px -6px rgba(31,39,27,0.25)',
          }}
        />
        <div
          className="absolute top-0 left-1/2 -translate-x-1/2 h-[4px] w-[18%] rounded-b-md"
          style={{ background: '#8e8b86' }}
        />
      </div>
    </div>
  )
}
