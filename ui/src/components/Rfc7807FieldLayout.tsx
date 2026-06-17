interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
}

interface Rfc7807FieldLayoutProps {
  problem: ProblemDetail;
}

export function Rfc7807FieldLayout({ problem }: Rfc7807FieldLayoutProps) {
  const fields: Array<{ label: string; value: string | number | undefined }> = [
    { label: 'Type', value: problem.type },
    { label: 'Title', value: problem.title },
    { label: 'Status', value: problem.status },
    { label: 'Detail', value: problem.detail },
  ];

  return (
    <div className="border border-red-200 rounded-md bg-red-50 p-4" data-testid="rfc7807-layout">
      <dl className="space-y-2">
        {fields.map(({ label, value }) =>
          value != null ? (
            <div key={label} className="flex" data-testid={`rfc7807-field-${label.toLowerCase()}`}>
              <dt className="w-20 flex-shrink-0 font-medium text-sm text-red-700">{label}</dt>
              <dd className="text-sm text-red-900 break-all">{String(value)}</dd>
            </div>
          ) : null
        )}
      </dl>
    </div>
  );
}
