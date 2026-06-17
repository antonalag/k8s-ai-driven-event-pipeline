import { useState } from 'react';
import { RecommendedActionBlock } from './RecommendedActionBlock';

type ActionCategory = 'kubectl-commands' | 'configuration-changes' | 'verification-steps';

interface ActionGroupCollapsibleProps {
  actions: string[];
}

const CATEGORY_LABELS: Record<ActionCategory, string> = {
  'kubectl-commands': 'kubectl commands',
  'configuration-changes': 'configuration changes',
  'verification-steps': 'verification steps',
};

export function categorizeAction(action: string): ActionCategory {
  if (action.startsWith('kubectl')) return 'kubectl-commands';
  if (/config|edit|set/i.test(action)) return 'configuration-changes';
  return 'verification-steps';
}

export function ActionGroupCollapsible({ actions }: ActionGroupCollapsibleProps) {
  if (!actions || actions.length <= 5) {
    return <RecommendedActionBlock actions={actions} />;
  }

  const groups = actions.reduce<Record<ActionCategory, string[]>>((acc, action) => {
    const category = categorizeAction(action);
    acc[category].push(action);
    return acc;
  }, {
    'kubectl-commands': [],
    'configuration-changes': [],
    'verification-steps': [],
  });

  return (
    <div className="space-y-3" data-testid="action-group-collapsible">
      {(Object.entries(groups) as Array<[ActionCategory, string[]]>)
        .filter(([, items]) => items.length > 0)
        .map(([category, items]) => (
          <CollapsibleGroup key={category} category={category} actions={items} />
        ))}
    </div>
  );
}

function CollapsibleGroup({ category, actions }: { category: ActionCategory; actions: string[] }) {
  const [open, setOpen] = useState(true);

  return (
    <div className="border border-gray-200 rounded-md" data-testid={`action-group-${category}`}>
      <button
        onClick={() => setOpen(!open)}
        className="w-full flex items-center justify-between px-4 py-2 bg-gray-50 hover:bg-gray-100 text-sm font-medium text-gray-700 rounded-t-md"
        aria-expanded={open}
      >
        <span>{CATEGORY_LABELS[category]} ({actions.length})</span>
        <span className="text-gray-400">{open ? '▾' : '▸'}</span>
      </button>
      {open && (
        <div className="p-3">
          <RecommendedActionBlock actions={actions} />
        </div>
      )}
    </div>
  );
}
