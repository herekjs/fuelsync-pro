import type { TankStatus } from '../../types';

interface Props {
  status: TankStatus;
}

const STATUS_CONFIG: Record<TankStatus, { label: string; className: string; icon: string }> = {
  NORMAL:  { label: '정상',    className: 'bg-green-100 text-green-800 border-green-200',  icon: '🟢' },
  WARNING: { label: '주의',    className: 'bg-yellow-100 text-yellow-800 border-yellow-200', icon: '🟡' },
  DANGER:  { label: '위험',    className: 'bg-red-100 text-red-800 border-red-200',         icon: '🔴' },
};

export default function StatusBadge({ status }: Props) {
  const { label, className, icon } = STATUS_CONFIG[status];
  return (
    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium border ${className}`}>
      {icon} {label}
    </span>
  );
}
